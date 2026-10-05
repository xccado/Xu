package com.xse.sesame.ui.miuix

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xse.sesame.data.ConfigPreload
import com.xse.sesame.data.ConfigV2
import com.xse.sesame.data.Model
import com.xse.sesame.data.ModelField
import com.xse.sesame.data.ModelFields
import com.xse.sesame.data.modelFieldExt.SelectAndCountModelField
import com.xse.sesame.data.modelFieldExt.SelectAndCountOneModelField
import com.xse.sesame.data.modelFieldExt.IntegerModelField
import com.xse.sesame.data.modelFieldExt.SelectModelField
import com.xse.sesame.data.modelFieldExt.SelectOneModelField
import com.xse.sesame.entity.AlipayUser
import com.xse.sesame.entity.IdAndName
import com.xse.sesame.entity.KVNode
import com.xse.sesame.entity.MemberBenefit
import com.xse.sesame.util.Log
import com.xse.sesame.util.ToastUtil
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * 选填编辑页（四级）：编辑 SELECT/SELECT_ONE/SELECT_AND_COUNT/SELECT_AND_COUNT_ONE 类型字段。
 * 从 MiuixGroupFieldsActivity 跳转进来，通过 Intent 传递 userId、groupCode、fieldCode、modelCode。
 */
class MiuixSelectionEditActivity : MiuixBaseActivity() {

    companion object {
        const val EXTRA_USER_ID = "userId"
        const val EXTRA_GROUP_CODE = "groupCode"
        const val EXTRA_FIELD_CODE = "fieldCode"
        const val EXTRA_MODEL_CODE = "modelCode"

        /** 兑换请求：UI 进程 → 支付宝进程（由 AlipayBroadcastReceiver 处理） */
        const val ACTION_MEMBER_EXCHANGE = "com.eg.android.AlipayGphone.sesame.memberExchange"

        /** 兑换结果回传：支付宝进程 → UI 进程 */
        const val ACTION_MEMBER_EXCHANGE_RESULT = "com.xse.sesame.memberExchangeResult"
    }

    internal var userId: String? = null
    private var groupCode: String? = null
    private var fieldCode: String? = null
    private var modelCode: String? = null

    /**
     * 待回传的兑换请求：requestId → 挂起中的协程。
     * UI 进程没有支付宝宿主环境（classLoader/rpcBridge 均为 null），不能直接调 RPC，
     * 兑换统一发广播给支付宝进程执行，结果经 ACTION_MEMBER_EXCHANGE_RESULT 回传后 resume。
     */
    internal val pendingExchanges = java.util.concurrent.ConcurrentHashMap<String, CancellableContinuation<String>>()

    private val exchangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_MEMBER_EXCHANGE_RESULT) {
                val requestId = intent.getStringExtra("requestId")
                val result = intent.getStringExtra("result")
                Log.i("SelectionEdit", "memberExchange result: requestId=$requestId result=$result")
                if (requestId != null) {
                    val cont = pendingExchanges.remove(requestId)
                    if (cont != null && cont.isActive) {
                        cont.resume(result ?: "兑换失败")
                    }
                }
            }
        }
    }

    /**
     * 由 Compose 内容注入的“保存未提交更改”回调。
     * 顶部返回按钮与系统返回键（手势/物理键）都会先调用它，再退出，
     * 避免 dirty 变更因直接 finish 而丢失。
     */
    internal var saveHandler: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 注册兑换结果回传接收器：支付宝进程与模块 App 是不同 UID，Android 13+ 必须导出
        val exchangeFilter = IntentFilter(ACTION_MEMBER_EXCHANGE_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(exchangeReceiver, exchangeFilter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(exchangeReceiver, exchangeFilter)
        }
        userId = intent.getStringExtra(EXTRA_USER_ID)
        groupCode = intent.getStringExtra(EXTRA_GROUP_CODE)
        fieldCode = intent.getStringExtra(EXTRA_FIELD_CODE)
        modelCode = intent.getStringExtra(EXTRA_MODEL_CODE)
        // 与字段页一致：本页可能被系统重建/直接拉起，需自行注册模型并预加载账号配置，
        // 否则 ConfigV2.INSTANCE 中取不到字段 → 页面显示「字段不存在」。
        Model.initAllModel()
        ConfigPreload.prepare(userId)
        setAppContent {
            val modelCodeVal = modelCode
            val fieldCodeVal = fieldCode
            if (fieldCodeVal != null && modelCodeVal != null) {
                @Suppress("UNCHECKED_CAST")
                val field = (ConfigV2.INSTANCE.getModelFields(modelCodeVal) as? ModelFields)?.get(fieldCodeVal) as? ModelField<*>
                if (field != null) {
                    SelectionEditContent(
                        activity = this,
                        field = field,
                        modelCode = modelCodeVal,
                        userId = userId
                    )
                } else {
                    top.yukonga.miuix.kmp.basic.Text("字段不存在: $fieldCodeVal")
                }
            } else {
                top.yukonga.miuix.kmp.basic.Text("缺少参数")
            }
        }
    }

    override fun onBackPressed() {
        saveHandler?.invoke()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(exchangeReceiver)
        } catch (_: Exception) {
        }
        // 页面销毁时若有未回传的兑换请求，统一以失败结束，避免协程悬挂
        pendingExchanges.values.forEach { cont ->
            if (cont.isActive) cont.resume("页面已关闭")
        }
        pendingExchanges.clear()
    }

    /** 顶部返回按钮与系统返回统一入口：先保存再退出。 */
    fun saveAndFinish() {
        saveHandler?.invoke()
        finish()
    }
}

@Composable
fun SelectionEditContent(
    activity: MiuixSelectionEditActivity,
    field: ModelField<*>,
    modelCode: String,
    userId: String?
) {
    // 始终以 ConfigV2.INSTANCE 中的实时字段读取当前值，
    // 避免传入引用与单例不一致时读不到已保存的勾选。
    val liveField = ConfigV2.INSTANCE.getModelFields(modelCode)?.get(field.code) ?: field
    val single = liveField.type == "SELECT_ONE" || liveField.type == "SELECT_AND_COUNT_ONE"
    val withCount = liveField.type == "SELECT_AND_COUNT"

    @Suppress("UNCHECKED_CAST")
    val smf = when {
        liveField.type == "SELECT" -> liveField as? SelectModelField
        liveField.type == "SELECT_ONE" -> liveField as? SelectOneModelField
        liveField.type == "SELECT_AND_COUNT" -> liveField as? SelectAndCountModelField
        liveField.type == "SELECT_AND_COUNT_ONE" -> liveField as? SelectAndCountOneModelField
        else -> null
    }

    // 选项列表与初始勾选只在进入页面时解析一次（remember 键为不变量）：
    // getExpandValue() 走 AlipayUser::getList 时是「遍历全部好友 + 逐个 new 对象」的 O(n) 分配，
    // 若每次重组都执行，还会让下游 filteredOptions 的 remember(options, ...) 缓存永久失效。
    val initialState = remember(modelCode, field.code) {
        @Suppress("UNCHECKED_CAST")
        val options: List<IdAndName> = (smf?.expandValue ?: emptyList<Any>()) as List<IdAndName>
        val v = liveField.value
        val ids: Set<String> = when {
            liveField.type == "SELECT" -> (v as? Set<*>)?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()
            liveField.type == "SELECT_ONE" -> {
                val sv = v as? String
                if (sv != null && sv.isNotEmpty()) setOf(sv) else emptySet()
            }
            liveField.type == "SELECT_AND_COUNT_ONE" -> {
                // value 是 KVNode<String, Integer>，取 key
                val key = (v as? KVNode<*, *>)?.key?.toString()
                if (key != null && key.isNotEmpty()) setOf(key) else emptySet()
            }
            else -> {
                // SELECT_AND_COUNT：value 是 Map<String, Integer>，取 key 集合
                (v as? Map<*, *>)?.keys?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()
            }
        }
        val initialCounts: Map<String, Int> = when {
            withCount -> (v as? Map<*, *>)
                ?.mapValues { (_, value) -> (value as? Int) ?: 1 }
                ?.mapKeys { (k, _) -> k as? String ?: "" }
                ?.filterKeys { it in ids } ?: emptyMap()
            liveField.type == "SELECT_AND_COUNT_ONE" -> {
                val kv = v as? KVNode<*, *>
                val key = kv?.key?.toString()
                val count = (kv?.value as? Int) ?: 1
                if (key != null && key.isNotEmpty()) mapOf(key to count) else emptyMap()
            }
            else -> emptyMap()
        }
        Triple(options, ids, initialCounts)
    }
    val options = initialState.first
    val selectedIds = initialState.second
    val initialCounts = initialState.third

    // 诊断日志只写一次：放在 Composable 主体会导致每次重组都做 O(n) 的 value.toString() 并入队写盘。
    LaunchedEffect(Unit) {
        Log.i("SelectionEdit", "Entry: field=${liveField.code}, type=${liveField.type}, value=${liveField.value}, selectedIds=$selectedIds")
    }

    var sel by remember { mutableStateOf(selectedIds) }
    var counts by remember {
        mutableStateOf(selectedIds.associateWith { initialCounts[it] ?: 1 })
    }
    var searchQuery by remember { mutableStateOf("") }
    var dirty by remember { mutableStateOf(false) }

    val filteredOptions = remember(options, searchQuery) {
        if (searchQuery.isBlank()) options
        else options.filter { it.name.contains(searchQuery, ignoreCase = true) || it.id.contains(searchQuery) }
    }

    // 选中项自动置顶
    val sortedOptions = remember(filteredOptions, sel) {
        filteredOptions.sortedByDescending { it.id in sel }
    }

    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

    // 会员权益列表（memberPointExchangeBenefitList）：展示图片与价格，支持直接兑换
    val isBenefitList = liveField.code == "memberPointExchangeBenefitList"
    val scope = rememberCoroutineScope()

    fun applyAndSave() {
        // 始终以 ConfigV2.INSTANCE 中的字段为准，避免传入引用与单例不一致导致写入丢失。
        val configField = ConfigV2.INSTANCE.getModelFields(modelCode)?.get(field.code) ?: field
        when (configField.type) {
            "SELECT" -> configField.setObjectValue(sel)
            "SELECT_ONE" -> configField.setObjectValue(sel.firstOrNull())
            "SELECT_AND_COUNT" -> {
                val csmf = configField as? SelectAndCountModelField
                csmf?.clear()
                sel.forEach { id -> csmf?.add(id, counts[id] ?: 1) }
            }
            "SELECT_AND_COUNT_ONE" -> {
                val csmf = configField as? SelectAndCountOneModelField
                csmf?.clear()
                csmf?.add(sel.firstOrNull() ?: "", counts[sel.firstOrNull()] ?: 1)
            }
        }
        // userId 为 null 表示「默认」账号，ConfigV2.save 会落到默认配置文件，不能直接跳过
        val saved = ConfigV2.save(userId, true)
        Log.i("SelectionEdit", "applyAndSave: field=${field.code}, saved=$saved, value=${configField.value}")
        if (saved) {
            dirty = false
            ToastUtil.show(activity, "已保存")
            // 通知支付宝主进程重启模块，让新配置立即生效（与设置页同款）。
            // userId 为 null 表示「默认」账号，也要发广播（不带 extra 即可命中当前进程）。
            try {
                val intent = Intent("com.eg.android.AlipayGphone.sesame.restart")
                if (activity.userId != null) {
                    intent.putExtra("userId", activity.userId)
                }
                activity.sendBroadcast(intent)
            } catch (th: Throwable) {
                Log.printStackTrace(th)
            }
        } else {
            ToastUtil.show(activity, "保存失败")
        }
    }

    // 退出前统一先保存：顶部返回按钮与系统返回键（手势/物理键）共用同一逻辑。
    androidx.compose.runtime.SideEffect {
        activity.saveHandler = { if (dirty) applyAndSave() }
    }

    Scaffold(
        topBar = {
            LogTopBar(
                title = field.name ?: "",
                onBack = {
                    if (!dirty) {
                        ToastUtil.show(activity, "没有未保存的更改")
                    }
                    activity.saveAndFinish()
                }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (!single) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = "",
                        modifier = Modifier.weight(1f),
                        leadingIcon = {
                            top.yukonga.miuix.kmp.basic.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.Search,
                                contentDescription = "搜索",
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                top.yukonga.miuix.kmp.basic.Text(
                                    "×",
                                    fontSize = 16.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(end = 12.dp).clickable { searchQuery = "" }
                                )
                            }
                        }
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                    .border(1.dp, MiuixTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(count = sortedOptions.size, key = { idx -> sortedOptions[idx].id }) { idx ->
                        val opt = sortedOptions[idx]
                        val isChecked = sel.contains(opt.id)
                        if (isBenefitList) {
                            BenefitListItem(
                                opt = opt as? MemberBenefit,
                                isChecked = isChecked,
                                single = single,
                                onToggle = {
                                    if (single) {
                                        sel = setOf(opt.id)
                                    } else if (isChecked) {
                                        sel = sel - opt.id
                                    } else {
                                        sel = sel + opt.id
                                    }
                                    dirty = true
                                },
                                onExchange = {
                                    scope.launch {
                                        val msg = exchangeBenefit(activity, opt.name)
                                        ToastUtil.show(activity, msg)
                                    }
                                }
                            )
                        } else {
                            val optAvatar = (opt as? AlipayUser)?.avatar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .background(
                                        if (isChecked) MiuixTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!optAvatar.isNullOrBlank()) {
                                    MiuixAsyncAvatar(
                                        url = optAvatar,
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(40.dp),
                                        cornerRadius = 12,
                                        circle = true
                                    )
                                }
                                if (single) {
                                    RadioButtonPreference(
                                        title = opt.name,
                                        selected = isChecked,
                                        modifier = Modifier.weight(1f),
                                        titleColor = BasicComponentColors(
                                            color = if (isChecked) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                            disabledColor = MiuixTheme.colorScheme.disabledOnSurface
                                        ),
                                        radioButtonLocation = RadioButtonLocation.End,
                                        onClick = {
                                            sel = setOf(opt.id)
                                            dirty = true
                                        }
                                    )
                                } else {
                                    CheckboxPreference(
                                        title = opt.name,
                                        checked = isChecked,
                                        modifier = Modifier.weight(1f),
                                        titleColor = BasicComponentColors(
                                            color = if (isChecked) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                            disabledColor = MiuixTheme.colorScheme.disabledOnSurface
                                        ),
                                        checkboxLocation = CheckboxLocation.End,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                sel = sel + opt.id
                                                if (!counts.containsKey(opt.id)) {
                                                    counts = counts + (opt.id to (initialCounts[opt.id] ?: 1))
                                                }
                                            } else {
                                                sel = sel - opt.id
                                            }
                                            dirty = true
                                        }
                                    )
                                }
                            }
                        }
                        if (withCount && isChecked) {
                            key(opt.id) {
                                var sliderValue by remember(opt.id) { mutableFloatStateOf((counts[opt.id] ?: 1).toFloat()) }
                                SliderPreference(
                                    title = "数量",
                                    value = sliderValue,
                                    valueRange = run {
                                        val f = liveField as? SelectAndCountModelField
                                        (f?.valueRangeMin ?: 0f)..(f?.valueRangeMax ?: 100f)
                                    },
                                    valueText = sliderValue.roundToInt().toString(),
                                    onValueChange = { sliderValue = it },
                                    onValueChangeFinished = {
                                        counts = counts + (opt.id to sliderValue.roundToInt())
                                        dirty = true
                                    }
                                )
                            }
                        }
                    }
                    if (sortedOptions.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 权益列表行：图片 + 名称/价格 + 勾选 + 兑换按钮 */
@Composable
private fun BenefitListItem(
    opt: MemberBenefit?,
    isChecked: Boolean,
    single: Boolean,
    onToggle: () -> Unit,
    onExchange: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图片
        BenefitAsyncImage(
            url = opt?.pic,
            modifier = Modifier
                .size(56.dp)
                .background(MiuixTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.width(12.dp))
        // 名称 + 价格
        Column(Modifier.weight(1f)) {
            Text(
                text = opt?.name ?: "",
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onSurface
            )
            val point = opt?.point ?: ""
            val yuan = opt?.yuan ?: ""
            val priceText = when {
                point.isNotEmpty() && yuan.isNotEmpty() -> "${point}积分 + ${yuan}元"
                point.isNotEmpty() -> "${point}积分"
                yuan.isNotEmpty() -> "${yuan}元"
                else -> ""
            }
            if (priceText.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = priceText,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // 兑换按钮
        TextButton(
            text = "兑换",
            onClick = onExchange
        )
        Spacer(Modifier.width(4.dp))
        // 勾选（保留批量勾选能力）：自绘勾选框
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = if (isChecked) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(6.dp)
                )
                .clickable { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            if (isChecked) {
                Text(
                    text = "✓",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

/** 轻量网络图片加载（OkHttp + Bitmap 缓存，避免新增依赖） */
@Composable
private fun BenefitAsyncImage(url: String?, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) {
            try {
                val finalUrl = if (url.startsWith("//")) "https:$url" else url
                val req = Request.Builder()
                    .url(finalUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 12; M2007J3SC Build/SKQ1.211006.001) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/89.0.4389.72 Mobile Safari/537.36 AlipayClient/12.12.12.8000")
                    .header("Referer", "https://render.alipay.com/")
                    .build()
                OkHttpClient().newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    } else {
                        null
                    }
                }
            } catch (t: Throwable) {
                null
            }
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(modifier)
    }
}

/**
 * 兑换单个权益：UI 进程无支付宝宿主环境，不能直接调 RPC。
 * 改为发广播 ACTION_MEMBER_EXCHANGE 给支付宝进程执行，挂起等待
 * ACTION_MEMBER_EXCHANGE_RESULT 回传后返回用户可见的结果文案。
 */
private suspend fun exchangeBenefit(activity: MiuixSelectionEditActivity, name: String): String {
    return withContext(Dispatchers.Main) {
        val requestId = java.util.UUID.randomUUID().toString()
        try {
            withTimeout(30_000) {
                suspendCancellableCoroutine { cont ->
                    activity.pendingExchanges[requestId] = cont
                    cont.invokeOnCancellation { activity.pendingExchanges.remove(requestId) }
                    val intent = Intent(MiuixSelectionEditActivity.ACTION_MEMBER_EXCHANGE)
                    intent.putExtra("name", name)
                    intent.putExtra("requestId", requestId)
                    activity.sendBroadcast(intent)
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            activity.pendingExchanges.remove(requestId)
            "兑换超时：请确认支付宝已运行且模块已注入"
        }
    }
}
