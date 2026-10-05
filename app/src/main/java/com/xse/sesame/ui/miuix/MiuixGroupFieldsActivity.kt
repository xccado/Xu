package com.xse.sesame.ui.miuix

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xse.sesame.data.AppConfig
import com.xse.sesame.data.ConfigPreload
import com.xse.sesame.data.ConfigV2
import com.xse.sesame.data.Model
import com.xse.sesame.data.ModelConfig
import com.xse.sesame.data.ModelField
import com.xse.sesame.data.ModelGroup
import com.xse.sesame.data.modelFieldExt.ChoiceModelField
import com.xse.sesame.data.modelFieldExt.EmptyModelField
import com.xse.sesame.data.modelFieldExt.IntegerModelField
import com.xse.sesame.data.modelFieldExt.SelectAndCountModelField
import com.xse.sesame.data.modelFieldExt.SelectAndCountOneModelField
import com.xse.sesame.data.modelFieldExt.SelectModelField
import com.xse.sesame.data.modelFieldExt.SelectOneModelField
import com.xse.sesame.util.Log
import com.xse.sesame.util.ToastUtil
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * 配置字段页（三级）：显示某个分组下的所有配置字段。
 * 从 MiuixSettingsActivity 跳转进来，通过 Intent 传递 userId 和 groupCode。
 */
class MiuixGroupFieldsActivity : MiuixBaseActivity() {

    companion object {
        const val EXTRA_USER_ID = "userId"
        const val EXTRA_GROUP_CODE = "groupCode"
    }

    private var userId: String? = null
    internal var groupCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = intent.getStringExtra(EXTRA_USER_ID)
        groupCode = intent.getStringExtra(EXTRA_GROUP_CODE)
        // 本页会被底部配置 Tab 的分组列表直接拉起，必须自行完成「注册模型 + 预加载账号配置」，
        // 否则 Model.getGroupModelConfig(group) 返回空表 → 只有标题、正文全空白；
        // 且 ConfigV2 未按 userId 加载时字段值取的是上一个账号，退出保存会把错值写进本账号配置。
        // 这两步原先由已删除的旧齿轮入口页 MiuixSettingsActivity.onCreate 承担。
        Model.initAllModel()
        ConfigPreload.prepare(userId)
        setAppContent {
            groupCode?.let { code ->
                val group = ModelGroup.entries.find { it.name == code }
                if (group != null) {
                    GroupFieldsContent(activity = this, userId = userId, groupCode = code, group = group)
                } else {
                    top.yukonga.miuix.kmp.basic.Text("分组不存在: $code", color = MiuixTheme.colorScheme.error)
                }
            } ?: run {
                top.yukonga.miuix.kmp.basic.Text("缺少参数", color = MiuixTheme.colorScheme.error)
            }
        }
    }

    override fun onBackPressed() {
        save()
        super.onBackPressed()
    }

    /** 顶部返回按钮与系统返回统一入口：先保存再退出。 */
    fun saveAndFinish() {
        save()
        finish()
    }

    /**
     * 统一落盘入口：本页字段变更只写内存，只有真正退出时才调用这里写一次磁盘。
     * 是否提示/写盘只看「本进程字段级改动」（hasFieldChanges 基于页面打开时的快照），
     * 不能依赖 isModify()：它比较的是整份序列化文本，磁盘里任何格式差异都会判成"有改动"，
     * 导致没动任何开关返回也弹"保存成功"并给支付宝发重启广播（用户反馈的"返回即保存成功直接运行"）。
     */
    fun save() {
        if (!ConfigV2.hasFieldChanges()) return
        if (ConfigV2.save(userId, true)) {
            ToastUtil.show(this, "保存成功！")
            sendRestartIfNeeded()
        }
    }

    /** 保存成功后通知支付宝主进程重启模块，让新配置立即生效（与设置页同款）。 */
    private fun sendRestartIfNeeded() {
        // userId 为 null 表示「默认」账号，也要发广播（不带 extra 即可命中当前进程），
        // 否则默认账号下改配置保存后支付宝进程不重载，仍然不能即时生效。
        try {
            val intent = Intent("com.eg.android.AlipayGphone.sesame.restart")
            if (userId != null) {
                intent.putExtra("userId", userId)
            }
            sendBroadcast(intent)
        } catch (th: Throwable) {
            Log.printStackTrace(th)
        }
    }
}

/**
 * 扁平化后的列表行：把「模型标题」和「字段」都提升为 LazyColumn 的独立 item，
 * 让虚拟化真正下沉到字段级。
 *
 * 原先每个 ModelConfig 是一个 item、内部用 fields.forEach 组合全部字段，
 * 导致 Forest 组（77 个字段）一旦进入视口就要一次性组合、measure、layout 所有字段。
 */
private sealed interface GroupFieldsRow {
    val key: String

    data class Header(override val key: String, val title: String) : GroupFieldsRow

    data class Field(
        override val key: String,
        val modelCode: String,
        val field: ModelField<*>,
        val first: Boolean,
        val last: Boolean
    ) : GroupFieldsRow

    /** 走 AppConfig 全局配置（非 ConfigV2 字段）的手写开关行 */
    data class AppConfigSwitch(
        override val key: String,
        val title: String
    ) : GroupFieldsRow
}

@Composable
fun GroupFieldsContent(activity: MiuixGroupFieldsActivity, userId: String?, groupCode: String, group: ModelGroup) {
    // 父字段开关/选项变化后，依赖其显示的子字段需重新计算可见性，
    // 用 depVersion 作为 remember 键触发扁平行列表重建。
    var depVersion by remember { mutableStateOf(0) }
    // 字段对象由 ConfigV2 单例持有，引用稳定；仅当分组或依赖版本变化时才重建。
    val rows = remember(group, depVersion) {
        val list = ArrayList<GroupFieldsRow>()
        Model.getGroupModelConfig(group).values.forEach { mc ->
            val fields = mc.fields.values.toList()
            if (fields.isEmpty()) return@forEach
            list.add(GroupFieldsRow.Header(key = "header:${mc.getCode()}", title = mc.name ?: ""))
            // 过滤：依赖父字段但父未激活的子字段
            val visibleFields = fields.filter { f ->
                f.isVisible(mc)
            }
            visibleFields.forEachIndexed { index, field ->
                list.add(
                    GroupFieldsRow.Field(
                        key = "field:${mc.getCode()}:${field.code}",
                        modelCode = mc.getCode(),
                        field = field,
                        first = index == 0,
                        last = index == visibleFields.lastIndex
                    )
                )
                // 「开启状态栏禁删」「屏蔽部分弹窗」走 AppConfig 全局配置（非 ConfigV2 字段），
                // 历史位置在配置页基础分组、开启抓包(debugMode)下方，故在字段行后插入手写开关行。
                if (groupCode == "BASE" && field.code == "debugMode") {
                    list.add(GroupFieldsRow.AppConfigSwitch(key = "appcfg:enableOnGoing", title = "开启状态栏禁删"))
                    list.add(GroupFieldsRow.AppConfigSwitch(key = "appcfg:closeCaptchaDialog", title = "屏蔽部分弹窗"))
                    list.add(GroupFieldsRow.AppConfigSwitch(key = "appcfg:showToast", title = "气泡提示"))
                    list.add(GroupFieldsRow.AppConfigSwitch(key = "appcfg:toastOffsetY", title = "气泡纵向偏移"))
                }
            }
        }
        list
    }

    Scaffold(
        topBar = {
            LogTopBar(
                title = group.getName(),
                onBack = { activity.saveAndFinish() },
                onExecute = {
                    try {
                        val intent = Intent("com.eg.android.AlipayGphone.sesame.execute")
                        intent.putExtra("group", group.getCode())
                        activity.sendBroadcast(intent)
                        ToastUtil.show(activity, "已发送执行请求：" + group.getName())
                    } catch (th: Throwable) {
                        Log.printStackTrace(th)
                        ToastUtil.show(activity, "执行失败: " + th.message)
                    }
                }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is GroupFieldsRow.Header -> SmallTitle(text = row.title)
                    is GroupFieldsRow.Field -> GroupFieldRow(
                        activity = activity,
                        userId = userId,
                        groupCode = groupCode,
                        row = row,
                        onDependencyChanged = { depVersion++ }
                    )
                    is GroupFieldsRow.AppConfigSwitch -> AppConfigSwitchRow(activity = activity, row = row)
                }
            }
        }
    }
}

/**
 * 单个字段行：每个字段独立一张纯白拟态卡片（一个功能一张卡片）。
 * 展开的编辑区与字段行同卡展示，LazyColumn 仍可逐字段复用/回收。
 */
@Composable
private fun GroupFieldRow(
    activity: MiuixGroupFieldsActivity,
    userId: String?,
    groupCode: String,
    row: GroupFieldsRow.Field,
    onDependencyChanged: () -> Unit
) {
    val field = row.field
    val isSelect = field.type in listOf("SELECT", "SELECT_ONE", "SELECT_AND_COUNT", "SELECT_AND_COUNT_ONE")
    ItemCard(verticalPadding = if (isSelect) 8.dp else 5.dp) {
        if (isSelect) {
            ArrowPreference(
                title = field.name ?: "",
                onClick = {
                    activity.startActivity(
                        Intent(activity, MiuixSelectionEditActivity::class.java).apply {
                            putExtra(MiuixGroupFieldsActivity.EXTRA_USER_ID, userId)
                            putExtra(MiuixGroupFieldsActivity.EXTRA_GROUP_CODE, groupCode)
                            putExtra(MiuixSelectionEditActivity.EXTRA_FIELD_CODE, field.code)
                            putExtra(MiuixSelectionEditActivity.EXTRA_MODEL_CODE, row.modelCode)
                        }
                    )
                }
            )
        } else {
            // 只写内存，落盘统一在 saveAndFinish() / onBackPressed() 完成
            FieldItem(field = field, onFieldChanged = onDependencyChanged)
        }
    }
}

/**
 * AppConfig 全局配置开关行（非 ConfigV2 字段）：
 * 「开启状态栏禁删」「屏蔽部分弹窗」「气泡提示」「气泡纵向偏移」逻辑读 AppConfig.INSTANCE
 * （NotificationUtil / CaptchaHook / ToastUtil），因此开关直接读写 AppConfig 并即时落盘 + 广播重载，
 * 不参与本页退出的统一 ConfigV2 保存。
 */
@Composable
private fun AppConfigSwitchRow(activity: MiuixGroupFieldsActivity, row: GroupFieldsRow.AppConfigSwitch) {
    ItemCard(verticalPadding = 5.dp) {
        when (row.key) {
            "appcfg:enableOnGoing" -> {
                var checked by remember { mutableStateOf(AppConfig.INSTANCE.enableOnGoing ?: false) }
                SwitchPreference(
                    title = row.title,
                    summary = null,
                    checked = checked,
                    onCheckedChange = {
                        checked = it
                        AppConfig.INSTANCE.enableOnGoing = it
                        AppConfig.save()
                        activity.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.reloadConfig"))
                    }
                )
            }
            "appcfg:closeCaptchaDialog" -> {
                var checked by remember { mutableStateOf(AppConfig.INSTANCE.closeCaptchaDialog ?: true) }
                SwitchPreference(
                    title = row.title,
                    summary = null,
                    checked = checked,
                    onCheckedChange = {
                        checked = it
                        AppConfig.INSTANCE.closeCaptchaDialog = it
                        AppConfig.save()
                        activity.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.reloadConfig"))
                    }
                )
            }
            "appcfg:showToast" -> {
                var checked by remember { mutableStateOf(AppConfig.INSTANCE.showToast ?: true) }
                SwitchPreference(
                    title = row.title,
                    summary = null,
                    checked = checked,
                    onCheckedChange = {
                        checked = it
                        AppConfig.INSTANCE.showToast = it
                        AppConfig.save()
                        activity.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.reloadConfig"))
                    }
                )
            }
            "appcfg:toastOffsetY" -> {
                var text by remember { mutableStateOf((AppConfig.INSTANCE.toastOffsetY ?: 0).toString()) }
                var expanded by remember { mutableStateOf(false) }
                Column {
                    ArrowPreference(
                        title = row.title,
                        summary = if (text.isEmpty()) "0 px（正数向下）" else "$text px（正数向下）",
                        onClick = { expanded = !expanded }
                    )
                    if (expanded) {
                        TextField(
                            value = text,
                            onValueChange = { newText ->
                                val filtered = newText.filterIndexed { index, c -> c.isDigit() || (c == '-' && index == 0) }
                                text = filtered
                                filtered.toIntOrNull()?.let { value ->
                                    AppConfig.INSTANCE.toastOffsetY = value
                                    AppConfig.save()
                                    activity.sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.reloadConfig"))
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            label = ""
                        )
                    }
                }
            }
        }
    }
}


