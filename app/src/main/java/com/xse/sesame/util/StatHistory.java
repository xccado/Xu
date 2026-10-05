package com.xse.sesame.util;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 能量日历史：记录最近 30 天的每日收取/帮收/浇水数据，供首页趋势图使用。
 * <p>
 * 数据文件 statHistory.json（与 statistics.json 同目录）：
 * <pre>{ "2026-10-04": {"collected": 123, "helped": 10, "watered": 5}, ... }</pre>
 * <p>
 * 写侧：{@link #add(int, int, int)} 由 {@link Statistics#addData} 在同锁内调用，
 * 节流落盘（60s 一次）；读侧：{@link #getRecentDays(int)} 输出按日期升序的最近 N 天，
 * 缺失的日子补 0，保证趋势图时间轴连续。跨年/跨月无需特殊处理（key 是全日期）。
 */
public class StatHistory {

    private static final String TAG = "StatHistory";
    private static final int MAX_DAYS = 30;
    private static final long FLUSH_INTERVAL_MS = 60_000L;

    /** 与 Statistics.addData 共用同一把锁（Statistics.class） */
    private static final Object STAT_LOCK = Statistics.class;

    /** key=yyyy-MM-dd（TreeMap 天然按日期升序），value=当日三项计数 */
    private static final TreeMap<String, Day> history = new TreeMap<>();

    private static volatile long lastFlush = 0L;

    /** 单日三项计数 */
    public static class Day {
        public int collected;
        public int helped;
        public int watered;

        public Day() {}

        public Day(int collected, int helped, int watered) {
            this.collected = collected;
            this.helped = helped;
            this.watered = watered;
        }
    }

    private static final SimpleDateFormat DAY_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private static String dayKey() {
        return dayKey(Calendar.getInstance());
    }

    private static String dayKey(Calendar cal) {
        synchronized (DAY_FORMAT_LOCK) {
            return DAY_FORMAT.format(cal.getTime());
        }
    }

    private static final Object DAY_FORMAT_LOCK = new Object();

    /** 读盘（幂等：UI 进程与宿主进程首次进入时各调一次） */
    public static void load() {
        synchronized (STAT_LOCK) {
            try {
                File f = FileUtil.getStatHistoryFile();
                if (f.exists()) {
                    String json = FileUtil.readFromFile(f);
                    ObjectMapper mapper = JsonUtil.copyMapper();
                    MapType mt = mapper.getTypeFactory()
                            .constructMapType(TreeMap.class, String.class, Day.class);
                    Map<String, Day> loaded = mapper.readValue(json, mt);
                    if (loaded != null) {
                        history.clear();
                        history.putAll(loaded);
                        trim();
                    }
                }
            } catch (Throwable t) {
                Log.printStackTrace(TAG, t);
            }
        }
    }

    /** 写侧入口：由 Statistics.addData 在持有 STAT_LOCK 时调用；节流落盘 */
    public static void add(int collected, int helped, int watered) {
        String key = dayKey();
        Day d = history.get(key);
        if (d == null) {
            d = new Day();
            history.put(key, d);
        }
        d.collected += collected;
        d.helped += helped;
        d.watered += watered;
        trim();
        long now = System.currentTimeMillis();
        if (lastFlush == 0L || System.currentTimeMillis() - lastFlush >= FLUSH_INTERVAL_MS) {
            lastFlush = System.currentTimeMillis();
            flushNow();
        }
    }

    /** 日期切换时由 Statistics.updateDay 在同锁内调用：立即落盘封存旧日 */
    public static void onDayChanged() {
        flushNow();
    }

    /** 读侧：最近 N 天（含今日）按日期升序；缺失日补 0 */
    public static List<Map.Entry<String, Day>> getRecentDays(int n) {
        List<Map.Entry<String, Day>> out = new ArrayList<>();
        synchronized (STAT_LOCK) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -(n - 1));
            for (int i = 0; i < n; i++) {
                String key = dayKey(cal);
                Day d = history.get(key);
                if (d == null) d = new Day();
                out.add(new AbstractMap.SimpleEntry<>(key, d));
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
        }
        return out;
    }

    /** 只保留最近 MAX_DAYS 天 */
    private static void trim() {
        while (history.size() > MAX_DAYS) {
            history.remove(history.firstKey());
        }
    }

    private static void flushThrottled() {
        flushNow();
    }

    /** 供外部强制落盘（任务收尾处调用；调用方线程随意） */
    public static void flushPublic() {
        synchronized (STAT_LOCK) {
            flushNow();
        }
    }



    /** 落盘（调用方须已持有 STAT_LOCK 或可接受并发写） */
    private static void flushNow() {
        try {
            ObjectMapper mapper = JsonUtil.copyMapper();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(history);
            FileUtil.write2File(json, FileUtil.getStatHistoryFile());
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }
}
