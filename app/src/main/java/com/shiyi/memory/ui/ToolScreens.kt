package com.shiyi.memory.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiyi.memory.data.*
import com.shiyi.memory.domain.*
import com.shiyi.memory.platform.*
import java.time.*

@Composable
fun PasswordField(
    label: String,
    value: String,
    change: (String) -> Unit,
    numeric: Boolean = false,
) {
    OutlinedTextField(
        value,
        change,
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.NumberPassword else KeyboardType.Password
            ),
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
fun BackupScreen(vm: AppViewModel, back: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val prepared by vm.prepared.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("导出备份") }
    var overwrite by remember { mutableStateOf(false) }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            vm.endExternal()
            if (uri != null) {
                val chars = password.toCharArray()
                password = ""
                confirmation = ""
                vm.action("加密备份已保存") { vm.backup.export(uri, chars) }
            }
        }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            vm.endExternal()
            if (uri != null) {
                vm.inspectBackup(uri, password)
                password = ""
            }
        }
    Column(Modifier.fillMaxSize()) {
        Header("备份与恢复", back = back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("包含全部记录、回收站与原图。备份已加密，密码无法找回。", style = MaterialTheme.typography.bodyMedium)
            Section("最近备份")
            Text(
                if (s.lastBackup == 0L) "尚未创建备份"
                else
                    "${Instant.ofEpochMilli(s.lastBackup).atZone(ZoneId.systemDefault()).toLocalDateTime().withNano(0)}\n${"%.2f".format(s.backupSize / 1048576.0)} MB\n${s.backupLocation}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(18.dp))
            Chips(listOf("导出备份", "恢复备份"), mode) {
                mode = it
                password = ""
                confirmation = ""
            }
            PasswordField(
                if (mode == "导出备份") "备份密码（至少 8 位）" else "原备份密码",
                password,
                { password = it },
            )
            if (mode == "导出备份") {
                PasswordField("确认备份密码", confirmation, { confirmation = it })
                Button(
                    onClick = {
                        if (password.length < 8) vm.error.value = "备份密码至少 8 位"
                        else if (password != confirmation) vm.error.value = "两次密码不一致"
                        else {
                            vm.beginExternal()
                            export.launch("拾忆-${today()}.shiyi")
                        }
                    },
                    Modifier.fillMaxWidth(),
                ) {
                    Text("导出加密备份")
                }
            } else
                Button(
                    onClick = {
                        if (password.isBlank()) vm.error.value = "请输入备份密码"
                        else {
                            vm.beginExternal()
                            import.launch(
                                arrayOf("application/octet-stream", "application/zip", "*/*")
                            )
                        }
                    },
                    Modifier.fillMaxWidth(),
                ) {
                    Text("选择备份")
                }
            Text(
                "恢复将覆盖当前内容，请先备份。应用锁、通知及本机备份记录保持当前设备设置。",
                Modifier.padding(top = 18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (prepared != null) {
                val b = prepared!!.manifest.data
                Section("校验通过，尚未恢复")
                Text(
                    "${b.memories.size} 段回忆 · ${b.media.size} 张回忆图片\n${b.entries.size} 条档案 · ${b.wishes.size} 项心愿\n${b.anniversaries.size} 个纪念日 · ${b.collections.size} 个合集\n共 ${b.images().size} 个原始图片文件"
                )
                Button(
                    onClick = { overwrite = true },
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text("恢复此备份")
                }
                OutlinedButton(
                    onClick = {
                        prepared?.folder?.deleteRecursively()
                        vm.prepared.value = null
                    }
                ) {
                    Text("取消恢复并清除临时文件")
                }
            }
        }
    }
    if (overwrite)
        AlertDialog(
            onDismissRequest = { overwrite = false },
            title = { Text("覆盖当前全部数据？") },
            text = { Text("将以备份中的内容替换当前回忆、档案、心愿、纪念日、合集、标签及关联关系。确认已另外备份当前数据。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        overwrite = false
                        val b = prepared ?: return@TextButton
                        vm.action("恢复成功") {
                            try {
                                vm.backup.restore(b)
                            } finally {
                                vm.prepared.value = null
                            }
                            if (vm.prefs.read().notifications) Reminders.schedule(vm.app)
                        }
                    }
                ) {
                    Text("确认覆盖并恢复")
                }
            },
            dismissButton = { TextButton(onClick = { overwrite = false }) { Text("取消") } },
        )
}

@Composable
fun PdfScreen(vm: AppViewModel, back: () -> Unit) {
    val s by vm.data.collectAsStateWithLifecycle()
    var title by rememberSaveable { mutableStateOf("我们的回忆册") }
    var scope by rememberSaveable { mutableStateOf("全部回忆") }
    var year by rememberSaveable { mutableStateOf(today().year.toString()) }
    var month by rememberSaveable { mutableStateOf(today().monthValue.toString()) }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var collection by rememberSaveable { mutableStateOf("") }
    var cover by rememberSaveable { mutableStateOf("") }
    var images by rememberSaveable { mutableStateOf(true) }
    var text by rememberSaveable { mutableStateOf(true) }
    var dates by rememberSaveable { mutableStateOf(true) }
    fun selected() =
        s.activeMemories.filter { m ->
            when (scope) {
                "指定年份" -> m.date.take(4) == year
                "指定月份" -> m.date.take(7) == "$year-${month.padStart(2, '0')}"
                "日期范围" -> m.date >= from && m.date <= to
                "指定合集" ->
                    s.collectionRefs.any { it.collectionId == collection && it.memoryId == m.id }
                else -> true
            }
        }
    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/pdf")
        ) { uri ->
            vm.endExternal()
            if (uri != null) {
                val records = selected()
                val options = PdfOptions(title, cover, images, text, dates)
                vm.action("PDF 已保存") {
                    PdfExporter(vm.app, vm.repo).export(uri, records, s, options)
                }
            }
        }
    Column(Modifier.fillMaxSize()) {
        Header("PDF 回忆册", back = back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
            Field("回忆册标题", title, { title = it })
            Choices("选择范围", scope, listOf("全部回忆", "指定年份", "指定月份", "日期范围", "指定合集")) { scope = it }
            if (scope in listOf("指定年份", "指定月份")) Field("年份", year, { year = it })
            if (scope == "指定月份") Choices("月份", month, (1..12).map(Int::toString)) { month = it }
            if (scope == "日期范围") {
                DateField("开始日期", from, { from = it })
                DateField("结束日期", to, { to = it })
            }
            if (scope == "指定合集")
                Choices(
                    "合集",
                    s.collections.find { it.id == collection }?.title.orEmpty(),
                    s.collections.map { it.title + " · " + it.id.take(8) },
                ) { name ->
                    collection =
                        s.collections
                            .find { it.title + " · " + it.id.take(8) == name }
                            ?.id
                            .orEmpty()
                }
            Choices(
                "封面图",
                if (cover.isBlank()) "无" else "已选择",
                listOf("无") + s.media.mapIndexed { i, _ -> "图片 ${i + 1}" },
            ) { choice ->
                cover =
                    s.media
                        .getOrNull(choice.removePrefix("图片 ").toIntOrNull()?.minus(1) ?: -1)
                        ?.path
                        .orEmpty()
            }
            if (cover.isNotBlank()) Photo(cover, vm, Modifier.fillMaxWidth().height(200.dp))
            Toggle("包含图片", images) { images = it }
            Toggle("包含文字描述", text) { text = it }
            Toggle("包含日期与地点", dates) { dates = it }
            Text(
                "${selected().size} 段回忆",
                Modifier.padding(vertical = 18.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = {
                    when {
                        title.isBlank() -> vm.error.value = "请填写回忆册标题"
                        scope in listOf("指定年份", "指定月份") && year.toIntOrNull() !in 1..9999 ->
                            vm.error.value = "请输入有效年份"
                        scope == "日期范围" &&
                            (runCatching {
                                    LocalDate.parse(from)
                                    LocalDate.parse(to)
                                }
                                .isFailure || from > to) -> vm.error.value = "请选择有效日期范围"
                        selected().isEmpty() -> vm.error.value = "所选范围没有回忆"
                        else -> {
                            vm.beginExternal()
                            launcher.launch("拾忆回忆册-${today()}.pdf")
                        }
                    }
                },
                Modifier.fillMaxWidth(),
            ) {
                Text("生成并保存 PDF")
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, back: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pinDialog by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    var disable by remember { mutableStateOf(false) }
    var erase by remember { mutableStateOf(false) }
    var eraseText by remember { mutableStateOf("") }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted)
                vm.action("提醒已开启") {
                    vm.prefs.update { it.copy(notifications = true) }
                    Reminders.schedule(vm.app)
                }
            else vm.error.value = "通知权限未授予。可稍后在系统设置中开启。"
        }
    Column(Modifier.fillMaxSize()) {
        Header("设置与隐私", back = back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
            Section("外观")
            Choices(
                "主题",
                when (s.theme) {
                    "light" -> "浅色"
                    "dark" -> "深色"
                    else -> "跟随系统"
                },
                listOf("跟随系统", "浅色", "深色"),
            ) { choice ->
                vm.action("主题已更新") {
                    vm.prefs.update {
                        it.copy(
                            theme =
                                when (choice) {
                                    "浅色" -> "light"
                                    "深色" -> "dark"
                                    else -> "system"
                                }
                        )
                    }
                }
            }
            Section("应用锁")
            Text(
                "${if (s.pinHash.isBlank()) "未设置" else "已开启"} · 后台锁定",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = {
                    pinDialog = true
                    disable = false
                    current = ""
                    pin = ""
                    confirm = ""
                },
                Modifier.fillMaxWidth(),
            ) {
                Text(if (s.pinHash.isBlank()) "设置数字密码" else "修改数字密码")
            }
            if (s.pinHash.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        pinDialog = true
                        disable = true
                        current = ""
                    },
                    Modifier.fillMaxWidth(),
                ) {
                    Text("关闭应用锁")
                }
                Toggle("生物识别解锁", s.biometric) { enabled ->
                    if (
                        enabled &&
                            BiometricManager.from(context)
                                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) !=
                                BiometricManager.BIOMETRIC_SUCCESS
                    )
                        vm.error.value = "设备未配置可用的生物识别，请先在系统设置中录入。"
                    else vm.action { vm.prefs.update { it.copy(biometric = enabled) } }
                }
                OutlinedButton(onClick = { vm.unlocked.value = false }) { Text("立即锁定") }
            }
            Section("本地提醒")
            Toggle("开启纪念日通知", s.notifications) { enabled ->
                if (!enabled)
                    vm.action("提醒已关闭") {
                        vm.prefs.update { it.copy(notifications = false) }
                        Reminders.cancel(vm.app)
                    }
                else if (
                    Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                )
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else
                    vm.action("提醒已开启") {
                        vm.prefs.update { it.copy(notifications = true) }
                        Reminders.schedule(vm.app)
                    }
            }
            Text(
                "每天约 9:00 检查当天到期的提醒；无需精确闹钟权限。系统重启或日期、时区变化后重新调度。省电与后台限制可能延迟通知。内容只显示通用提示。",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(
                                android.provider.Settings.EXTRA_APP_PACKAGE,
                                context.packageName,
                            )
                    )
                }
            ) {
                Text("打开系统通知设置")
            }
            Section("数据管理")
            OutlinedButton(
                onClick = {
                    erase = true
                    eraseText = ""
                },
                Modifier.fillMaxWidth(),
            ) {
                Text("彻底删除全部本地数据", color = MaterialTheme.colorScheme.error)
            }
            Section("隐私说明")
            Text(
                "拾忆不申请 INTERNET 权限，不访问服务器；没有账号、云同步、广告、分析 SDK、AI 或 OCR。导入图片复制到私有目录，原相册内容不会被修改或删除。Android 自动云备份和设备迁移已关闭。\n\n加密备份使用 PBKDF2-HMAC-SHA256（310,000 次）与 AES-256-GCM，独立随机盐和 IV。请使用强密码并定期备份。设备私有沙盒不能防止已获取系统权限的攻击。卸载应用将删除本地数据，卸载前请导出备份。",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(30.dp))
        }
    }
    if (pinDialog)
        AlertDialog(
            onDismissRequest = {
                pinDialog = false
                pin = ""
                confirm = ""
                current = ""
            },
            title = { Text(if (disable) "关闭应用锁" else "设置数字密码") },
            text = {
                Column {
                    if (s.pinHash.isNotBlank())
                        PasswordField(
                            "当前密码",
                            current,
                            { current = it.filter(Char::isDigit).take(12) },
                            true,
                        )
                    if (!disable) {
                        PasswordField(
                            "新密码（6–12 位数字）",
                            pin,
                            { pin = it.filter(Char::isDigit).take(12) },
                            true,
                        )
                        PasswordField(
                            "确认新密码",
                            confirm,
                            { confirm = it.filter(Char::isDigit).take(12) },
                            true,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val old = current
                        val new = pin
                        val repeated = confirm
                        vm.action(
                            "应用锁已更新",
                            {
                                pinDialog = false
                                pin = ""
                                confirm = ""
                                current = ""
                            },
                        ) {
                            val actual = vm.prefs.read()
                            if (actual.pinHash.isNotBlank()) {
                                require(System.currentTimeMillis() >= actual.retryAfter) {
                                    "尝试次数过多，请稍后再试"
                                }
                                val chars = old.toCharArray()
                                val valid =
                                    try {
                                        Crypto.verifyPin(chars, actual.pinSalt, actual.pinHash)
                                    } finally {
                                        chars.fill('\u0000')
                                    }
                                if (!valid) {
                                    vm.prefs.update {
                                        val n = it.failedAttempts + 1
                                        it.copy(
                                            failedAttempts = n,
                                            retryAfter =
                                                if (n >= 5) System.currentTimeMillis() + 30_000
                                                else 0,
                                        )
                                    }
                                    error("当前密码不正确")
                                }
                            }
                            if (disable)
                                vm.prefs.update {
                                    it.copy(
                                        pinSalt = "",
                                        pinHash = "",
                                        biometric = false,
                                        failedAttempts = 0,
                                        retryAfter = 0,
                                    )
                                }
                            else {
                                require(new == repeated) { "两次密码不一致" }
                                require(new.matches(Regex("[0-9]{6,12}"))) { "密码需为 6–12 位数字" }
                                val salt = Crypto.salt()
                                val chars = new.toCharArray()
                                val hash =
                                    try {
                                        Crypto.hashPin(chars, salt)
                                    } finally {
                                        chars.fill('\u0000')
                                    }
                                vm.prefs.update {
                                    it.copy(
                                        pinSalt = salt,
                                        pinHash = hash,
                                        failedAttempts = 0,
                                        retryAfter = 0,
                                    )
                                }
                                vm.unlocked.value = true
                            }
                        }
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pinDialog = false
                        pin = ""
                        confirm = ""
                        current = ""
                    }
                ) {
                    Text("取消")
                }
            },
        )
    if (erase)
        AlertDialog(
            onDismissRequest = { erase = false },
            title = { Text("彻底删除全部数据？") },
            text = {
                Column {
                    Text("将删除当前设备上的全部回忆、原图、档案、设置和临时导出文件，无法撤销。已导出到其他位置的备份不会删除。输入「删除全部」确认。")
                    Field("确认文字", eraseText, { eraseText = it })
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        erase = false
                        vm.action(
                            "本地数据已删除",
                            onSuccess = {
                                context.startActivity(
                                    Intent(context, com.shiyi.memory.MainActivity::class.java)
                                        .addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        )
                                )
                            },
                        ) {
                            vm.repo.eraseAll(vm.prefs)
                            Reminders.cancel(vm.app)
                            vm.prepared.value = null
                        }
                    },
                    enabled = eraseText == "删除全部",
                ) {
                    Text("永久删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { erase = false }) { Text("取消") } },
        )
}

@Composable
fun CategoriesScreen(vm: AppViewModel, back: () -> Unit) {
    val s by vm.data.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var scope by rememberSaveable { mutableStateOf("回忆") }
    var editing by rememberSaveable { mutableStateOf("") }
    var delete by remember { mutableStateOf<Category?>(null) }
    LazyColumn(contentPadding = PaddingValues(20.dp)) {
        item {
            Header("分类管理", "删除分类不会删除使用该名称的记录", back)
            Choices("用途", scope, listOf("回忆", "档案")) { scope = it }
            Field("自定义分类名称", name, { name = it })
            Button(
                onClick = {
                    vm.action(
                        onSuccess = {
                            name = ""
                            editing = ""
                        }
                    ) {
                        require(name.isNotBlank()) { "请输入分类名称" }
                        require(
                            s.categories.none {
                                it.scope == scope && it.name == name.trim() && it.id != editing
                            }
                        ) {
                            "分类已存在"
                        }
                        vm.repo.updateCategory(
                            Category(editing.ifBlank { newId() }, scope, name.trim())
                        )
                    }
                }
            ) {
                Text(if (editing.isBlank()) "创建分类" else "保存分类")
            }
            if (editing.isNotBlank())
                TextButton(
                    onClick = {
                        editing = ""
                        name = ""
                    }
                ) {
                    Text("取消编辑")
                }
            Section("自定义分类")
        }
        items(s.categories, key = { it.id }) { c ->
            Row {
                Box(Modifier.weight(1f)) {
                    RecordCard(
                        c.name,
                        c.scope,
                        {
                            editing = c.id
                            name = c.name
                            scope = c.scope
                        },
                    )
                }
                IconButton(onClick = { delete = c }) { Icon(Icons.Outlined.DeleteOutline, "删除分类") }
            }
        }
    }
    if (delete != null)
        ConfirmDelete("删除此自定义分类？", "现有记录保留原分类文字；只移除分类选项。", { delete = null }) {
            val id = delete!!.id
            delete = null
            vm.action("分类已删除") { vm.repo.delete("category", id) }
        }
}
