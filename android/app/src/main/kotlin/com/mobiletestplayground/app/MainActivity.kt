package com.mobiletestplayground.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobiletestplayground.app.navigation.PlaygroundRoot
import com.mobiletestplayground.app.scenarios.notifications.NotificationChannels
import com.mobiletestplayground.core.domain.preferences.ThemeMode
import com.mobiletestplayground.core.ui.theme.PlaygroundTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity. It owns no UI logic: it forwards deep-link intents to [AppViewModel] and
 * hosts the Compose tree.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Also registered by the Application; repeated here because instrumented tests replace the
        // Application with HiltTestApplication. Registering an existing channel is a no-op.
        NotificationChannels.register(this)
        // A configuration change recreates the activity with the same intent; only a fresh launch
        // should act on its deep link.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val preferences by appViewModel.preferences.collectAsStateWithLifecycle()
            val dark = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            PlaygroundTheme(darkTheme = dark) {
                PlaygroundRoot(appViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val link = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString ?: return
        appViewModel.onDeepLink(link)
    }
}
