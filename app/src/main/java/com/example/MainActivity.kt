package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import coil.ImageLoader
import com.example.ui.PodcastAppContent
import com.example.ui.PodcastViewModel
import com.example.ui.theme.MyApplicationTheme
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

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
      val viewModel: PodcastViewModel = viewModel()
      val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
      MyApplicationTheme(themeMode = themeMode) {
        PodcastAppContent(viewModel = viewModel)
      }
    }
  }
}

