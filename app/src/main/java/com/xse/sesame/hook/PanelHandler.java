package com.xse.sesame.hook;

import com.xse.sesame.util.Log;
import com.xse.sesame.util.FileUtil;
import com.xse.sesame.util.Statistics;
import com.xse.sesame.util.idMap.UserIdMap;
import fi.iki.elonen.NanoHTTPD;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 远程面板 API：供 Web 面板查询状态/统计/日志、触发任务执行与配置重载。
 * 路由（全部经 BaseHandler 的 Bearer Token 鉴权）：
 *   GET  /panel/api/status             激活状态 + 版本 + 当前账号
 *   GET  /panel/api/statistics         能量统计（日/月/年 × 收/帮/浇/被水/浇水）
 *   GET  /panel/api/logs?name=runtime[&tail=N]
 *                                      日志尾部（name: runtime/debug/forest/farm/other/goldenbeans/error，
 *                                      tail 默认 200，上限 2000）
 *   POST /panel/api/execute            触发一轮任务执行（可带 {"group":"FOREST"}，空＝全部）
 *   POST /panel/api/reload             让支付宝进程重载配置
 */
public class PanelHandler extends BaseHandler {

    private static final String TAG = "PanelHandler";
    private static final int DEFAULT_TAIL = 200;
    private static final int MAX_TAIL = 2000;

    public PanelHandler(String secretToken) {
        super(secretToken);
    }

    @Override
    protected NanoHTTPD.Response onGet(NanoHTTPD.IHTTPSession session) {
        String uri = session.getUri();
        if ("/panel/api/status".equals(uri)) {
            return status();
        }
        if ("/panel/api/statistics".equals(uri)) {
            return statistics();
        }
        if ("/panel/api/logs".equals(uri)) {
            return logs(session);
        }
        return notFound();
    }

    @Override
    protected NanoHTTPD.Response onPost(NanoHTTPD.IHTTPSession session, String body) {
        String uri = session.getUri();
        if ("/panel/api/execute".equals(uri)) {
            return execute(body);
        }
        if ("/panel/api/reload".equals(uri)) {
            ApplicationHook.restartByBroadcast();
            java.util.Map<String, Object> respMsg = new LinkedHashMap<>();
            respMsg.put("status", "ok");
            respMsg.put("message", "重载指令已发送");
            return ok(respMsg);
        }
        return notFound();
    }

    // ---------- 状态 ----------

    private NanoHTTPD.Response status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("app", "Xse");
        data.put("version", com.xse.sesame.data.ViewAppInfo.getAppVersion());
        data.put("currentUid", UserIdMap.getCurrentUid());
        data.put("currentUser", UserIdMap.getCurrentMaskName());
        data.put("userCount", UserIdMap.getUserMap().size());
        return ok(data);
    }

    // ---------- 统计 ----------

    private NanoHTTPD.Response statistics() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "ok");
        resp.put("day", timeStats(Statistics.TimeType.DAY));
        resp.put("month", timeStats(Statistics.TimeType.MONTH));
        resp.put("year", timeStats(Statistics.TimeType.YEAR));
        return ok(resp);
    }

    private Map<String, Object> timeStats(Statistics.TimeType tt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("collected", Statistics.getData(tt, Statistics.DataType.COLLECTED));
        m.put("helped", Statistics.getData(tt, Statistics.DataType.HELPED));
        m.put("watered", Statistics.getData(tt, Statistics.DataType.WATERED));
        m.put("wateredcount", Statistics.getData(tt, Statistics.DataType.WATEREDCOUNT));
        m.put("wateringcount", Statistics.getData(tt, Statistics.DataType.WATERINGCOUNT));
        return m;
    }

    // ---------- 日志尾部 ----------

    private NanoHTTPD.Response logs(NanoHTTPD.IHTTPSession session) {
        String name = "runtime";
        int tail = 200;
        try {
            java.util.Map<String, String> parms = session.getParms();
            if (parms != null) {
                String n = parms.get("name");
                if (n != null && !n.trim().isEmpty()) {
                    name = n.trim();
                }
                String t = parms.get("tail");
                if (t != null) {
                    tail = Math.max(1, Math.min(MAX_TAIL, Integer.parseInt(t.trim())));
                }
            }
        } catch (Throwable ignored) {
        }

        File logFile;
        switch (name) {
            case "debug": logFile = FileUtil.getDebugLogFile(); break;
            case "forest": logFile = FileUtil.getForestLogFile(); break;
            case "farm": logFile = FileUtil.getFarmLogFile(); break;
            case "other": logFile = FileUtil.getOtherLogFile(); break;
            case "goldenbeans": logFile = FileUtil.getGoldenBeansLogFile(); break;
            case "error": logFile = FileUtil.getErrorLogFile(); break;
            default: logFile = FileUtil.getRuntimeLogFile(); break;
        }

        Deque<String> deque = new ArrayDeque<>();
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(logFile), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (deque.size() >= tail) {
                    deque.pollFirst();
                }
                deque.addLast(line);
            }
            reader.close();
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("name", name);
        data.put("lines", new ArrayList<>(deque));
        return ok(data);
    }

    // ---------- 触发任务执行 ----------

    private NanoHTTPD.Response execute(String body) {
        String group = null;
        try {
            if (body != null && !body.trim().isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> req = mapper.readValue(body, Map.class);
                Object g = req.get("group");
                if (g != null) {
                    group = String.valueOf(g);
                }
            }
        } catch (Exception ignored) {
        }
        try {
            android.content.Context ctx = ApplicationHook.getContext();
            android.content.Intent intent = new android.content.Intent(
                    "com.eg.android.AlipayGphone.sesame.execute");
            if (group != null && !group.isEmpty()) {
                intent.putExtra("group", group);
            }
            ctx.sendBroadcast(intent);
            java.util.Map<String, Object> respMsg = new LinkedHashMap<>();
            respMsg.put("status", "ok");
            respMsg.put("message", group == null || group.isEmpty() ? "已触发全部任务" : ("已触发分组任务: " + group));
            return ok(respMsg);
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return badRequest("execute failed: " + t.getMessage());
        }
    }
}
