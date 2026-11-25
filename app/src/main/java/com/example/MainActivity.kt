package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import coil.ImageLoader
import com.example.shortcuts.ShortcutIntents
import com.example.ui.LandscapeLayout
import com.example.ui.LocalIsLandscape
import com.example.ui.PodcastAppContent
import com.example.ui.PodcastViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
  private val pendingShortcutEpisodeId = MutableStateFlow<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    if (savedInstanceState == null) {
      ShortcutIntents.consume(intent)?.let { pendingShortcutEpisodeId.value = it }
    }

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
      }
    }

    try {
      val imageLoader = ImageLoader.Builder(this)
        .okHttpClient {
          OkHttpClient.Builder()
            .addInterceptor { chain ->
              val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 LabCast/1.0")
                .build()
              chain.proceed(request)
            }
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        }
        .crossfade(true)
        .build()
      Coil.setImageLoader(imageLoader)
    } catch (e: Throwable) {
      android.util.Log.e("MainActivity", "Error setting Coil imageLoader: ${e.message}")
    }

    setContent {
      val isLandscape = LandscapeLayout.isLandscape(LocalConfiguration.current)
      val viewModel: PodcastViewModel = viewModel()
      val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
      val shortcutEpisodeId by pendingShortcutEpisodeId.collectAsStateWithLifecycle()
      LaunchedEffect(shortcutEpisodeId) {
        val id = shortcutEpisodeId ?: return@LaunchedEffect
        pendingShortcutEpisodeId.value = null
        viewModel.playEpisodeById(id)
      }
      CompositionLocalProvider(LocalIsLandscape provides isLandscape) {
        MyApplicationTheme(themeMode = themeMode) {
          PodcastAppContent(viewModel = viewModel)
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    ShortcutIntents.consume(intent)?.let { pendingShortcutEpisodeId.value = it }
  }
}

