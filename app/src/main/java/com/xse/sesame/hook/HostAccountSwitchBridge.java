package com.xse.sesame.hook;

import android.os.Bundle;
import com.xse.sesame.util.XHelpers;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

public final class HostAccountSwitchBridge {

    public static final class Account {
        public final String label;
        public final String loginId;
        public final String uid;

        Account(String str, String str2, String str3) {
            this.uid = str;
            this.loginId = str2;
            this.label = str3;
        }
    }

    private static Object service(String str) throws Exception {
        ClassLoader classLoader = SimplePageManager.getClassLoader();
        if (classLoader == null) {
            throw new IllegalStateException("HOST_NOT_READY");
        }
        Object objCallMethod = XHelpers.callMethod(XHelpers.callMethod(XHelpers.callStaticMethod(Class.forName("com.alipay.mobile.framework.LauncherApplicationAgent", false, classLoader), "getInstance", new Object[0]), "getMicroApplicationContext", new Object[0]), "findServiceByInterface", new Object[]{"com.alipay.mobile.framework.service.ext.security." + str});
        if (objCallMethod != null) {
            return objCallMethod;
        }
        throw new IllegalStateException("HOST_SERVICE_MISSING");
    }

    public String currentUid() throws Exception {
        Object objCallMethod = XHelpers.callMethod(service("AuthService"), "getUserInfo", new Object[0]);
        if (objCallMethod == null) {
            return null;
        }
        return value(XHelpers.callMethod(objCallMethod, "getUserId", new Object[0]));
    }

    public List<Account> accounts() throws Exception {
        String strValue;
        Object objCallMethod = XHelpers.callMethod(service("AccountService"), "getLoginedAlipayUser", new Object[0]);
        if (objCallMethod instanceof List) {
            List list = (List) objCallMethod;
            if (list.size() <= 32) {
                LinkedHashMap linkedHashMap = new LinkedHashMap();
                for (Object obj : list) {
                    if (obj != null) {
                        String strValue2 = value(XHelpers.callMethod(obj, "getUserId", new Object[0]));
                        String strValue3 = value(XHelpers.callMethod(obj, "getLogonId", new Object[0]));
                        if (AccountSwitchState.validUid(strValue2) && strValue3 != null && strValue3.length() <= 256) {
                            try {
                                strValue = value(XHelpers.callMethod(obj, "getDisplayName", new Object[0]));
                            } catch (Throwable unused) {
                                strValue = "";
                            }
                            Account account = (Account) linkedHashMap.get(strValue2);
                            if (account != null && !account.loginId.equals(strValue3)) {
                                throw new IllegalStateException("AMBIGUOUS_HISTORY");
                            }
                            linkedHashMap.put(strValue2, new Account(strValue2, strValue3, masked(strValue) + " (" + masked(strValue3) + ")"));
                        }
                    }
                }
                Object objCallMethod2 = XHelpers.callMethod(service("AuthService"), "getUserInfo", new Object[0]);
                if (objCallMethod2 != null) {
                    String strValue4 = value(XHelpers.callMethod(objCallMethod2, "getUserId", new Object[0]));
                    if (AccountSwitchState.validUid(strValue4) && !linkedHashMap.containsKey(strValue4)) {
                        String strValue5 = value(XHelpers.callMethod(objCallMethod2, "getLogonId", new Object[0]));
                        if (strValue5 == null || strValue5.length() > 256) {
                            throw new IllegalStateException("CURRENT_ACCOUNT_UNAVAILABLE");
                        }
                        linkedHashMap.put(strValue4, new Account(strValue4, strValue5, "当前账号 (" + masked(strValue5) + ")"));
                    }
                }
                if (linkedHashMap.size() > 32) {
                    throw new IllegalStateException("HISTORY_TOO_LARGE");
                }
                return new ArrayList(linkedHashMap.values());
            }
        }
        throw new IllegalStateException("HISTORY_UNAVAILABLE");
    }

    private static Method loginMethod(Object obj) throws Exception {
        Method method = null;
        for (Method method2 : obj.getClass().getMethods()) {
            Class<?>[] parameterTypes = method2.getParameterTypes();
            if ("login".equals(method2.getName()) && parameterTypes.length == 7 && parameterTypes[0] == String.class && parameterTypes[2] == String.class && parameterTypes[5] == Boolean.TYPE && parameterTypes[6] == Bundle.class && !parameterTypes[1].isPrimitive() && !parameterTypes[3].isPrimitive() && !parameterTypes[4].isPrimitive()) {
                if (method != null && !Arrays.equals(method.getParameterTypes(), parameterTypes)) {
                    throw new IllegalStateException("AMBIGUOUS_LOGIN_METHOD");
                }
                method = method2;
            }
        }
        if (method == null) {
            throw new NoSuchMethodException("LOGIN_METHOD_UNAVAILABLE");
        }
        method.setAccessible(true);
        return method;
    }

    public void probe() throws Exception {
        loginMethod(service("LoginService"));
    }

    public boolean switchTo(Account account) throws Exception {
        Object objService = service("LoginService");
        Bundle bundle = new Bundle();
        bundle.putString("targetUid", account.uid);
        Object objInvoke = loginMethod(objService).invoke(objService, account.loginId, null, "switchAccount", null, null, true, bundle);
        if (objInvoke == null) {
            return false;
        }
        Object objProperty = property(objInvoke, "resultStatus", "getResultStatus");
        return ((objProperty instanceof Number) && ((Number) objProperty).intValue() == 1000) || Boolean.TRUE.equals(property(objInvoke, "loginFlag", "isLoginFlag"));
    }

    private static Object property(Object obj, String str, String str2) {
        try {
            return XHelpers.getObjectField(obj, str);
        } catch (Throwable unused) {
            try {
                return XHelpers.callMethod(obj, str2, new Object[0]);
            } catch (Throwable unused2) {
                return null;
            }
        }
    }

    private static String value(Object obj) {
        if (obj == null) {
            return null;
        }
        String string = obj.toString();
        if (string.isEmpty()) {
            return null;
        }
        return string;
    }

    private static String masked(String str) {
        if (str == null || str.isEmpty()) {
            return "账号";
        }
        String strReplaceAll = str.replaceAll("[\\r\\n\\t]", " ");
        if (strReplaceAll.length() <= 4) {
            return "***";
        }
        return strReplaceAll.substring(0, 2) + "***" + strReplaceAll.substring(strReplaceAll.length() - 2);
    }
}
