package com.xse.sesame.util

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 配置云备份（WebDAV）：把 config_v2.json / statistics.json / statHistory.json 上传到
 * 用户自己的 WebDAV 空间（坚果云、Alist、自建等），实现配置跨设备容灾。
 * <p>
 * 配置持久化在 App 私有目录 webdav.json（不随 Xposed 主配置走）。
 * 上传以 PUT 覆盖式写 <目录>/xse-<时间戳>-<文件名>，Basic Auth，不落口令日志。
 */
object WebDavBackup {

    private const val TAG = "WebDavBackup"

    /** 备份配置（可序列化） */
    data class Config(
        var enabled: Boolean = false,
        var url: String = "",       // 目录 URL，如 https://dav.example.com/dav/xse
        var username: String = "",
        var password: String = "",
        var lastTime: String = ""
    )

    private fun configFile(): File = WebDavStore.configFile()

    @JvmStatic
    fun loadConfig(): Config {
        return try {
            val f = configFile()
            if (f.exists()) {
                JsonUtil.copyMapper().readValue(FileUtil.readFromFile(f), Config::class.java)
            } else {
                Config()
            }
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, t)
            Config()
        }
    }

    @JvmStatic
    fun saveConfig(config: Config): Boolean {
        return try {
            FileUtil.write2File(
                JsonUtil.copyMapper().writeValueAsString(config),
                configFile()
            )
            true
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, t)
            false
        }
    }

    /**
     * 立即备份核心文件。返回人类可读结果摘要。
     * 阻塞网络 IO，调用方自行放线程。
     */
    @JvmStatic
    fun backupNow(config: Config): String {
        if (config.url.isBlank()) return "未配置 WebDAV 地址"
        if (config.username.isEmpty()) return "未配置账号"

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = config.url.trimEnd('/')
        val results = mutableListOf<String>()

        val files = listOfNotNull(
            FileUtil.getDefaultConfigV2File().takeIf { it.exists() },
            FileUtil.getStatisticsFile().takeIf { it.exists() },
            FileUtil.getStatHistoryFile().takeIf { it.exists() }
        )
        if (files.isEmpty()) return "没有可备份的文件"

        for (f in files) {
            try {
                val body = FileUtil.readFromFile(f)
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder()
                    .url("$dir/xse-$stamp-${f.name}")
                    .put(body)
                    .header("Authorization", Credentials.basic(config.username, config.password))
                    .build()
                client.newCall(req).execute().use { resp ->
                    val code = resp.code
                    results.add(f.name + ":" + if (code in 200..299) "OK" else "HTTP $code")
                }
            } catch (t: Throwable) {
                Log.printStackTrace(TAG, t)
                results.add(f.name + ":FAIL(" + (t.message ?: "err") + ")")
            }
        }

        val failed = results.any { it.contains("FAIL") || it.contains("HTTP") }
        if (!failed) {
            config.lastTime = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            saveConfig(config)
            Log.record("WebDAV 备份完成: " + results.joinToString(" "))
        } else {
            Log.record("WebDAV 备份异常: " + results.joinToString(" "))
        }
        return results.joinToString(" ")
    }

    /** 每日自动备份入口：启用后每天首次触发；调用方自行放线程 */
    @JvmStatic
    fun autoBackup() {
        try {
            val cfg = loadConfig()
            if (!cfg.enabled) return
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            if (cfg.lastTime.startsWith(today)) return  // 今天已备
            val r = backupNow(cfg)
            Log.record("WebDAV 自动备份: " + r)
        } catch (t: Throwable) {
            Log.printStackTrace(TAG, t)
        }
    }
}
