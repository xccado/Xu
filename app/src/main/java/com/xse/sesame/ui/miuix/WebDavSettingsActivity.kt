package com.xse.sesame.ui.miuix

import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xse.sesame.util.WebDavBackup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * WebDAV 备份配置页：地址/账号/密码/开关 + 立即备份。
 * 配置持久化到 App 私有目录 webdav.json（经 WebDavStore / WebDavBackup）。
 */
class WebDavSettingsActivity : MiuixBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent {
            WebDavScreen(this)
        }
    }
}

@Composable
fun WebDavScreen(activity: WebDavSettingsActivity) {
    val cfg = remember { WebDavBackup.loadConfig() }
    var url by remember { mutableStateOf(cfg.url) }
    var username by remember { mutableStateOf(cfg.username) }
    var password by remember { mutableStateOf(cfg.password) }
    var enabled by remember { mutableStateOf(cfg.enabled) }
    var lastTime by remember { mutableStateOf(cfg.lastTime) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf(if (cfg.lastTime.isNotEmpty()) "上次备份: ${cfg.lastTime}" else "") }

    Scaffold(
        topBar = {
            LogTopBar(
                title = "WebDAV 备份",
                onBack = { activity.finish() }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            SmallTitle(text = "服务器")
            ItemCard {
                var urlText by remember { mutableStateOf(url) }
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = "WebDAV 目录地址（如 https://dav.jianguoyun.com/dav/xse）",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp)
                )
                TextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "账号",
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "应用密码",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(10.dp))

            var enabledState by remember { mutableStateOf(enabled) }
            SwitchPreference(
                title = "每日自动备份",
                summary = if (lastTime.isEmpty()) "未备份过" else "上次备份: $lastTime",
                checked = enabledState,
                onCheckedChange = {
                    enabledState = it
                    enabled = it
                    WebDavBackup.saveConfig(
                        WebDavBackup.Config(enabled, url.trim(), username, password, lastTime)
                    )
                }
            )

            Spacer(Modifier.height(10.dp))
            TextButton(
                text = if (busy) "备份中…" else "保存并立即备份",
                onClick = {
                    if (busy) return@TextButton
                    WebDavBackup.saveConfig(
                        WebDavBackup.Config(enabled, url.trim(), username, password, lastTime)
                    )
                    busy = true
                    msg = "备份中…"
                    CoroutineScope(Dispatchers.Main).launch {
                        val r = withContext(Dispatchers.IO) {
                            WebDavBackup.backupNow(WebDavBackup.loadConfig())
                        }
                            busy = false
                        msg = r
                        com.xse.sesame.util.Log.record("WebDAV 手动备份: " + r)
                    }
                }
            )
            if (msg.isNotEmpty() && !msg.startsWith("上次")) {
                Text(
                    text = msg,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
