package com.bell.launcher

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import com.bell.launcher.theme.Appearance
import com.bell.launcher.theme.LauncherTheme
import com.bell.launcher.theme.LauncherThemeData
import com.bell.launcher.theme.ThemeSource
import com.bell.launcher.theme.model.ThemeManifest
import com.bell.launcher.ui.CrashScreen
import com.bell.launcher.ui.LauncherRoot
import com.bell.launcher.ui.LauncherViewModel
import com.bell.launcher.ui.Overlay
import com.bell.launcher.ui.widget.LocalWidgetController
import com.bell.launcher.ui.widget.WidgetController
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels {
        LauncherViewModel.Factory((application as LauncherApp).container)
    }

    /**
     * УВАГА: створювати тільки в onCreate, а не в полі класу.
     * У конструкторі активності базовий контекст ще не підключений (attachBaseContext
     * не викликано), а AppWidgetHost одразу звертається до context.getMainLooper()
     * і падає з NullPointerException — активність не інстанціюється взагалі.
     */
    private lateinit var widgetController: WidgetController

    private var crashMode = false

    private val importThemeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.installTheme(uri) { result ->
                result.fold(
                    onSuccess = { toast("Тему «${it.name}» встановлено") },
                    onFailure = { toast("Помилка: ${it.message}") },
                )
            }
        }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.refreshWeather(force = true)
            } else {
                toast("Без дозволу на геолокацію вкажіть місто вручну")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.step(this, "MainActivity.onCreate")

        widgetController = WidgetController(this)
        CrashLog.step(this, "WidgetController ok")

        // На деяких прошивках це може кинути виняток — лаунчеру важливіше запуститися.
        runCatching {
            enableEdgeToEdge()
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }.onFailure { CrashLog.step(this, "edgeToEdge ПОМИЛКА: ${it.javaClass.simpleName}") }
        CrashLog.step(this, "edgeToEdge ok")

        val app = application as LauncherApp
        val report = CrashLog.report(this)

        if (report != null || !app.isReady) {
            crashMode = true
            CrashLog.step(this, "показую екран діагностики")
            showDiagnosticScreen(
                report ?: "Не вдалося ініціалізувати лаунчер (AppContainer == null).",
            )
            return
        }

        CrashLog.step(this, "ViewModel: створення")
        viewModel.hashCode()
        CrashLog.step(this, "ViewModel: готово, викликаю setContent")

        setContent {
            val state by viewModel.state.collectAsState()
            val theme = state.theme ?: emptyTheme()
            val fontOverride = remember(state.settings.fontChoice) {
                Appearance.fontFamily(state.settings.fontChoice)
            }
            val clockSeparator = Appearance.clockSeparator(state.theme, state.settings)

            LauncherTheme(
                themeData = theme,
                fontOverride = fontOverride,
                clockSeparator = clockSeparator,
            ) {
                CompositionLocalProvider(LocalWidgetController provides widgetController) {
                    LauncherRoot(
                        viewModel = viewModel,
                        onImportTheme = { importThemeLauncher.launch(arrayOf("*/*")) },
                        onRequestLocationPermission = {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        },
                    )
                }
            }

            LaunchedEffect(Unit) {
                CrashLog.markReady(this@MainActivity)
            }
        }
    }

    private fun showDiagnosticScreen(text: String) {
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CrashScreen(
                    text = text,
                    externalPath = CrashLog.externalPath(this),
                    onCopy = {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Bell Launcher", text))
                        toast("Скопійовано")
                    },
                    onContinue = {
                        CrashLog.clear(this)
                        recreate()
                    },
                )
            }
            LaunchedEffect(Unit) { CrashLog.markReady(this@MainActivity) }
        }
    }

    private fun emptyTheme() = LauncherThemeData(
        manifest = ThemeManifest(id = "fallback", name = "Default"),
        source = ThemeSource.Folder(File(filesDir, "__none__")),
    )

    override fun onStart() {
        super.onStart()
        if (!crashMode && ::widgetController.isInitialized) widgetController.startListening()
    }

    override fun onResume() {
        super.onResume()
        if (!crashMode) viewModel.refreshWeather()
    }

    override fun onStop() {
        if (!crashMode && ::widgetController.isInitialized) widgetController.stopListening()
        super.onStop()
    }

    /** Натискання кнопки «Home», коли лаунчер уже активний. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (crashMode) return
        if (viewModel.overlay.value != Overlay.NONE) viewModel.closeOverlay()
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
