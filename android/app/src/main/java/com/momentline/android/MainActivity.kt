package com.momentline.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.momentline.android.data.readUtf8
import com.momentline.android.ui.MomentApp
import com.momentline.android.ui.MomentViewModel
import com.momentline.android.ui.theme.MomentTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MomentViewModel by viewModels()
    private var sharedUri by mutableStateOf<Uri?>(null)
    private var sharedUriConsumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedUriConsumed = savedInstanceState?.getBoolean(SHARED_URI_CONSUMED) == true
        sharedUri = if (sharedUriConsumed) null else intent.sharedUri()
        setContent {
            MomentTheme(viewModel.uiState.appearance) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MomentApp(
                        viewModel = viewModel,
                        sharedUri = sharedUri,
                        onShareConsumed = ::consumeSharedUri,
                        onReadShared = { uri -> contentResolver.readUtf8(uri) }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedUriConsumed = false
        sharedUri = intent.sharedUri()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SHARED_URI_CONSUMED, sharedUriConsumed)
    }

    private fun consumeSharedUri() {
        sharedUriConsumed = true
        sharedUri = null
        intent.action = null
        intent.removeExtra(Intent.EXTRA_STREAM)
    }

    private fun Intent.sharedUri(): Uri? {
        if (action != Intent.ACTION_SEND) return null
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    companion object {
        private const val SHARED_URI_CONSUMED = "shared_uri_consumed"
    }
}