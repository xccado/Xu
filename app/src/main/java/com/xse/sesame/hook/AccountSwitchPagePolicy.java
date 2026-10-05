package com.xse.sesame.hook;

import java.util.Locale;

final class AccountSwitchPagePolicy {
    private AccountSwitchPagePolicy() {
    }

    static boolean blocksClass(String str, String str2) {
        if (str == null || str.isEmpty() || str.equals(str2)) {
            return false;
        }
        String lowerCase = str.toLowerCase(Locale.ROOT);
        return lowerCase.contains("login") || lowerCase.contains("verifyidentity") || lowerCase.contains("captcha");
    }
}
