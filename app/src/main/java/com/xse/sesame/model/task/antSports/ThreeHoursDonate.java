package com.xse.sesame.model.task.antSports;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.xse.sesame.hook.ApplicationHook;
import com.xse.sesame.util.Log;
import com.xse.sesame.util.Status;

/**
 * 3小时公益捐步（移植自 SesameX v3.6.0）
 * 每日自动将今日运动步数捐赠给"3小时公益"平台（m.3hours.taobao.com），获得爱心豆。
 */
public class ThreeHoursDonate {

    private static final String APPID = "2019052265312523";
    private static final String BASE = "https://m.3hours.taobao.com";
    private static final String CSR_TOKEN = "e8d23e67-be3d-4812-92f5-6d7d3b53060b";
    private static final String CSR_UUID = "385EB086-93A9-45CE-8D9D-2207F4F96BDC";
    private static final String JSON = "application/json;charset=UTF-8";
    private static final String NAME = "3小时公益捐步";
    private static final String TAG = "ThreeHoursDonate";
    private static final String FLAG_TODAY = "antSports::threeHourDonate";

    public static void run() {
        try {
            String strMintAuthCode = mintAuthCode();
            if (strMintAuthCode != null && strMintAuthCode.length() != 0) {
                String strEncode = URLEncoder.encode(strMintAuthCode, "UTF-8");
                HashMap<String, String> map = new HashMap<>();
                map.put("Content-Type", JSON);
                map.put("csr-token", CSR_TOKEN);
                map.put("csr-uuid", CSR_UUID);
                map.put("csr-account-v2", "true");
                map.put("csr-front-v", "1");
                String str = httpGet(BASE + "/donateStep/v2/getTodayRemainStep?authCode=" + strEncode + "&timeZone=Asia/Shanghai", map);
                if (str == null) {
                    Log.record("3小时公益捐步查询失败(网络)");
                    return;
                }
                if (!str.contains("61011") && !str.contains("授权码")) {
                    if (!str.contains("\"code\":405") && !str.contains("请登录")) {
                        if (str.contains("\"allDonated\":true")) {
                            Log.record("3小时公益捐步今日已完成");
                            Status.flagToday(FLAG_TODAY);
                            return;
                        }
                        int iExtractInt = extractInt(str, "todaySteps");
                        int iExtractInt2 = extractInt(str, "remainSteps");
                        if (iExtractInt2 <= 0) {
                            Log.record("3小时公益捐步今日已完成(无可捐步数)");
                            Status.flagToday(FLAG_TODAY);
                            return;
                        }
                        if (iExtractInt <= 0) {
                            iExtractInt = iExtractInt2;
                        }
                        HashMap<String, Object> map2 = new HashMap<>();
                        map2.put("todayExerciseSteps", iExtractInt);
                        String strPost = httpPost(BASE + "/donateStep/v2/donate?authCode=" + strEncode + "&timeZone=Asia/Shanghai", map2, map);
                        if (strPost == null) {
                            Log.record("3小时公益捐步捐步失败(网络)");
                            return;
                        }
                        if (!strPost.contains("\"allDonated\":true") && !strPost.contains("\"success\":true")) {
                            if (!strPost.contains("\"code\":201") && !strPost.contains("已全部捐出")) {
                                if (!strPost.contains("61011") && !strPost.contains("授权码")) {
                                    if (!strPost.contains("\"code\":405") && !strPost.contains("请登录")) {
                                        Log.record("3小时公益捐步捐步返回:" + strPost);
                                        return;
                                    }
                                    Log.record("3小时公益捐步csr-token失效,请重新抓取更新");
                                    return;
                                }
                                Log.record("3小时公益捐步authCode无效/过期");
                                return;
                            }
                            Log.record("3小时公益捐步今日已完成");
                            Status.flagToday(FLAG_TODAY);
                            return;
                        }
                        int iExtractInt3 = extractInt(strPost, "donatedSteps");
                        int iExtractInt4 = extractInt(strPost, "loveBeanNum");
                        StringBuilder sbAppend = new StringBuilder().append("3小时公益捐步❤️[捐");
                        if (iExtractInt3 > 0) {
                            iExtractInt2 = iExtractInt3;
                        }
                        Log.other(sbAppend.append(iExtractInt2).append("步]").append(iExtractInt4 > 0 ? "#得" + iExtractInt4 + "爱心豆" : "").toString());
                        Status.flagToday(FLAG_TODAY);
                        return;
                    }
                    Log.record("3小时公益捐步csr-token失效,请重新抓取更新");
                    return;
                }
                Log.record("3小时公益捐步authCode无效/过期");
                return;
            }
            Log.record("3小时公益捐步authCode铸造失败");
        } catch (Throwable th) {
            Log.i(TAG, "ThreeHoursDonate err:");
            Log.printStackTrace(TAG, th);
        }
    }

    private static String mintAuthCode() {
        Method method;
        Object field;
        Object field2;
        ClassLoader classLoader = ApplicationHook.getClassLoader();
        if (classLoader == null) {
            Log.record("3小时公益捐步无Alipay classLoader");
            return null;
        }
        for (int i = 0; i < 5; i++) {
            try {
                Object objNewInstance = classLoader.loadClass("com.alibaba.ariver.permission.openauth.model.request.AuthSkipRequestModel").newInstance();
                setField(objNewInstance, "appId", APPID);
                ArrayList<String> arrayList = new ArrayList<>();
                arrayList.add("auth_base");
                setField(objNewInstance, "scopeNicks", arrayList);
                setField(objNewInstance, "fromSystem", "mobilegw_android");
                setField(objNewInstance, "currentPageUrl", "https://2019052265312523.hybrid.alipay-eco.com/index.html#pages/EWalk/index?__appxPageId=2&spm=a211d9.10083988.firstCard&uuid=385EB086-93A9-45CE-8D9D-2207F4F96BDC&vid=3311518228555");
                HashMap<String, String> map = new HashMap<>();
                map.put("channel", "tinyapp");
                map.put("clientAppId", APPID);
                setField(objNewInstance, "appExtInfo", map);
                HashMap<String, String> map2 = new HashMap<>();
                map2.put("tinyAppChannel", "1002");
                map2.put("quickOauthLbsAuthStatus", "-1");
                setField(objNewInstance, "extInfo", map2);
                setField(objNewInstance, "state", "dGhyZWVob3Vyc19hdXRo");
                Class<?> clsLoadClass = classLoader.loadClass("com.alibaba.ariver.rpc.biz.proxy.Oauth2AuthCodeServiceImpl");
                Object objNewInstance2 = clsLoadClass.newInstance();
                Method[] methods = clsLoadClass.getMethods();
                int length = methods.length;
                int i2 = 0;
                while (true) {
                    if (i2 >= length) {
                        method = null;
                        break;
                    }
                    method = methods[i2];
                    if (method.getName().equals("getAuthSkipResult") && method.getParameterTypes().length == 3) {
                        break;
                    }
                    i2++;
                }
                if (method == null) {
                    Log.record("3小时公益捐步未找到getAuthSkipResult");
                    return null;
                }
                Object objInvoke = method.invoke(objNewInstance2, APPID, null, objNewInstance);
                if (objInvoke != null && (field = getField(objInvoke, "authExecuteResult")) != null && (field2 = getField(field, "authCode")) != null && field2.toString().length() > 0) {
                    return field2.toString();
                }
                try {
                    Thread.sleep(1500L);
                } catch (InterruptedException unused) {
                }
            } catch (Throwable th) {
                Log.i(TAG, "mint attempt" + i + " err:" + th);
            }
        }
        return null;
    }

    private static String httpGet(String strUrl, Map<String, String> headers) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(strUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            int respCode = conn.getResponseCode();
            return readResponse(conn, respCode);
        } catch (Throwable th) {
            Log.i(TAG, "httpGet err:" + th);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String httpPost(String strUrl, Map<String, Object> body, Map<String, String> headers) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(strUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setDoOutput(true);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : body.entrySet()) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            }
            sb.append("}");
            try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(sb.toString());
            }
            int respCode = conn.getResponseCode();
            return readResponse(conn, respCode);
        } catch (Throwable th) {
            Log.i(TAG, "httpPost err:" + th);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readResponse(HttpURLConnection conn, int respCode) throws Exception {
        StringBuilder responseText = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                respCode >= 200 && respCode <= 299 ? conn.getInputStream() : conn.getErrorStream(),
                StandardCharsets.UTF_8
        ))) {
            String line;
            while ((line = reader.readLine()) != null) {
                responseText.append(line);
            }
        }
        return responseText.toString();
    }

    private static void setField(Object obj, String str, Object obj2) throws Exception {
        Field fieldFindField = findField(obj.getClass(), str);
        if (fieldFindField == null) {
            throw new NoSuchFieldException(str);
        }
        fieldFindField.setAccessible(true);
        fieldFindField.set(obj, obj2);
    }

    private static Object getField(Object obj, String str) {
        try {
            Field fieldFindField = findField(obj.getClass(), str);
            if (fieldFindField == null) {
                return null;
            }
            fieldFindField.setAccessible(true);
            return fieldFindField.get(obj);
        } catch (Throwable unused) {
            return null;
        }
    }

    private static Field findField(Class<?> cls, String str) {
        while (cls != null) {
            try {
                return cls.getDeclaredField(str);
            } catch (Throwable unused) {
                cls = cls.getSuperclass();
            }
        }
        return null;
    }

    private static int extractInt(String str, String str2) {
        char cCharAt;
        try {
            String str3 = "\"" + str2 + "\":";
            int iIndexOf = str.indexOf(str3);
            if (iIndexOf < 0) {
                return -1;
            }
            int length = iIndexOf + str3.length();
            int i = length;
            while (i < str.length() && (((cCharAt = str.charAt(i)) >= '0' && cCharAt <= '9') || cCharAt == '-')) {
                i++;
            }
            if (i == length) {
                return -1;
            }
            return Integer.parseInt(str.substring(length, i));
        } catch (Throwable unused) {
            return -1;
        }
    }
}
