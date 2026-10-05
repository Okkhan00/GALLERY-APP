package com.vaultgallery.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vaultgallery.app.data.AppSettings
import com.vaultgallery.app.data.SettingsRepository
import com.vaultgallery.app.security.AppLockManager
import com.vaultgallery.app.security.PinManager
import com.vaultgallery.app.security.SecureFlag
import com.vaultgallery.app.security.VaultSession
import com.vaultgallery.app.ui.theme.LocalAnimations
import androidx.compose.runtime.CompositionLocalProvider
import android.net.Uri
import com.vaultgallery.app.ui.components.VaultAuthHost
import com.vaultgallery.app.ui.screens.EditorScreen
import com.vaultgallery.app.ui.screens.GalleryScope
import com.vaultgallery.app.ui.screens.GalleryScreen
import com.vaultgallery.app.ui.screens.MainShell
import com.vaultgallery.app.ui.screens.OTHER_ALBUM_ROUTE
import com.vaultgallery.app.ui.screens.StorageScreen
import com.vaultgallery.app.ui.screens.VaultViewerScreen
import com.vaultgallery.app.ui.screens.VideoToolsScreen
import com.vaultgallery.app.ui.screens.LockScreen
import com.vaultgallery.app.ui.screens.PermissionGate
import com.vaultgallery.app.ui.screens.SettingsScreen
import com.vaultgallery.app.ui.screens.TrashScreen
import com.vaultgallery.app.ui.screens.VideoPlayerScreen
import com.vaultgallery.app.ui.screens.ViewerScreen
import com.vaultgallery.app.ui.theme.VaultTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var pin: PinManager
    private var latest: AppSettings? = null
    private var paused = false
    private var vaultVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settingsRepo = SettingsRepository(this)
        pin = PinManager(this)
        lifecycleScope.launch { settingsRepo.flow.collect { latest = it; updateSecureFlag() } }
        // Vault screens always block screenshots and blank the recents thumbnail, whatever the global setting says.
        lifecycleScope.launch { SecureFlag.active.collect { vaultVisible = it > 0; updateSecureFlag() } }
        setContent { Root() }
    }

    override fun onStart() {
        super.onStart()
        val s = latest
        AppLockManager.onStart(s?.appLock == true && pin.isSet(), s?.autoLockSec ?: 0)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) { AppLockManager.onStop(); VaultSession.left() }
    }

    override fun onPause() { super.onPause(); paused = true; updateSecureFlag() }
    override fun onResume() { super.onResume(); paused = false; updateSecureFlag() }

    /** FLAG_SECURE blocks screenshots/recording and blanks the recent-apps thumbnail. */
    private fun updateSecureFlag() {
        val s = latest
        val secure = s?.secureScreens == true || vaultVisible || (paused && s?.appLock == true)
        if (secure) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    @Composable
    private fun Root() {
        val settings by settingsRepo.flow.collectAsStateWithLifecycle(initialValue = null)
        val s = settings
        VaultTheme(s?.theme ?: 0) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (s == null) { Box(Modifier.fillMaxSize()); return@Surface }
                val locked by AppLockManager.locked.collectAsStateWithLifecycle()
                val lockActive = s.appLock && pin.isSet()
                if (lockActive && locked) {
                    LockScreen(pin, s.biometric, onUnlocked = { AppLockManager.unlock() })
                } else {
                    val vm: GalleryViewModel = viewModel()
                    // Subtle motion is skipped when animations are off or when Performance / Battery saver is selected.
                    CompositionLocalProvider(LocalAnimations provides (s.animations && s.perfMode == 0)) {
                        VaultAuthHost(pin, s) {
                            PermissionGate(onGranted = { vm.scan() }) { AppNav(vm, s) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AppNav(vm: GalleryViewModel, settings: AppSettings) {
        val nav = rememberNavController()
        val pinManager = remember { pin }
        NavHost(nav, startDestination = "main") {
            composable("main") {
                // Videos open the dedicated player; photos open the photo viewer. Both index into the same list.
                MainShell(
                    vm, settings,
                    onOpenMedia = { i -> if (vm.photos.value?.getOrNull(i)?.isVideo == true) nav.navigate("video/$i") else nav.navigate("viewer/$i") },
                    onOpenAlbum = { name -> nav.navigate("album/${Uri.encode(name.ifEmpty { OTHER_ALBUM_ROUTE })}") },
                    onOpenVaultItem = { i -> nav.navigate("vaultviewer/$i") },
                    onSettings = { nav.navigate("settings") }, onTrash = { nav.navigate("trash") }, onStorage = { nav.navigate("storage") },
                )
            }
            composable("album/{name}", arguments = listOf(navArgument("name") { type = NavType.StringType })) {
                val raw = it.arguments?.getString("name") ?: ""
                val album = if (raw == OTHER_ALBUM_ROUTE) "" else raw
                GalleryScreen(
                    vm, settings, GalleryScope.Album(album),
                    onOpen = { i -> if (vm.photos.value?.getOrNull(i)?.isVideo == true) nav.navigate("video/$i") else nav.navigate("viewer/$i") },
                    onSettings = { nav.navigate("settings") }, onTrash = { nav.navigate("trash") }, onStorage = { nav.navigate("storage") },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("vaultviewer/{index}", arguments = listOf(navArgument("index") { type = NavType.IntType })) {
                VaultViewerScreen(vm, settings, it.arguments?.getInt("index") ?: 0, onBack = { nav.popBackStack() })
            }
            composable("videotools/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                VideoToolsScreen(vm, it.arguments?.getLong("id") ?: -1L, onBack = { nav.popBackStack() })
            }
            composable("storage") { StorageScreen(vm, onBack = { nav.popBackStack() }) }
            composable("viewer/{index}", arguments = listOf(navArgument("index") { type = NavType.IntType })) {
                ViewerScreen(
                    vm, settings, it.arguments?.getInt("index") ?: 0, onBack = { nav.popBackStack() },
                    onEdit = { id -> nav.navigate("editor/$id") }, onPlayVideo = { i -> nav.navigate("video/$i") },
                )
            }
            composable("video/{index}", arguments = listOf(navArgument("index") { type = NavType.IntType })) {
                VideoPlayerScreen(vm, settings, it.arguments?.getInt("index") ?: 0, onBack = { nav.popBackStack() }, onTools = { id -> nav.navigate("videotools/$id") })
            }
            composable("editor/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                EditorScreen(vm, it.arguments?.getLong("id") ?: -1L, onBack = { nav.popBackStack() })
            }
            composable("settings") { SettingsScreen(vm, settings, pinManager, onBack = { nav.popBackStack() }, onStorage = { nav.navigate("storage") }, onTrash = { nav.navigate("trash") }) }
            composable("trash") { TrashScreen(vm, onBack = { nav.popBackStack() }) }
        }
    }
}
