package com.xse.sesame.hook;

import com.xse.sesame.util.FileUtil;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public final class AccountSwitchStatus {
    private static String last;
    private static long lastWrite;

    private AccountSwitchStatus() {
    }

    private static File file() {
        return new File(FileUtil.MAIN_DIRECTORY_FILE, "account_switch_status.json");
    }

    static void publish(String str) {
        try {
            if (message(str) == null) {
                return;
            }
            long jCurrentTimeMillis = System.currentTimeMillis();
            if (!str.equals(last) || jCurrentTimeMillis - lastWrite >= 30000) {
                JSONObject jSONObject = new JSONObject();
                jSONObject.put("phase", str);
                jSONObject.put("updatedAt", jCurrentTimeMillis);
                writeAll(file(), jSONObject.toString());
                last = str;
                lastWrite = jCurrentTimeMillis;
            }
        } catch (Throwable unused) {
        }
    }

    public static String label() {
        try {
            File file = file();
            if (file.isFile() && file.length() <= 256) {
                JSONObject jSONObject = new JSONObject(readAll(file));
                String strOptString = jSONObject.optString("phase", "");
                long jOptLong = jSONObject.optLong("updatedAt", 0L);
                long jCurrentTimeMillis = System.currentTimeMillis();
                String strMessage = message(strOptString);
                if (strMessage != null && jCurrentTimeMillis - jOptLong <= 90000) {
                    return "轮询状态：" + strMessage;
                }
                return "轮询状态：等待支付宝更新";
            }
            return "轮询状态：等待支付宝更新";
        } catch (Throwable unused) {
            return "轮询状态：等待支付宝更新";
        }
    }

    static String message(String str) {
        if (str == null) {
            str = "";
        }
        str.hashCode();
        switch (str) {
            case "PAUSED":
                return "切换异常已暂停，请关闭后再开启";
            case "WAIT_CAPTCHA":
                return "等待验证码或登录页面处理完成";
            case "WAIT_HISTORY":
                return "等待至少两个有效账号";
            case "WAIT_IDENTITY":
                return "等待账号信息同步";
            case "ROUND_COOLDOWN":
                return "切号冷却中，当前账号任务正常运行";
            case "COUNTDOWN":
                return "本账号任务已完成，等待切换下一个账号（15秒）";
            case "SWITCHING":
                return "正在切换到下一个账号";
            case "DISABLED":
                return "已关闭";
            case "WAIT_TASKS":
                return "等待当前业务任务结束";
            case "CONFIRMING":
                return "等待确认切换结果";
            case "WAIT_HOME":
                return "等待返回支付宝首页，二级页面不切号";
            case "WAIT_HOST":
                return "等待支付宝服务就绪";
            default:
                return null;
        }
    }

    private static String readAll(File file) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        FileInputStream fileInputStream = new FileInputStream(file);
        try {
            byte[] bArr = new byte[256];
            while (true) {
                int i = fileInputStream.read(bArr);
                if (i == -1) {
                    fileInputStream.close();
                    return new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
                }
                byteArrayOutputStream.write(bArr, 0, i);
            }
        } catch (Throwable th) {
            try {
                fileInputStream.close();
            } catch (Throwable th2) {
                th.addSuppressed(th2);
            }
            throw th;
        }
    }

    private static void writeAll(File file, String str) throws Exception {
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        try {
            fileOutputStream.write(str.getBytes(StandardCharsets.UTF_8));
            fileOutputStream.close();
        } catch (Throwable th) {
            try {
                fileOutputStream.close();
            } catch (Throwable th2) {
                th.addSuppressed(th2);
            }
            throw th;
        }
    }
}
