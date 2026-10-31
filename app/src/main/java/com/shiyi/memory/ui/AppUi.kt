package com.shiyi.memory.ui

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*

@Composable
fun MemoryApp(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ready by vm.settingsReady.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val dark = settings.theme == "dark" || (settings.theme == "system" && isSystemInDarkTheme())
    val colors =
        if (dark)
            darkColorScheme(
                primary = Color(0xFFE8B5C6),
                secondary = Color(0xFFC7BAE7),
                background = Color(0xFF19181D),
                surface = Color(0xFF252329),
                surfaceVariant = Color(0xFF34313A),
                onBackground = Color(0xFFF3EDF0),
                onSurface = Color(0xFFF3EDF0),
            )
        else
            lightColorScheme(
                primary = Color(0xFF92506A),
                primaryContainer = Color(0xFFF2DDE5),
                secondary = Color(0xFF776394),
                secondaryContainer = Color(0xFFECE5F7),
                background = Color(0xFFFAF8F5),
                surface = Color.White,
                surfaceVariant = Color(0xFFEFEAE7),
                onBackground = Color(0xFF34323A),
                onSurface = Color(0xFF34323A),
                onSurfaceVariant = Color(0xFF6C6873),
                outline = Color(0xFF92909A),
            )
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
    ) {
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            val snack = remember { SnackbarHostState() }
            LaunchedEffect(vm) { vm.messages.collect { snack.showSnackbar(it) } }
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (!ready) CircularProgressIndicator(Modifier.align(Alignment.Center))
                else {
                    Box(
                        if (settings.pinHash.isNotEmpty() && !unlocked)
                            Modifier.clearAndSetSemantics {}
                        else Modifier
                    ) {
                        AppNavigation(vm, snack)
                    }
                    if (settings.pinHash.isNotEmpty() && !unlocked) {
                        val focus = LocalFocusManager.current
                        val keyboard = LocalSoftwareKeyboardController.current
                        LaunchedEffect(Unit) {
                            focus.clearFocus()
                            keyboard?.hide()
                        }
                        androidx.activity.compose.BackHandler {}
                        Dialog(
                            onDismissRequest = {},
                            properties =
                                DialogProperties(
                                    dismissOnBackPress = false,
                                    dismissOnClickOutside = false,
                                    usePlatformDefaultWidth = false,
                                    decorFitsSystemWindows = false,
                                    securePolicy = SecureFlagPolicy.SecureOn,
                                ),
                        ) {
                            Surface(Modifier.fillMaxSize(), color = colors.background) {
                                Box(Modifier.safeDrawingPadding()) { LockScreen(vm) }
                            }
                        }
                    }
                }
                if (busy)
                    Box(
                        Modifier.fillMaxSize()
                            .background(Color.Black.copy(alpha = .25f))
                            .clickable(enabled = true, onClick = {}),
                        contentAlignment = Alignment.Center,
                    ) {
                        Card {
                            Row(
                                Modifier.padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(Modifier.size(26.dp))
                                Spacer(Modifier.width(18.dp))
                                Text("正在处理，请稍候…")
                            }
                        }
                    }
            }
            androidx.activity.compose.BackHandler(enabled = busy) {}
            if (error != null)
                AlertDialog(
                    onDismissRequest = { vm.error.value = null },
                    title = { Text("暂时未能完成") },
                    text = {
                        Text(
                            if (
                                settings.pinHash.isNotEmpty() &&
                                    !unlocked &&
                                    error !in listOf("密码不正确", "尝试次数过多，请稍后再试")
                            )
                                "操作未完成，请解锁后重试。"
                            else error!!
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { vm.error.value = null }) { Text("知道了") }
                    },
                )
        }
    }
}

@Composable
private fun LockScreen(vm: AppViewModel) {
    var pin by remember { mutableStateOf("") }
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().imePadding().padding(32.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Lock,
            null,
            Modifier.size(42.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Text("拾忆", style = MaterialTheme.typography.displaySmall)
        Text(
            "只属于你的私人收藏",
            Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            pin,
            { pin = it.filter(Char::isDigit).take(12) },
            label = { Text("数字密码") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions =
                androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword
                ),
            singleLine = true,
        )
        Button(
            onClick = {
                vm.unlock(pin)
                pin = ""
            },
            Modifier.padding(12.dp),
        ) {
            Text("解锁")
        }
        if (s.biometric)
            OutlinedButton(
                onClick = {
                    val activity =
                        generateSequence(context) {
                                (it as? android.content.ContextWrapper)?.baseContext
                            }
                            .filterIsInstance<FragmentActivity>()
                            .firstOrNull()
                    if (activity != null) {
                        val prompt =
                            BiometricPrompt(
                                activity,
                                ContextCompat.getMainExecutor(context),
                                object : BiometricPrompt.AuthenticationCallback() {
                                    override fun onAuthenticationSucceeded(
                                        result: BiometricPrompt.AuthenticationResult
                                    ) {
                                        vm.unlocked.value = true
                                    }

                                    override fun onAuthenticationError(
                                        errorCode: Int,
                                        errString: CharSequence,
                                    ) {
                                        if (
                                            errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                        )
                                            vm.error.value = errString.toString()
                                    }
                                },
                            )
                        prompt.authenticate(
                            BiometricPrompt.PromptInfo.Builder()
                                .setTitle("解锁拾忆")
                                .setSubtitle("使用设备上的生物识别")
                                .setNegativeButtonText("使用数字密码")
                                .build()
                        )
                    }
                }
            ) {
                Icon(Icons.Outlined.Fingerprint, null)
                Text("生物识别解锁")
            }
        Text(
            "忘记密码无法找回。请妥善保存加密备份。",
            Modifier.padding(top = 24.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AppNavigation(vm: AppViewModel, snack: SnackbarHostState) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var add by remember { mutableStateOf(false) }
    val roots = listOf("home", "memories", "profile", "me")
    val labels = listOf("首页", "回忆", "TA 的档案", "我的")
    val icons =
        listOf(
            Icons.Outlined.Home,
            Icons.Outlined.PhotoLibrary,
            Icons.Outlined.PersonOutline,
            Icons.Outlined.Tune,
        )
    val go: (String) -> Unit = { nav.navigate(it) { launchSingleTop = true } }
    val back: () -> Unit = {
        nav.popBackStack()
        Unit
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (route in roots)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    roots.forEachIndexed { i, r ->
                        NavigationBarItem(
                            selected = route == r,
                            onClick = {
                                nav.navigate(r) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icons[i], labels[i]) },
                            label = { Text(labels[i]) },
                        )
                    }
                }
        },
        floatingActionButton = {
            if (route in roots)
                FloatingActionButton(
                    onClick = { add = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(Icons.Outlined.Add, "添加")
                }
        },
    ) { padding ->
        NavHost(nav, "home", Modifier.padding(padding).imePadding()) {
            composable("home") { HomeScreen(vm, go) }
            composable("memories") { MemoriesScreen(vm, go) }
            composable("profile") { ProfileScreen(vm, go) }
            composable("me") { MeScreen(vm, go) }
            composable("detail/{kind}/{id}") {
                DetailScreen(
                    vm,
                    it.arguments?.getString("kind").orEmpty(),
                    it.arguments?.getString("id").orEmpty(),
                    go,
                    back,
                )
            }
            composable("edit/{kind}/{id}?type={type}") {
                EditorScreen(
                    vm,
                    it.arguments?.getString("kind").orEmpty(),
                    it.arguments?.getString("id").orEmpty(),
                    it.arguments?.getString("type").orEmpty(),
                    back,
                )
            }
            composable("wishes") { WishesScreen(vm, go, back) }
            composable("anniversaries") { AnniversariesScreen(vm, go, back) }
            composable("collections") { CollectionsScreen(vm, go, back) }
            composable("calendar") { CalendarScreen(vm, go, back) }
            composable("search") { SearchScreen(vm, go, back) }
            composable("stats") { StatsScreen(vm, back) }
            composable("backup") { BackupScreen(vm, back) }
            composable("pdf") { PdfScreen(vm, back) }
            composable("settings") { SettingsScreen(vm, back) }
            composable("categories") { CategoriesScreen(vm, back) }
            composable("viewer/{memory}/{index}") {
                ViewerScreen(
                    vm,
                    it.arguments?.getString("memory").orEmpty(),
                    it.arguments?.getString("index")?.toIntOrNull() ?: 0,
                    back,
                )
            }
        }
    }
    if (add)
        AlertDialog(
            onDismissRequest = { add = false },
            title = { Text("留下一点，关于我们") },
            text = {
                Column {
                    listOf(
                            "添加回忆" to "edit/memory/new",
                            "收藏聊天" to "edit/memory/new?type=chat",
                            "记录 TA 的细节" to "edit/entry/new",
                            "添加心愿" to "edit/wish/new",
                            "添加纪念日" to "edit/anniversary/new",
                        )
                        .forEach { (label, path) ->
                            TextButton(
                                onClick = {
                                    add = false
                                    go(path)
                                },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text(label)
                            }
                        }
                }
            },
            confirmButton = { TextButton(onClick = { add = false }) { Text("取消") } },
        )
}
