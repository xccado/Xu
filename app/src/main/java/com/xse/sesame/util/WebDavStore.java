package com.xse.sesame.util;

import java.io.File;

/**
 * 供 Kotlin 侧取模块私有目录的桥（Kotlin 不解析 lombok 生成的 getter，
 * 所以 WebDavBackup.kt 不直接调 ApplicationHook.getContext()）。
 */
public class WebDavStore {

    private static final String FILE_NAME = "webdav.json";

    public static File configFile() {
        android.content.Context ctx = com.xse.sesame.hook.ApplicationHook.getContext();
        return new File(ctx.getFilesDir(), FILE_NAME);
    }
}
