package com.clobrano.irlhero.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.clobrano.irlhero.data.ThemeMode

private val Blue = Color(0xFF2F7DE1)
private val BlueDark = Color(0xFF8AB8FF)

private val LightColors = lightColorScheme(primary = Blue, secondary = Color(0xFF4F6A8F), tertiary = Color(0xFFB4651E))
private val DarkColors = darkColorScheme(primary = BlueDark, secondary = Color(0xFFB4C8E6), tertiary = Color(0xFFF0A868))

/** Whether the app is currently drawn dark (from the in-app theme setting, not only the system). */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun IrlHeroTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalDarkTheme provides dark) {
            // Every screen sits on a Surface so text and icons get the right content colour;
            // without it, text outside a Scaffold falls back to black.
            Surface(modifier = Modifier.fillMaxSize(), color = colors.background, content = content)
        }
    }
}
