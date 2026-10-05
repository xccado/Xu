package com.xse.sesame.model.task.plantingFlowers;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.xse.sesame.hook.AuthCodeHelper;
import com.xse.sesame.util.Log;
import com.xse.sesame.util.TimeUtil;

public class PlantingFlowersRpcCall {

    private static final String TAG = PlantingFlowersRpcCall.class.getSimpleName();

    /** 支付宝小程序 AppID */
    public static final String APP_ID = "2021005162668238";
    /** 业务后端 */
    public static final String HOST = "https://plantingflowers.zhisanzhao.com";
    /** 数据中心（日常任务） */
    public static final String DATA_CENTER_HOST = "https://datacenter.zhisanzhao.com";

    /** AES 密钥（hex，32 字节 → AES-256） */
    private static final String D = "77122edad8d75adabe88c2b59fe3b4a1a12247f04cf0d1bac82e66abb6ae5bfa";
    /** AES 向量（hex，16 字节） */
    private static final String k = "cb252df83e58e8a2fab5a6eda437e9b1";
    private static final byte[] AES_KEY = hexToBytes(D);
    private static final byte[] AES_IV = hexToBytes(k);

    /** 专属对照字典：code "00"~"99" → 汉字 */
    private static final char[] DICT = {
            '爂', '蠄', '阅', '訇', '娈', '耋', '弋', '嬎', '蠔', '儕',
            '渙', '刚', '蔢', '紧', '琧', '圧', '鸨', '鄬', '刮', '踰',
            '圱', '漶', '蘺', '錽', '舾', '績', '栾', '湀', '詄', '癅',
            '腈', '晉', '湏', '豔', '捔', '鱕', '靖', '佘', '书', '筧',
            '獨', '奬', '虬', '牾', '鮁', '疁', '禃', '徆', '禇', '蒍',
            '鲎', '冐', '貑', '暕', '斗', '暘', '梚', '沛', '鶛', '媝',
            '妝', '皞', '醞', '碡', '犩', '莩', '暫', '龬', '颲', '侵',
            '亹', '咼', '嗀', '泀', '净', '鷁', '泂', '拂', '揂', '飃',
            '姄', '淒', '凔', '囕', '瓕', '飗', '裗', '嫘', '峘', '緙',
            '駟', '珢', '泣', '觧', '嗧', '泮', '鋰', '曷', '僻', '旾'
    };

    /** 登录会话 */
    public static class LoginSession {
        public final String token;
        public final String uid;

        public LoginSession(String token, String uid) {
            this.token = token;
            this.uid = uid;
        }
    }

    private PlantingFlowersRpcCall() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ═══════════════════ 登录 ═══════════════════

    /**
     * 动态登录：AuthCodeHelper 换取授权码 → code 换 token/uid
     * 返回 null 表示登录失败
     */
    public static LoginSession login(String manualToken) {
        String authCode = null;
        for (int i = 0; i < 3; i++) {
            authCode = AuthCodeHelper.getAuthCode(APP_ID);
            if (authCode != null && !authCode.isEmpty()) {
                break;
            }
            TimeUtil.sleep(1000);
        }

        String token = null;
        String uid = null;
        if (authCode != null && !authCode.isEmpty()) {
            try {
                String queryUrl = HOST + "/plantingFlowers/apiLogin/login?code="
                        + URLEncoder.encode(authCode, "UTF-8") + "&type=ALIPAY";
                JSONObject headers = new JSONObject();
                headers.put("Content-Type", "application/json;charset=UTF-8");
                headers.put("channel", "fenxiang");
                String resStr = requestRaw(queryUrl, "GET", null, headers);
                if (resStr == null || resStr.isEmpty()) {
                    // GET 为空则尝试 POST 登录兼容
                    JSONObject body = new JSONObject();
                    body.put("code", authCode);
                    body.put("type", "ALIPAY");
                    resStr = requestRaw(HOST + "/plantingFlowers/apiLogin/login", "POST", body.toString(), headers);
                }
                if (resStr != null && !resStr.isEmpty()) {
                    JSONObject res = new JSONObject(resStr);
                    JSONObject obj = extractObj(res);
                    if (obj != null && obj.has("token")) {
                        token = obj.optString("token");
                        JSONObject user = obj.optJSONObject("user");
                        if (user != null) {
                            uid = user.optString("alipayId", "");
                            if (uid.isEmpty()) {
                                uid = user.optString("id", "");
                            }
                            if (uid.isEmpty()) {
                                uid = user.optString("uid", "");
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                Log.err(TAG, "动态登录异常:", t);
            }
            if (token != null && !token.isEmpty()) {
                Log.other("种花大作战 动态登录成功");
                return new LoginSession(token, uid);
            }
        }

        if (manualToken != null && !manualToken.isEmpty()) {
            Log.other("种花大作战 动态登录不可用，使用手动 token");
            return new LoginSession(manualToken, "");
        }
        return null;
    }

    // ═══════════════════ 通用请求 ═══════════════════

    /**
     * 带业务头的 JSON 请求
     *
     * @param urlPath 以 http 开头则为完整地址，否则拼 HOST 前缀
     */
    public static JSONObject requestApi(String urlPath, String method, JSONObject data, String token, String uid) {
        String fullUrl = urlPath.startsWith("http") ? urlPath : HOST + urlPath;
        JSONObject headers;
        try {
            headers = buildHeaders(token, uid);
        } catch (JSONException e) {
            Log.err(TAG, "组装请求头失败: " + fullUrl, e);
            return null;
        }
        String body = null;
        if ("POST".equalsIgnoreCase(method)) {
            body = data != null ? data.toString() : "{}";
        }
        String resStr = requestRaw(fullUrl, method, body, headers);
        if (resStr == null || resStr.isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(resStr);
        } catch (Throwable t) {
            Log.err(TAG, "响应解析失败: " + fullUrl, t);
            return null;
        }
    }

    /**
     * 原始 HTTP 请求
     */
    public static String requestRaw(String url, String method, String body, JSONObject headers) {
        HttpURLConnection conn = null;
        try {
            URL target = new URL(url);
            conn = (HttpURLConnection) target.openConnection();
            conn.setRequestMethod(method.toUpperCase());
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            if (headers != null) {
                Iterator<String> keys = headers.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    conn.setRequestProperty(key, headers.optString(key));
                }
            }
            if ("POST".equalsIgnoreCase(method)) {
                conn.setDoOutput(true);
                try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
                    writer.write(body == null ? "" : body);
                }
            }
            int respCode = conn.getResponseCode();
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
        } catch (IOException e) {
            Log.err(TAG, "请求网络异常(" + url + "):", e);
            return null;
        } catch (Throwable t) {
            Log.err(TAG, "请求异常(" + url + "):", t);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 组装业务请求头：token / uid / dataCenterEncryption / coffeeEncryption / aesEncryption5
     */
    private static JSONObject buildHeaders(String token, String uid) throws JSONException {
        JSONObject headers = new JSONObject();
        headers.put("Content-Type", "application/json;charset=UTF-8");
        headers.put("channel", "fenxiang");
        if (token != null && !token.isEmpty()) {
            headers.put("token", token);
        }
        if (uid != null && !uid.isEmpty()) {
            headers.put("uid", uid);
            String today = getTodayStr();
            headers.put("dataCenterEncryption", md5(uid + "dj#i9a%u4j^2e#w*n43" + today));
            headers.put("coffeeEncryption", md5(uid + "R%v7j^4s&ug6c*8" + today));
            headers.put("aesEncryption5", getPlantingFlowersEncryption5(uid));
        }
        return headers;
    }

    // ═══════════════════ 响应工具 ═══════════════════

    /** 兼容 res.obj 与 res.data.obj 两种结构 */
    public static JSONObject extractObj(JSONObject res) {
        if (res == null) {
            return null;
        }
        JSONObject obj = res.optJSONObject("obj");
        if (obj != null) {
            return obj;
        }
        JSONObject data = res.optJSONObject("data");
        return data != null ? data.optJSONObject("obj") : null;
    }

    /** 兼容 res.obj 与 res.data.obj 两种结构，obj 可为数组或对象 */
    public static Object extractObjOrArray(JSONObject res) {
        if (res == null) {
            return null;
        }
        Object obj = res.opt("obj");
        if (obj != null && !JSONObject.NULL.equals(obj)) {
            return obj;
        }
        JSONObject data = res.optJSONObject("data");
        return data != null ? data.opt("obj") : null;
    }

    /** code==200 或 data.code==200 */
    public static boolean isOk(JSONObject res) {
        if (res == null) {
            return false;
        }
        if (res.optInt("code") == 200) {
            return true;
        }
        JSONObject data = res.optJSONObject("data");
        return data != null && data.optInt("code") == 200;
    }

    /** 提取 message（兼容 data.message） */
    public static String extractMessage(JSONObject res) {
        if (res == null) {
            return "";
        }
        String msg = res.optString("message", "");
        if (msg.isEmpty()) {
            JSONObject data = res.optJSONObject("data");
            if (data != null) {
                msg = data.optString("message", "");
            }
        }
        return msg;
    }

    // ═══════════════════ 专属加签算法 ═══════════════════

    /**
     * getPlantingFlowersEncryption5(uid)
     * 五步：y(uid) → _() 时间戳 → $(t,n) 交错混淆 → B(r) 字典映射 → j(o) AES 加扰
     */
    public static String getPlantingFlowersEncryption5(String uid) {
        String t = y(uid);
        String n = underscoreTime();
        String r = dollar(t, n);
        String o = dictMap(r);
        return jumble(o);
    }

    /** y(e)：偶数位数字 + 递增序号，>9 取个位 */
    private static String y(String e) {
        StringBuilder r = new StringBuilder();
        int a = 1;
        for (int o = 0; o < e.length(); o++) {
            if (o % 2 == 0) {
                int i = Character.digit(e.charAt(o), 10) + a;
                a++;
                if (i > 9) {
                    i = i % 10;
                }
                r.append(i);
            } else {
                r.append(e.charAt(o));
            }
        }
        return r.toString();
    }

    /** _()：当前时间戳（秒级）偶数位 + 递增序号，>9 取个位 */
    private static String underscoreTime() {
        String o = String.valueOf(System.currentTimeMillis());
        o = o.substring(0, o.length() - 3);
        StringBuilder n = new StringBuilder();
        int i = 1;
        for (int l = 0; l < o.length(); l++) {
            if (l % 2 == 0) {
                int u = Character.digit(o.charAt(l), 10) + i;
                i++;
                if (u > 9) {
                    u = u % 10;
                }
                n.append(u);
            } else {
                n.append(o.charAt(l));
            }
        }
        return n.toString();
    }

    /** $(e,t)：e 与 t 交错拼接 + 剩余后缀，再在位置 20/15/10/5 各插入一个随机数字 */
    private static String dollar(String e, String t) {
        int headLen = Math.min(t.length(), e.length());
        String a = e.substring(headLen);
        StringBuilder o = new StringBuilder();
        for (int i = 0; i < headLen; i++) {
            o.append(e.charAt(i)).append(t.charAt(i));
        }
        o.append(a);
        int[] positions = {20, 15, 10, 5};
        for (int pos : positions) {
            int s = (int) (Math.random() * 10);
            o.insert(Math.min(pos, o.length()), s);
        }
        return o.toString();
    }

    /** B(e)：两两分组查字典 → 汉字 charCode 4 位 hex，每组后补一个随机小写字母 */
    private static String dictMap(String e) {
        StringBuilder u = new StringBuilder();
        String letters = "abcdefghijklmnopqrstuvwxyz";
        for (int a = 0; a < e.length(); a += 2) {
            int end = Math.min(a + 2, e.length());
            String code = e.substring(a, end);
            char password = lookupDict(code);
            u.append(String.format(Locale.ROOT, "%04x", (int) password));
            u.append(letters.charAt((int) (Math.random() * letters.length())));
        }
        return u.toString();
    }

    /** 字典查找：code "00"~"99" → 汉字；查不到返回 0 */
    private static char lookupDict(String code) {
        try {
            int idx = Integer.parseInt(code);
            if (idx >= 0 && idx < DICT.length) {
                return DICT[idx];
            }
        } catch (NumberFormatException ignored) {
        }
        Log.debug("种花大作战 字典未命中: " + code);
        return 0;
    }

    /** j(e)：AES-256-CBC 加密 → hex，每 5 字符后插入一个随机小写字母 */
    private static String jumble(String e) {
        String r = aesEncryptHex(e);
        StringBuilder o = new StringBuilder();
        String letters = "abcdefghijklmnopqrstuvwxyz";
        for (int i = 0; i < r.length(); i += 5) {
            int end = Math.min(i + 5, r.length());
            o.append(r, i, end);
            o.append(letters.charAt((int) (Math.random() * letters.length())));
        }
        return o.toString();
    }

    /** AES-256-CBC 加密，输出小写 hex */
    private static String aesEncryptHex(String plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"), new IvParameterSpec(AES_IV));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(enc);
        } catch (Throwable t) {
            Log.err(TAG, "AES 加密异常:", t);
            return "";
        }
    }

    /** MD5 → 小写 hex */
    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return bytesToHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Throwable t) {
            Log.err(TAG, "MD5 异常:", t);
            return "";
        }
    }

    /** yyyy-MM-dd */
    private static String getTodayStr() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        return sdf.format(new java.util.Date());
    }

    // ═══════════════════ hex 工具 ═══════════════════

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(HEX_CHARS[(b >> 4) & 0xF]).append(HEX_CHARS[b & 0xF]);
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
