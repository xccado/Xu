package com.xse.sesame.hook;

import com.xse.sesame.util.FileUtil;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public final class AccountSwitchAccountCount {
    private static final long FRESH_MS = 90000;

    private AccountSwitchAccountCount() {
    }

    private static File file() {
        return new File(FileUtil.MAIN_DIRECTORY_FILE, "account_switch_count.json");
    }

    static void publish(int i) {
        if (i < -1 || i > 32) {
            return;
        }
        try {
            JSONObject jSONObject = new JSONObject();
            jSONObject.put("count", i);
            jSONObject.put("updatedAt", System.currentTimeMillis());
            FileOutputStream fileOutputStream = new FileOutputStream(file());
            try {
                fileOutputStream.write(jSONObject.toString().getBytes(StandardCharsets.UTF_8));
                fileOutputStream.close();
            } catch (Throwable th) {
                try {
                    fileOutputStream.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        } catch (Throwable unused) {
        }
    }

    public static String label() {
        try {
            File file = file();
            if (file.isFile() && file.length() <= 256) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                FileInputStream fileInputStream = new FileInputStream(file);
                try {
                    byte[] bArr = new byte[256];
                    while (true) {
                        int i = fileInputStream.read(bArr);
                        if (i == -1) {
                            break;
                        }
                        byteArrayOutputStream.write(bArr, 0, i);
                    }
                    fileInputStream.close();
                    JSONObject jSONObject = new JSONObject(new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8));
                    int iOptInt = jSONObject.optInt("count", -1);
                    long jOptLong = jSONObject.optLong("updatedAt", 0L);
                    long jCurrentTimeMillis = System.currentTimeMillis();
                    if (iOptInt >= 0 && iOptInt <= 32) {
                        if (jCurrentTimeMillis - jOptLong > FRESH_MS) {
                            return "上次读取可轮询账号：" + iOptInt + "个（等待支付宝更新）";
                        }
                        StringBuilder sb = new StringBuilder();
                        sb.append("本机可轮询账号：");
                        sb.append(iOptInt);
                        sb.append("个");
                        sb.append(iOptInt < 2 ? "（至少2个才能轮询）" : "");
                        return sb.toString();
                    }
                    return "本机可轮询账号：等待读取（请先打开支付宝）";
                } catch (Throwable th) {
                    try {
                        fileInputStream.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                    throw th;
                }
            }
        } catch (Throwable unused) {
        }
        return "本机可轮询账号：等待读取（请先打开支付宝）";
    }
}
