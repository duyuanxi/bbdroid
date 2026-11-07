package com.bbdroid.app

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppSnackbar { val host = SnackbarHostState() }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by SettingsStore.settings(this).collectAsState(initial = AppSettings())
            BbdroidTheme(settings.themeMode, GlassConfig(settings.glassEnabled, settings.glassIntensity)) { MainScreen(this) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(context: Context) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT < 29) permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
    LaunchedEffect(ReDownloadRequest.id) { if (ReDownloadRequest.id > 0) selectedTab = 0 }
    var handledToolDestination by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(ToolboxDestination.id) {
        if (ToolboxDestination.id > handledToolDestination) { focus.clearFocus(); selectedTab = 1; handledToolDestination = ToolboxDestination.id }
    }
    GlassSceneHost(Modifier.fillMaxSize()) {
        Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("BBDROID", style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
                        Text(listOf("下载", "工具箱", "历史")[selectedTab], style = MaterialTheme.typography.headlineMedium)
                    }
                    FilledTonalIconButton(onClick = { focus.clearFocus(); showSettings = true }, modifier = Modifier.size(48.dp), shape = RoundedCornerShape(17.dp)) {
                        Icon(Icons.Outlined.Tune, "设置", Modifier.size(23.dp))
                    }
                }
            },
            snackbarHost = { SnackbarHost(AppSnackbar.host, Modifier.padding(horizontal = 18.dp)) },
            bottomBar = {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 22.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    FloatingNavigation(selectedTab) { focus.clearFocus(); selectedTab = it }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                fun tabModifier(index: Int): Modifier = Modifier.padding(padding)
                    .then(if (selectedTab == index) Modifier.widthIn(max = 840.dp).fillMaxSize() else Modifier.size(0.dp)).clipToBounds()
                // Keep all tabs composed so imports, download jobs and conversion state survive navigation.
                DownloadTab(context, tabModifier(0))
                ToolboxTab(context, tabModifier(1))
                HistoryTab(context, tabModifier(2)) { selectedTab = 0 }
            }
        }
        if (showSettings) {
            ModalBottomSheet(onDismissRequest = { showSettings = false }, shape = DialogShape,
                containerColor = MaterialTheme.colorScheme.surface, dragHandle = { BottomSheetDefaults.DragHandle() }) {
                SettingsContent(context) { showSettings = false }
            }
        }
    }
}

@Composable
private fun FloatingNavigation(selected: Int, onSelect: (Int) -> Unit) {
    val icons = listOf(Icons.Outlined.Download, Icons.Outlined.SwapHoriz, Icons.Outlined.History)
    val names = listOf("下载", "工具箱", "历史")
    AppCard(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(7.dp)) {
            val slot = (maxWidth - 8.dp) / 3
            val offset by animateDpAsState((slot + 4.dp) * selected, spring(dampingRatio = 0.83f, stiffness = 450f), label = "navPosition")
            Box(Modifier.matchParentSize().padding(end = maxWidth - slot).offset(x = offset).clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                names.forEachIndexed { index, text ->
                    val tint by animateColorAsState(if (selected == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "navTint")
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(24.dp))
                        .selectable(selected == index, role = Role.Tab, onClick = { onSelect(index) }).padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(icons[index], null, Modifier.size(23.dp), tint)
                        Text(text, fontSize = 11.sp, color = tint)
                    }
                }
            }
        }
    }
}
