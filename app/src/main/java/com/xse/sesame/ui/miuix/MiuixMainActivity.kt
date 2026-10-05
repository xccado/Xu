package com.xse.sesame.ui.miuix

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xse.sesame.R
import com.xse.sesame.data.AppConfig
import com.xse.sesame.data.ConfigPreload
import com.xse.sesame.data.ConfigV2
import com.xse.sesame.data.Model
import com.xse.sesame.data.ModelGroup
import com.xse.sesame.data.RunType
import com.xse.sesame.data.ViewAppInfo
import com.xse.sesame.util.FileUtil
import com.xse.sesame.util.LanguageUtil
import com.xse.sesame.util.Log
import com.xse.sesame.util.PermissionUtil
import com.xse.sesame.util.Statistics
import com.xse.sesame.util.Statistics.DataType
import com.xse.sesame.util.Statistics.TimeType
import com.xse.sesame.util.StringUtil
import com.xse.sesame.util.ToastUtil
import com.xse.sesame.util.idMap.UserIdMap
import androidx.compose.ui.window.Dialog
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.util.Calendar

class MiuixMainActivity : MiuixBaseActivity() {

    companion object {
        private const val PREFS_UI = "sesame_ui_state"
        private const val KEY_LAST_SELECTED_USER = "last_selected_user_id"

        /** 最多探测次数：每次间隔 3 秒，共约 15 秒，覆盖框架冷启动绑定晚于 Activity 的情况 */
        private const val MAX_RUN_TYPE_PROBE_TIMES = 5

        /**
         * 设备显示名：优先读市场名（如 Xiaomi 13）。
         * 多数设备的 ro.product.model 只是内部型号编号，多设备会显示相同，须用 marketname。
         */
        fun getDeviceDisplayName(): String {
            try {
                val clazz = Class.forName("android.os.SystemProperties")
                val get = clazz.getMethod("get", String::class.java)
                val market = get.invoke(null, "ro.product.marketname") as? String
                if (!market.isNullOrBlank()) {
                    return market
                }
            } catch (_: Throwable) {
            }
            return Build.MODEL ?: Build.DEVICE ?: ""
        }
    }

    var runTypeText by mutableStateOf("")

    /** 可观察的激活状态：页面订阅它，状态广播到达时自动重组刷新 */
    var uiRunType by mutableStateOf(RunType.DISABLE)
    var statisticsText by mutableStateOf("")
    var hasPermission by mutableStateOf(false)

    /** 统计版本号：刷新后自增，首页统计订阅它触发重组 */
    var statisticsVersion by mutableStateOf(0)

    /** 是否已请求过系统文件权限，用于 onResume 检测授权返回 */
    var hasRequestedPermission by mutableStateOf(false)

    private val uiPrefs by lazy { getSharedPreferences(PREFS_UI, MODE_PRIVATE) }

    /** 配置页当前选中的账号 userId；null=默认配置。必须持久在 Activity 级别，否则切换底部 tab 后 ConfigTab 离开组合会丢失。
     *  类型须为 MutableState 本身，ConfigTab 内通过 `by activity.selectedUserId` 委托读写。 */
    val selectedUserId = mutableStateOf<String?>(null)

    /** 持久化“配置页上次选中账号”，进程重启/重新打开 UI 后自动恢复，不再跳回默认 */
    fun persistSelectedAccount(userId: String?) {
        uiPrefs.edit().putString(KEY_LAST_SELECTED_USER, userId).apply()
    }

    /** 读取上次选中的账号 id(可能已失效,由 ConfigTab 渲染时对目录校验并回退默认) */
    fun restoreSelectedAccount(): String? {
        val last = uiPrefs.getString(KEY_LAST_SELECTED_USER, null) ?: return null
        return if (StringUtil.isEmpty(last)) null else last
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isClick = false

    /** 激活探测已重试次数，上限见 MAX_RUN_TYPE_PROBE_TIMES */
    private var runTypeProbeTimes = 0

    /**
     * 激活探测：仅当真实状态仍为 DISABLE 时才显示未激活，并在超时前周期性重试，
     * 避免覆盖晚到的激活信号，也避免框架绑定较慢时误报未激活。
     */
    private val titleRunner: Runnable = Runnable {
        if (ViewAppInfo.getRunType() == RunType.DISABLE) {
            runTypeProbeTimes++
            updateSubTitle(RunType.DISABLE)
            if (runTypeProbeTimes < MAX_RUN_TYPE_PROBE_TIMES) {
                sendQueryBroadcast()
                handler.postDelayed(titleRunner, 3000)
            }
        } else {
            runTypeProbeTimes = 0
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.i("view broadcast action:" + action + " intent:" + intent)
            if (action != null) {
                when (action) {
                    "com.xse.sesame.status" -> {
                        // 模块已被 LSPosed 启用并注入支付宝，标记为已激活
                        ViewAppInfo.setRunTypeByCode(RunType.MODEL.getCode())
                        runTypeProbeTimes = 0
                        handler.removeCallbacks(titleRunner)
                        updateSubTitle(RunType.MODEL)
                        handler.removeCallbacks(titleRunner)
                        if (isClick) {
                            ToastUtil.show(context, "Xse 加载状态正常")
                            isClick = false
                        }
                    }

                    "com.xse.sesame.update" -> {
                        refreshStatistics()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 分组入口按“组内有配置模型”过滤，必须先初始化模型注册表，否则全组为空、入口全消失
        Model.initAllModel()
        // runType 被模块置为 MODEL（onModuleLoaded）时立即刷新界面，无需手动加载配置
        ViewAppInfo.setRunTypeListener {
            runOnUiThread {
                handler.removeCallbacks(titleRunner)
                updateSubTitle(ViewAppInfo.getRunType())
            }
        }
        ViewAppInfo.checkRunType()
        updateSubTitle(ViewAppInfo.getRunType())
        // 恢复“配置页上次选中账号”(进程重启不丢失); 若目录已不存在由 ConfigTab 渲染时回退
        selectedUserId.value = restoreSelectedAccount()
        val intentFilter = IntentFilter()
        intentFilter.addAction("com.xse.sesame.status")
        intentFilter.addAction("com.xse.sesame.update")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, intentFilter)
        }
        setAppContent {
            MainScreen(this)
        }
    }

    override fun onResume() {
        super.onResume()
        // 激活状态探测独立于存储权限：防止 titleRunner 重复累积，先清空再启动
        if (RunType.DISABLE == ViewAppInfo.getRunType()) {
            handler.removeCallbacks(titleRunner)
            runTypeProbeTimes = 0
            sendQueryBroadcast()
            handler.postDelayed(titleRunner, 3000)
        }
        checkPermissionAndRefresh()
    }

    /** 检查文件权限，若已授权则刷新统计；同时处理首次请求权限的场景 */
    private fun checkPermissionAndRefresh() {
        if (hasRequestedPermission) {
            hasRequestedPermission = false
            if (PermissionUtil.checkFilePermissions(this)) {
                hasPermission = true
                refreshStatistics()
            }
        } else if (!hasPermission && PermissionUtil.checkFilePermissions(this)) {
            hasPermission = true
            refreshStatistics()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            hasPermission = granted
            if (granted) refreshStatistics()
        }
    }

    /**
     * 重新从统计文件加载并通知首页刷新。
     * 基于可观察的 statisticsVersion 触发重组，解决"首次进入首页统计为 0、必须进配置返回才刷新"的问题。
     */
    fun refreshStatistics() {
        if (!hasPermission) return
        try {
            Statistics.load()
            Statistics.updateDay(Calendar.getInstance())
            statisticsVersion++
            statisticsText = Statistics.getText(this)
            // 同步刷新桌面小部件（同进程读同一份静态快照）
            com.xse.sesame.ui.widget.XseWidget.pushUpdate(this)
        } catch (e: Exception) {
            Log.printStackTrace(e)
        }
    }

    override fun onPause() {
        super.onPause()
        // 离开前台即停止状态轮询，避免后台无谓广播与泄漏
        handler.removeCallbacks(titleRunner)
    }

    fun updateSubTitle(runType: RunType) {
        uiRunType = runType
        runTypeText = when (runType) {
            RunType.DISABLE -> ViewAppInfo.getAppTitle() + "【" + getString(R.string.disable) + "】"
            RunType.MODEL -> ViewAppInfo.getAppTitle() + "【" + getString(R.string.activated) + "】"
            RunType.PACKAGE -> ViewAppInfo.getAppTitle() + "【" + getString(R.string.loading) + "】"
        }
    }

    fun sendStatus() {
        try {
            isClick = true
            sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.status"))
        } catch (th: Throwable) {
            Log.i("view sendBroadcast status err:")
            Log.printStackTrace(th)
        }
    }

    /** 向支付宝进程查询本模块注入状态（不弹 Toast），由 titleRunner 周期性调用 */
    fun sendQueryBroadcast() {
        try {
            sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.status"))
        } catch (th: Throwable) {
            Log.i("view sendBroadcast status err:")
            Log.printStackTrace(th)
        }
    }

    /**
     * 通知支付宝进程重载共享配置(日志各分项开关等)，使开关在注入进程中即时生效。
     *
     * 不发这个广播的话，UI 只把 appConfig.json 落盘，但支付宝进程内存里的 AppConfig.INSTANCE
     * 仍是旧值，于是"抓包记录"等开关看着开了、日志却依旧为空。
     */
    fun broadcastReloadConfig() {
        try {
            sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.reloadConfig"))
        } catch (th: Throwable) {
            Log.i("view sendBroadcast reloadConfig err:")
            Log.printStackTrace(th)
        }
    }

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            ToastUtil.show(this, "无法打开链接")
        }
    }

    fun toggleLanguage() {
        val appConfig = AppConfig.INSTANCE
        appConfig.languageSimplifiedChinese = !appConfig.languageSimplifiedChinese
        if (AppConfig.save()) {
            LanguageUtil.setLocal(this)
            recreate()
        }
    }

    fun isIconHidden(): Boolean {
        val alias = ComponentName(this, "com.xse.sesame.ui.MainActivityAlias")
        return packageManager.getComponentEnabledSetting(alias) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    fun toggleHideIcon() {
        val alias = ComponentName(this, "com.xse.sesame.ui.MainActivityAlias")
        val state = packageManager.getComponentEnabledSetting(alias)
        val newState = if (state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        packageManager.setComponentEnabledSetting(alias, newState, PackageManager.DONT_KILL_APP)
    }

    fun exportStatistics(): Uri? {
        return FileUtil.getExportedStatisticsFile()?.let { Uri.fromFile(it) }
    }

    fun importStatistics(): Boolean {
        val src = FileUtil.getExportedStatisticsFile()
        if (src != null && FileUtil.copyTo(src, FileUtil.getStatisticsFile())) {
            statisticsText = Statistics.getText(this)
            return true
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(broadcastReceiver)
        } catch (_: Exception) {
        }
    }
}

@Composable
fun MainScreen(activity: MiuixMainActivity) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        if (!PermissionUtil.checkOrRequestFilePermissions(activity)) {
            activity.hasPermission = false
        } else {
            activity.hasPermission = true
        }
        onDispose { }
    }

    Scaffold(
        bottomBar = {
            FloatingNavBar(
                items = listOf(
                    NavBarTab("首页", Icons.Filled.Home),
                    NavBarTab("日志", Icons.Filled.Description),
                    NavBarTab("配置", Icons.Filled.Tune),
                    NavBarTab("设置", Icons.Filled.Settings),
                ),
                selected = selectedTab,
                onSelect = { selectedTab = it },
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            when (selectedTab) {
                0 -> HomeTab(activity)
                1 -> LogsTab(activity)
                2 -> ConfigTab(activity)
                3 -> SettingsTab(activity)
            }
        }
    }
}

/** 悬浮导航栏的条目定义 */
data class NavBarTab(val label: String, val icon: ImageVector)

/**
 * 悬浮拟态底部导航栏。
 *
 * 与背景同色的拟态胶囊从纯白底面上"浮起"，两侧留边、底部悬空，
 * 选中项以凹陷槽 + 暖橙高亮呈现，形成软按压的交互隐喻。
 */
@Composable
fun FloatingNavBar(
    items: List<NavBarTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .neuRaised(RoundedCornerShape(30.dp), 10.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, tab ->
                val isSelected = index == selected
                Column(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (isSelected) Modifier.neuPressed(RoundedCornerShape(20.dp))
                            else Modifier
                        )
                        .clickable { onSelect(index) }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val tint =
                        if (isSelected) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary
                    Image(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(23.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        fontSize = 11.sp,
                        color = tint
                    )
                }
            }
        }
    }
}

@Composable
fun HomeTab(activity: MiuixMainActivity) {
    val context = LocalContext.current
    val activated = activity.uiRunType == RunType.MODEL
    val appTitle = ViewAppInfo.getAppTitle()
    val version = ViewAppInfo.getAppVersion()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        Text(
            text = "Xse",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onBackground
        )
        // 品牌渐变副标：曜石紫 → 霓虹粉
        Text(
            text = "NIGHT NEON",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 3.sp,
            style = androidx.compose.ui.text.TextStyle(
                brush = Brush.linearGradient(
                    listOf(Color(0xFF7C5CFF), Color(0xFFF56A8C))
                )
            ),
            modifier = Modifier.padding(top = 2.dp)
        )
    }
    Spacer(Modifier.height(14.dp))

    // 能量统计卡：顶部状态条（激活状态 + 标题），下方 4×4 数据网格
    // 透传统计版本号：统计刷新后本卡随之重组并重读最新快照
    EnergyStatsCard(activated = activated, version = version, dataVersion = activity.statisticsVersion)
    Spacer(Modifier.height(16.dp))

    // 近 7 天收取趋势图（拟态卡）
    EnergyTrendCard(dataVersion = activity.statisticsVersion)
    Spacer(Modifier.height(16.dp))

    // 随机一言：点击整卡换一句
    HitokotoCard()
    Spacer(Modifier.height(16.dp))

    // Q版鲸鱼娘桌宠展示位：一言下方留形象，会自己动、可拖动，点击进对话。
    PetHomeImage()
    Spacer(Modifier.height(16.dp))
}

/**
 * 能量统计卡（拟态）：顶部状态条 + 统计网格。
 * 顶部左侧红点 + "已激活/未加载"状态 + 版本号，右侧"能量统计"标题；
 * 统计口径与上游一致：5 行 × 3 列，行=收/帮/浇/被水/浇水，列=今日/本月/今年。
 */
@Composable
fun EnergyStatsCard(activated: Boolean, version: String, dataVersion: Int) {
    // dataVersion 仅用于建立 Compose 重组依赖：数值变化时重读 Statistics 静态快照
    @Suppress("UNUSED_VARIABLE")
    val statsRevision = dataVersion
    // 统计口径与上游一致：行 = 收 / 帮 / 浇 / 被水 / 浇水，列 = 今日 / 本月 / 今年
    val timeTypes = listOf(TimeType.DAY, TimeType.MONTH, TimeType.YEAR)
    val colHeaders = listOf("今日", "本月", "今年")
    val rows = listOf(
        "收" to DataType.COLLECTED,
        "帮" to DataType.HELPED,
        "浇" to DataType.WATERED,
        "被水" to DataType.WATEREDCOUNT,
        "浇水" to DataType.WATERINGCOUNT
    )

    Column(
        Modifier
            .fillMaxWidth()
            .neuRaised(RoundedCornerShape(24.dp), 5.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        // 顶部状态条
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 状态红点
                Box(
                    Modifier
                        .size(10.dp)
                        .background(
                            if (activated) Color(0xFF4CAF50) else Color(0xFFE0532C),
                            CircleShape
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (activated) "已激活" else "未加载",
                    fontSize = 15.sp,
                    color = if (activated) MiuixTheme.colorScheme.secondary
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "· $version",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Text(
                text = "能量统计",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }

        Spacer(Modifier.height(8.dp))

        // 表头
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f))
            colHeaders.forEach { h ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = h,
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        // 数据行
        rows.forEach { (rowLabel, dt) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    Text(
                        text = rowLabel,
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                timeTypes.forEachIndexed { colIdx, tt ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = "%,d".format(Statistics.getData(tt, dt)),
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onBackground,
                            fontWeight = if (colIdx == 0) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

/** 一言（Hitokoto）客户端：短超时，避免阻塞首页 */
private val hitokotoClient by lazy {
    okhttp3.OkHttpClient.Builder()
        .connectTimeout(java.util.concurrent.TimeUnit.SECONDS.toMillis(5), java.util.concurrent.TimeUnit.MILLISECONDS)
        .readTimeout(java.util.concurrent.TimeUnit.SECONDS.toMillis(5), java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()
}

private const val HITOKOTO_URL = "https://v1.hitokoto.cn/?encode=json"

/**
 * 随机一言卡片：拟态表面 + 点击刷新。
 * 数据来自 hitokoto.cn；网络不可用时静默保留上一句（首次为内置句）。
 */
@Composable
fun HitokotoCard() {
    var sentence by remember { mutableStateOf("慢慢来，比较快。") }
    var source by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder().url(HITOKOTO_URL).build()
                hitokotoClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val node = com.fasterxml.jackson.databind.ObjectMapper().readTree(body)
                            sentence = node.path("hitokoto").asText(sentence)
                            val from = node.path("from").asText("")
                            val fromWho = node.path("from_who").asText("")
                            source = when {
                                from.isNotBlank() && fromWho.isNotBlank() -> "—— $fromWho「$from」"
                                from.isNotBlank() -> "——「$from」"
                                fromWho.isNotBlank() -> "—— $fromWho"
                                else -> ""
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // 网络不可用：保留当前句子，不打扰用户
            }
        }
        loading = false
    }

    Column(
        Modifier
            .fillMaxWidth()
            .neuRaised(RoundedCornerShape(24.dp), 5.dp)
            .clickable(enabled = !loading) { refreshKey++ }
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "「 一言 」",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            // 刷新按钮：凹陷圆槽
            Box(
                Modifier
                    .size(30.dp)
                    .neuPressed(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "换一句",
                    modifier = Modifier.size(15.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                        if (loading) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = sentence,
            fontSize = 15.sp,
            lineHeight = 23.sp,
            color = MiuixTheme.colorScheme.onBackground
        )
        if (source.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = source,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun LogsTab(activity: MiuixMainActivity) {
    Text(
        text = "日志",
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
    )

    SmallTitle(text = "分类记录")
    CardList {
        var forest by remember { mutableStateOf(AppConfig.INSTANCE.enableForestLog ?: true) }
        LogSwitchRow("森林记录", forest, onClick = { openLog(activity, LogType.FOREST) }) {
            forest = it
            AppConfig.INSTANCE.enableForestLog = it
            AppConfig.save()
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog("forest")
        }
        var farm by remember { mutableStateOf(AppConfig.INSTANCE.enableFarmLog ?: true) }
        LogSwitchRow("庄园记录", farm, onClick = { openLog(activity, LogType.FARM) }) {
            farm = it
            AppConfig.INSTANCE.enableFarmLog = it
            AppConfig.save()
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog("farm")
        }
        var goldenBeans by remember { mutableStateOf(AppConfig.INSTANCE.enableGoldenBeansLog ?: true) }
        LogSwitchRow("金豆记录", goldenBeans, onClick = { openLog(activity, LogType.GOLDENBEANS) }) {
            goldenBeans = it
            AppConfig.INSTANCE.enableGoldenBeansLog = it
            AppConfig.save()
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog("goldenbeans")
        }
        var other by remember { mutableStateOf(AppConfig.INSTANCE.enableOtherLog ?: true) }
        LogSwitchRow("其他记录", other, onClick = { openLog(activity, LogType.OTHER) }) {
            other = it
            AppConfig.INSTANCE.enableOtherLog = it
            AppConfig.save()
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog("other")
        }
    }
    Spacer(Modifier.height(16.dp))

    SmallTitle(text = "系统记录")
    CardList {
        var debug by remember { mutableStateOf(AppConfig.INSTANCE.enableDebugLog ?: false) }
        LogSwitchRow("抓包记录", debug, onClick = { openLog(activity, LogType.DEBUG) }) {
            debug = it
            AppConfig.INSTANCE.enableDebugLog = it
            AppConfig.save()
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog("debug")
        }
        var error by remember { mutableStateOf(AppConfig.INSTANCE.enableViewErrorLog ?: true) }
        LogSwitchRow("查看异常日志", error, onClick = { openLog(activity, LogType.ERROR) }) {
            error = it
            AppConfig.INSTANCE.enableViewErrorLog = it
            AppConfig.save()
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog("error")
        }
        var runtime by remember { mutableStateOf(AppConfig.INSTANCE.enableViewRuntimeLog ?: true) }
        LogSwitchRow("查看运行日志", runtime, onClick = { openLog(activity, LogType.RUNTIME) }) {
            runtime = it
            AppConfig.INSTANCE.enableViewRuntimeLog = it
            AppConfig.save()
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog("runtime")
        }
    }
    Spacer(Modifier.height(16.dp))
}

/** 日志条目行：点按整行进入对应日志详情；右侧开关控制是否记录。每个功能独立一张纯白拟态卡片。 */
@Composable
fun LogSwitchRow(title: String, checked: Boolean, onClick: () -> Unit, onCheckedChange: (Boolean) -> Unit) {
    ItemCard(
        modifier = Modifier.clickable(onClick = onClick),
        verticalPadding = 8.dp,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                color = MiuixTheme.colorScheme.onBackground
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/** 打开日志查看器(显示指定日志类型的全部条目) */
fun openLog(activity: MiuixMainActivity, logType: LogType) {
    try {
        activity.startActivity(
            Intent(activity, MiuixLogViewerActivity::class.java)
                .putExtra(LogType.EXTRA_LOG_TYPE, logType.name)
        )
    } catch (t: Throwable) {
        Log.printStackTrace(t)
    }
}

/** 配置分组入口页(图2)的图标与描述映射:全部 ModelGroup 均展示,不隐藏空分组。
 *  图标统一改用 Material 矢量图标替代此前的 emoji：emoji 在不同机型/字体下的字重、大小、
 *  彩色渲染都不一致，和纯白拟态底色放在一起显乱；矢量图标可统一尺寸与主色，观感更整齐。 */
internal val GROUP_ICON: Map<ModelGroup, ImageVector> = mapOf(
    ModelGroup.BASE to Icons.Filled.Tune,
    ModelGroup.FOREST to Icons.Filled.Forest,
    ModelGroup.FARM to Icons.Filled.Egg,
    ModelGroup.STALL to Icons.Filled.Storefront,
    ModelGroup.ORCHARD to Icons.Filled.Agriculture,
    ModelGroup.GOLDENBEANS to Icons.Filled.MonetizationOn,
    ModelGroup.SPORTS to Icons.AutoMirrored.Filled.DirectionsRun,
    ModelGroup.MEMBER to Icons.Filled.WorkspacePremium,
    ModelGroup.OTHER to Icons.Filled.Widgets
)

/** 分组图标主色：低饱和冷色系，与纯白拟态底色搭配干净利落，同时让九个分组一眼可区分。 */
internal val GROUP_ICON_TINT: Map<ModelGroup, Color> = mapOf(
    ModelGroup.BASE to Color(0xFF5A6B85),
    ModelGroup.FOREST to Color(0xFF2E9E6E),
    ModelGroup.FARM to Color(0xFFC98A2E),
    ModelGroup.STALL to Color(0xFF1FA3B8),
    ModelGroup.ORCHARD to Color(0xFF4FA85A),
    ModelGroup.GOLDENBEANS to Color(0xFFD8A32B),
    ModelGroup.SPORTS to Color(0xFF2F7BF0),
    ModelGroup.MEMBER to Color(0xFF7C6BE0),
    ModelGroup.OTHER to Color(0xFF6C7B8C)
)

internal val GROUP_DESC: Map<ModelGroup, String> = mapOf(
    ModelGroup.BASE to "应用与通用设置",
    ModelGroup.FOREST to "能量森林收取设置",
    ModelGroup.FARM to "蚂蚁庄园收取设置",
    ModelGroup.STALL to "新村摆摊相关设置",
    ModelGroup.ORCHARD to "农场果树相关设置",
    ModelGroup.GOLDENBEANS to "金豆任务与兑换设置",
    ModelGroup.SPORTS to "运动与步数设置",
    ModelGroup.MEMBER to "会员权益相关设置",
    ModelGroup.OTHER to "扩展与杂项设置"
)

/**
 * 配置分组入口条目(图2):左侧拟态凸起圆形图标槽 + 组名与描述 + 右侧 › 箭头。
 * 图标槽内为「低饱和主色浅底 + 同色矢量图标」，比彩色 emoji 更整齐、也更贴合拟态风格。
 * 每个分组独立一张纯白拟态卡片（一个功能一张卡片）。
 */
@Composable
fun GroupEntryRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ItemCard(
        modifier = Modifier.clickable(onClick = onClick),
        verticalPadding = 10.dp,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .neuRaised(CircleShape, 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
            Text(
                text = "›",
                fontSize = 22.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}

@Composable
fun ConfigTab(activity: MiuixMainActivity) {
    val context = LocalContext.current
    // selectedUserId 上提到 Activity 级别，切换底部 tab 后 ConfigTab 离开组合也不会重置
    var selectedUserId by activity.selectedUserId
    // 有「所有文件访问」权限才能列出支付宝共享目录中的账号;授权返回后 hasPermission 翻新,列表据此重建
    val canList = activity.hasPermission
    val items = remember(canList) {
        val list = ArrayList<Triple<String?, String, String?>>()
        list.add(Triple(null, "默认", null))
        if (canList) {
            try {
                val dir = FileUtil.CONFIG_DIRECTORY_FILE
                dir.listFiles()?.forEach { configDir ->
                    if (configDir.isDirectory) {
                        val userId = configDir.name
                        UserIdMap.loadSelf(userId)
                        val userEntity = UserIdMap.get(userId)
                        val name = userEntity?.let { it.showName + ": " + it.account } ?: userId
                        list.add(Triple(userId, name, userEntity?.avatar))
                    }
                }
            } catch (e: Exception) {
                Log.printStackTrace(e)
            }
        }
        list
    }
    // 仅当有权限、能读到真实目录时，才对失效的记忆账号做回退(避免无权限空列表误清用户记忆)
    LaunchedEffect(items, canList) {
        if (canList) {
            val cur = activity.selectedUserId.value
            if (cur != null && items.none { it.first == cur }) {
                activity.selectedUserId.value = null
                activity.persistSelectedAccount(null)
            }
        }
    }

    // 账号选择弹窗状态:右上角账号图标点击后弹出,页面主体只保留配置分组
    var showAccountDialog by remember { mutableStateOf(false) }
    // 删除配置确认弹窗状态
    var showDeleteDialog by remember { mutableStateOf(false) }

    val currentItem = items.firstOrNull { it.first == selectedUserId }
    val currentName = currentItem?.second ?: "默认"
    val currentAvatar = currentItem?.third

    // 导出:把当前账号的配置文件写到用户选定位置(与「账号配置管理」页内逻辑一致)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) {
            val file = ConfigPreload.getConfigFile(selectedUserId)
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    file.inputStream().use { it.copyTo(os) }
                }
                ToastUtil.show(context, "导出成功！")
            } catch (e: Exception) {
                ToastUtil.show(context, "导出失败！")
            }
        }
    }
    // 导入:用选定文件覆盖当前账号配置,并通知支付宝侧重新加载
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val file = ConfigPreload.getConfigFile(selectedUserId)
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                if (content.isNullOrBlank()) {
                    ToastUtil.show(context, "导入失败：文件为空")
                    return@rememberLauncherForActivityResult
                }
                // 预校验 JSON 语法：坏文件直接拒绝，避免误报“导入成功”却由 ConfigV2.load 回退成备份旧配置
                try {
                    com.fasterxml.jackson.databind.ObjectMapper().readTree(content)
                } catch (e: Exception) {
                    ToastUtil.show(context, "导入失败：配置文件格式错误")
                    return@rememberLauncherForActivityResult
                }
                file.outputStream().use { it.write(content.toByteArray(Charsets.UTF_8)) }
                // 导入后通知支付宝进程重载；userId 为空表示默认账号，也要发广播（不带 extra 即可命中当前进程）
                try {
                    val intent = Intent("com.eg.android.AlipayGphone.sesame.restart")
                    if (!StringUtil.isEmpty(selectedUserId)) {
                        intent.putExtra("userId", selectedUserId)
                    }
                    context.sendBroadcast(intent)
                } catch (th: Throwable) {
                    Log.printStackTrace(th)
                }
                Model.initAllModel()
                ConfigPreload.reload(selectedUserId)
                ToastUtil.show(context, "导入成功！")
            } catch (e: Exception) {
                ToastUtil.show(context, "导入失败！")
            }
        }
    }

    // 标题行:左侧大标题「配置」,右侧角落依次为导入/导出/账号选择
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "配置",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onBackground
        )
        // 顶部角落操作区:导入 / 导出 / 账号选择
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 导入:Upload 旋转 180°(朝下),与导出(朝上)区分
            IconButton(onClick = { importLauncher.launch("*/*") }) {
                Icon(
                    imageVector = Icons.Filled.Upload,
                    contentDescription = "导入配置",
                    tint = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.rotate(180f)
                )
            }
            IconButton(onClick = { exportLauncher.launch("[" + (selectedUserId ?: "默认") + "]-config_v2.json") }) {
                Icon(
                    imageVector = Icons.Filled.Upload,
                    contentDescription = "导出配置",
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
            // 删除配置:清空当前账号配置(默认账号=全局配置),删除后不可恢复
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除配置",
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
            // 账号头像按钮:默认显示人像图标;已选账号显示头像(无头像则显示账号名首字符)
            IconButton(onClick = { showAccountDialog = true }) {
                Box(
                    Modifier
                        .size(38.dp)
                        .neuPressed(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedUserId == null) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "选择账号",
                            modifier = Modifier.size(22.dp),
                            tint = MiuixTheme.colorScheme.primary
                        )
                    } else if (!currentAvatar.isNullOrBlank()) {
                        MiuixAsyncAvatar(
                            url = currentAvatar,
                            modifier = Modifier.size(34.dp),
                            cornerRadius = 12,
                            circle = true
                        )
                    } else {
                        Text(
                            text = currentName.take(1),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
    if (showAccountDialog) {
        AccountPickerDialog(
            items = items,
            selectedUserId = selectedUserId,
            onSelect = { userId ->
                selectedUserId = userId
                activity.persistSelectedAccount(userId)
            },
            onDismiss = { showAccountDialog = false }
        )
    }
    if (showDeleteDialog) {
        ConfirmDialog(
            title = "警告",
            text = "确认删除「" + currentName + "」的配置？删除后不可恢复。",
            onConfirm = {
                showDeleteDialog = false
                val file = ConfigPreload.getConfigFile(selectedUserId)
                if (FileUtil.deleteFile(file)) {
                    // 清掉内存中的旧配置与预加载标记，防止同进程再进配置页时旧值“复活”被重新写盘
                    ConfigPreload.clear()
                    ToastUtil.show(context, "配置删除成功")
                    // 删除的是非默认账号时回退到默认配置
                    if (selectedUserId != null) {
                        selectedUserId = null
                        activity.persistSelectedAccount(null)
                    }
                } else {
                    ToastUtil.show(context, "配置删除失败")
                }
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    // 顶部搜索框:按分组名/描述过滤配置分组入口
    var searchText by remember { mutableStateOf("") }
    val query = searchText.trim()
    val filteredGroups = if (query.isEmpty()) {
        ModelGroup.values().filter { g -> Model.getGroupModelConfig(g).isNotEmpty() }
    } else {
        ModelGroup.values().filter { g ->
            Model.getGroupModelConfig(g).isNotEmpty() && (
                g.getName().contains(query, ignoreCase = true) ||
                    g.code.contains(query, ignoreCase = true) ||
                    (GROUP_DESC[g] ?: "").contains(query, ignoreCase = true)
                )
        }
    }
    TextField(
        value = searchText,
        onValueChange = { searchText = it },
        label = "搜索配置",
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "搜索",
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 12.dp)
            )
        },
        trailingIcon = {
            if (searchText.isNotEmpty()) {
                Text(
                    "×",
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(end = 12.dp).clickable { searchText = "" }
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
    Spacer(Modifier.height(4.dp))

    if (query.isNotEmpty() && filteredGroups.isEmpty()) {
        // 无匹配结果时给出提示,占满空间避免底部空白
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "未找到相关配置",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    } else {
        CardList {
            filteredGroups.forEach { g ->
                GroupEntryRow(
                    icon = GROUP_ICON[g] ?: Icons.Filled.Widgets,
                    iconTint = GROUP_ICON_TINT[g] ?: MiuixTheme.colorScheme.primary,
                    title = g.getName(),
                    subtitle = GROUP_DESC[g] ?: "",
                    onClick = {
                        val intent = Intent(context, MiuixGroupFieldsActivity::class.java)
                        intent.putExtra(MiuixGroupFieldsActivity.EXTRA_USER_ID, selectedUserId)
                        intent.putExtra(MiuixGroupFieldsActivity.EXTRA_GROUP_CODE, g.name)
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
fun AccountPickerDialog(
    items: List<Triple<String?, String, String?>>,
    selectedUserId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var sel by remember { mutableStateOf(selectedUserId) }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .neuRaised(RoundedCornerShape(24.dp), 10.dp)
                .padding(18.dp)
        ) {
            Column {
                Text(
                    text = "选择账号",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(items, key = { it.first ?: "" }) { (userId, name, avatar) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(
                                    if (sel == userId) MiuixTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!avatar.isNullOrBlank()) {
                                MiuixAsyncAvatar(
                                    url = avatar,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(36.dp),
                                    cornerRadius = 12,
                                    circle = true
                                )
                            }
                            RadioButtonPreference(
                                title = name,
                                selected = sel == userId,
                                titleColor = BasicComponentColors(
                                    color = if (sel == userId) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                    disabledColor = MiuixTheme.colorScheme.disabledOnSurface
                                ),
                                onClick = { sel = userId },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(text = "取消", onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    TextButton(text = "确定", onClick = { onSelect(sel); onDismiss() })
                }
            }
        }
    }
}

@Composable
fun SettingsTab(activity: MiuixMainActivity) {
    val context = LocalContext.current

    Text(
        text = "设置",
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
    )

    SmallTitle(text = "功能设置")
    CardList {
        CardArrowPreference(
            title = "好友统计",
            onClick = { context.startActivity(Intent(context, MiuixFriendStatsActivity::class.java)) }
        )
        CardArrowPreference(
            title = "扩展功能",
            onClick = { context.startActivity(Intent(context, MiuixExtensionsActivity::class.java)) }
        )
    }
    Spacer(Modifier.height(16.dp))

    SmallTitle(text = "系统设置")
    CardList {
        // 文件权限申请引导
        val hasFilePerm = activity.hasPermission
        if (!hasFilePerm) {
            CardArrowPreference(
                title = "申请文件权限",
                summary = "模块需要文件权限才能正常运行",
                onClick = {
                    try {
                        PermissionUtil.checkOrRequestFilePermissions(activity)
                        activity.hasRequestedPermission = true
                    } catch (e: Exception) {
                        ToastUtil.show(context, "申请权限失败")
                    }
                }
            )
        }
        var iconHidden by remember { mutableStateOf(activity.isIconHidden()) }
        BooleanSwitch("隐藏图标", iconHidden) {
            activity.toggleHideIcon()
            iconHidden = activity.isIconHidden()
        }
        // 界面已固定为未来拟态纯白主题（见 MiuixBaseActivity / PureWhiteTheme），
        // 原「深色模式」「跟随系统设置」开关不生效，故不展示以免误导。
        var batteryPerm by remember { mutableStateOf(AppConfig.INSTANCE.batteryPerm ?: true) }
        BooleanSwitch("为支付宝申请后台运行权限", batteryPerm) {
            AppConfig.INSTANCE.batteryPerm = it
            AppConfig.save()
            batteryPerm = it
        }
        if (batteryPerm) {
            val hasPerm = try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                pm?.isIgnoringBatteryOptimizations("com.eg.android.AlipayGphone") == true
            } catch (e: Exception) {
                false
            }
            if (!hasPerm) {
                CardArrowPreference(
                    title = "立即申请权限",
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            ToastUtil.show(context, "申请权限失败")
                        }
                    }
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))

    SmallTitle(text = "关于")
    CardList {
        CardArrowPreference(
            title = "WebDAV 备份",
            summary = "配置与统计数据云备份",
            onClick = { context.startActivity(Intent(context, WebDavSettingsActivity::class.java)) }
        )
        CardArrowPreference(
            title = "关于应用",
            onClick = { context.startActivity(Intent(context, MiuixAboutActivity::class.java)) }
        )
    }
    Spacer(Modifier.height(16.dp))

}

/** 开关项：独立一张纯白拟态卡片（一个功能一张卡片）。 */
@Composable
fun BooleanSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    CardSwitchPreference(
        title = title,
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

/** 旧版「一卡多行」容器，保留仅为兼容历史调用；新代码请使用 [CardList] + [ItemCard]。 */
@Composable
fun CardColumn(content: @Composable ColumnScope.() -> Unit) {
    CardList {
        ItemCard {
            content()
        }
    }
}
