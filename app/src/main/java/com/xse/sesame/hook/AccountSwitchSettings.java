package com.xse.sesame.hook;

import com.xse.sesame.util.FileUtil;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public final class AccountSwitchSettings {
    private AccountSwitchSettings() {
    }

    public static final class Values {
        public final long activation;
        public final boolean enabled;
        public final int seconds;

        Values(boolean z, int i) {
            this(z, i, 0L);
        }

        Values(boolean z, int i, long j) {
            this.enabled = z;
            this.seconds = i;
            this.activation = j;
        }
    }

    private static File file() {
        return new File(FileUtil.MAIN_DIRECTORY_FILE, "account_switch_settings.json");
    }

    public static synchronized Values read() {
        try {
            File file = file();
            if (file.isFile() && file.length() <= 16384) {
                String all = readAll(file);
                JSONObject jSONObject = all.isEmpty() ? new JSONObject() : new JSONObject(all);
                boolean zOptBoolean = jSONObject.optBoolean("enabled", false);
                int iOptInt = jSONObject.optInt("intervalSeconds", AccountSwitchIntervalDraft.DEFAULT_SECONDS);
                long jOptLong = jSONObject.optLong("activation", 0L);
                if (iOptInt < 15 || iOptInt > 86400) {
                    iOptInt = AccountSwitchIntervalDraft.DEFAULT_SECONDS;
                }
                return new Values(zOptBoolean, iOptInt, jOptLong);
            }
            return new Values(false, AccountSwitchIntervalDraft.DEFAULT_SECONDS);
        } catch (Exception unused) {
            return new Values(false, AccountSwitchIntervalDraft.DEFAULT_SECONDS);
        }
    }

    public static boolean saveDraft(boolean z, String str) {
        Values values = read();
        return update(z, AccountSwitchIntervalDraft.resolve(z, str, values.seconds), values);
    }

    private static synchronized boolean update(boolean z, int i, Values values) {
        long j = values.activation;
        if (z && !values.enabled) {
            j++;
        }
        try {
            JSONObject jSONObject = new JSONObject();
            jSONObject.put("enabled", z);
            jSONObject.put("intervalSeconds", Math.max(15, Math.min(AccountSwitchIntervalDraft.MAX_SECONDS, i)));
            jSONObject.put("activation", j);
            writeAll(file(), jSONObject.toString());
        } catch (Exception unused) {
            return false;
        }
        return true;
    }

    private static String readAll(File file) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        FileInputStream fileInputStream = new FileInputStream(file);
        try {
            byte[] bArr = new byte[1024];
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
