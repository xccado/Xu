package com.xse.sesame.hook;

public final class AccountSwitchIntervalDraft {
    public static final int ACCOUNT_INTERVAL_SECONDS = 15;
    public static final int DEFAULT_SECONDS = 7200;
    public static final int MAX_SECONDS = 86400;
    public static final int MIN_SECONDS = 15;

    private AccountSwitchIntervalDraft() {
    }

    public static int resolve(boolean z, String str, int i) {
        if (i < 15 || i > 86400) {
            i = DEFAULT_SECONDS;
        }
        String strTrim = str == null ? "" : str.trim();
        if (strTrim.isEmpty()) {
            return z ? DEFAULT_SECONDS : i;
        }
        try {
            int i2 = Integer.parseInt(strTrim);
            if (i2 < 15 || i2 > 86400) {
                throw new NumberFormatException();
            }
            return i2;
        } catch (NumberFormatException e) {
            if (z) {
                throw e;
            }
            return i;
        }
    }
}
