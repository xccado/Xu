package com.xse.sesame.ui.miuix

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xse.sesame.data.ModelField
import com.xse.sesame.data.modelFieldExt.ChoiceModelField
import com.xse.sesame.data.modelFieldExt.EmptyModelField
import com.xse.sesame.data.modelFieldExt.IntegerModelField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 配置字段编辑项（原地展开编辑）。
 *
 * 值为「只写内存」：任何变更只调用 setObjectValue() 落在 ConfigV2 单例上，不触发磁盘写入。
 * 统一落盘由所在页面的退出流程负责（MiuixGroupFieldsActivity.saveAndFinish() / onBackPressed()），
 * 避免每次拨开关、每次提交输入都做一次「全量序列化 + 写盘 + 备份检查」。
 */
@Composable
fun FieldItem(field: ModelField<*>, onFieldChanged: (() -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FieldItemBody(field, onFieldChanged)
        // 字段说明统一在这里渲染；BOOLEAN 的说明由 SwitchPreference(summary) 承载，不重复
        val description = field.description
        if (field.type != "BOOLEAN" && !description.isNullOrBlank()) {
            Text(
                text = description,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
            )
        }
    }
}

@Composable
private fun FieldItemBody(field: ModelField<*>, onFieldChanged: (() -> Unit)? = null) {
    // 用字段名唯一标识展开状态，避免 LazyColumn 复用导致错位
    val fieldKey = "${field.type}:${field.name}"
    var expanded by remember { mutableStateOf(false) }
    var expandedFieldKey by remember { mutableStateOf<String?>(null) }
    when {
        field.type == "BOOLEAN" -> {
            var checked by remember { mutableStateOf(field.value as? Boolean ?: false) }
            SwitchPreference(
                title = field.name ?: "",
                summary = field.description,
                checked = checked,
                onCheckedChange = {
                    checked = it
                    field.setObjectValue(it)
                    onFieldChanged?.invoke()
                }
            )
        }

        field.type in listOf("INTEGER", "MULTIPLY_INTEGER") -> {
            val context = LocalContext.current
            val imf = field as? IntegerModelField
            val maxLimit = imf?.maxLimit
            val lowerLimit = imf?.minLimit
            val current = (field as? IntegerModelField.MultiplyIntegerModelField)?.getConfigValue()?.toInt() ?: (field.value as? Int ?: 0)
            val limitHint = when {
                lowerLimit == null && maxLimit == null -> ""
                lowerLimit != null && lowerLimit < 0 -> "（-1 表示按最大额度）"
                lowerLimit != null && maxLimit != null -> "（${lowerLimit}~${maxLimit}）"
                maxLimit != null -> "（上限 ${maxLimit}）"
                else -> "（下限 ${lowerLimit}）"
            }
            if (expandedFieldKey != fieldKey) {
                expanded = false
                expandedFieldKey = null
            }
            ArrowPreference(
                title = field.name ?: "",
                summary = if (limitHint.isEmpty()) current.toString() else "$current$limitHint",
                onClick = {
                    expanded = !expanded
                    expandedFieldKey = fieldKey
                }
            )
            if (expanded) {
                var text by remember { mutableStateOf(current.toString()) }
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).padding(horizontal = 16.dp)) {
                    TextField(
                        value = text,
                        onValueChange = { input ->
                            val filtered = input.filterIndexed { index, c -> c.isDigit() || (c == '-' && index == 0) }
                            text = filtered
                            val parsed = filtered.toIntOrNull()
                            val belowMin = lowerLimit != null && (parsed == null || parsed < lowerLimit)
                            val aboveMax = maxLimit != null && (parsed == null || parsed > maxLimit)
                            if (!belowMin && !aboveMax) {
                                field.setConfigValue(parsed.toString())
                                onFieldChanged?.invoke()
                            }
                        },
                        label = "",
                    )
                }
            }
        }

        field.type in listOf("STRING", "TEXT") -> {
            if (expandedFieldKey != fieldKey) {
                expanded = false
                expandedFieldKey = null
            }
            ArrowPreference(
                title = field.name ?: "",
                summary = field.configValue,
                onClick = {
                    expanded = !expanded
                    expandedFieldKey = fieldKey
                }
            )
            if (expanded) {
                var text by remember { mutableStateOf(field.configValue ?: "") }
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).padding(horizontal = 16.dp)) {
                    TextField(
                        value = text,
                        onValueChange = {
                            text = it
                            field.setObjectValue(it)
                            onFieldChanged?.invoke()
                        },
                        label = "",
                    )
                }
            }
        }

        field.type == "READ_TEXT" || field.type == "URL_TEXT" -> {
            ArrowPreference(title = field.name ?: "", summary = field.configValue)
        }

        field.type in listOf("SELECT", "SELECT_ONE", "SELECT_AND_COUNT", "SELECT_AND_COUNT_ONE") -> {} // 由 GroupFieldsPage 处理
        field.type == "EMPTY" -> {
            val emf = field as? EmptyModelField
            ArrowPreference(title = field.name ?: "", onClick = { emf?.clickRunner?.run() })
        }

        field.type == "LIST" -> {
            val list = (field.value as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            if (expandedFieldKey != fieldKey) {
                expanded = false
                expandedFieldKey = null
            }
            ArrowPreference(
                title = field.name ?: "",
                summary = list.joinToString(","),
                onClick = {
                    expanded = !expanded
                    expandedFieldKey = fieldKey
                }
            )
            if (expanded) {
                var text by remember { mutableStateOf(list.joinToString("\n")) }
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).padding(horizontal = 16.dp)) {
                    TextField(
                        value = text,
                        onValueChange = { input ->
                            text = input
                            field.setObjectValue(input.lines().map { it.trim() }.filter { it.isNotEmpty() })
                            onFieldChanged?.invoke()
                        },
                        label = "",
                        singleLine = false,
                        maxLines = 8,
                    )
                }
            }
        }

        field.type == "CHOICE" -> {
            val cmf = field as? ChoiceModelField
            val choiceArray = cmf?.expandKey ?: emptyArray()
            val current = field.value as? Int ?: 0
            if (expandedFieldKey != fieldKey) {
                expanded = false
                expandedFieldKey = null
            }
            ArrowPreference(
                title = field.name ?: "",
                summary = choiceArray.getOrNull(current),
                onClick = {
                    expanded = !expanded
                    expandedFieldKey = fieldKey
                }
            )
            if (expanded) {
                var sel by remember { mutableStateOf(current) }
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    choiceArray.forEachIndexed { index, opt ->
                        // 点选即生效（不再需要保存按钮）
                        RadioButtonPreference(
                            title = opt,
                            selected = sel == index,
                            onClick = {
                                sel = index
                                field.setObjectValue(index)
                                onFieldChanged?.invoke()
                            }
                        )
                    }
                }
            }
        }

        else -> {
            Text(text = field.name ?: "", color = MiuixTheme.colorScheme.onBackground)
        }
    }
}
