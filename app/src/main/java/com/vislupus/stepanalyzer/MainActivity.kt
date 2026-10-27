package com.vislupus.stepanalyzer

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.vislupus.stepanalyzer.ui.navigation.AppRoot
import com.vislupus.stepanalyzer.ui.theme.StepAnalyzerTheme

/**
 * Single activity. AppCompatActivity (not ComponentActivity) so that the in-app language
 * switch via AppCompatDelegate.setApplicationLocales works on every Android version.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as StepAnalyzerApp).container
        setContent {
            val settings by container.settings.settings.collectAsState()
            StepAnalyzerTheme(
                themeMode = settings.themeMode,
                accentId = settings.accentId,
                fontScale = settings.fontScale,
            ) {
                AppRoot(container)
            }
        }
    }
}
