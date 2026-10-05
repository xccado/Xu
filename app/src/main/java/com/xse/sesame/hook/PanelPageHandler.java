package com.xse.sesame.hook;

import com.xse.sesame.util.Log;
import fi.iki.elonen.NanoHTTPD;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 远程面板静态页面（GET /panel）。
 * HTML 资产放 assets/panel/index.html（避免在 Java 里拼巨型字符串）；
 * 页面 JS 调用同源 /panel/api/*（由 PanelHandler 做 Bearer Token 鉴权），
 * 页面本身不含敏感数据，未带 Token 时 API 全部 401。
 */
public class PanelPageHandler implements HttpHandler {

    private static final String TAG = "PanelPageHandler";

    @Override
    public NanoHTTPD.Response handle(NanoHTTPD.IHTTPSession session, String body) {
        if (session.getMethod() != NanoHTTPD.Method.GET) {
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                    "text/plain", "Method Not Allowed");
        }
        try {
            android.content.Context ctx = ApplicationHook.getContext();
            InputStream is = ctx.getAssets().open("panel/index.html");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            is.close();
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
                    "text/html; charset=utf-8", new String(bos.toByteArray(), StandardCharsets.UTF_8));
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "text/plain", "panel asset missing");
        }
    }
}
