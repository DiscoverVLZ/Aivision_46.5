package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = CyanNeon,
    secondary = SlateSurface,
    tertiary = CyanNeonDim,
    background = SlateBackground,
    surface = DarkCardBg,
    onPrimary = SlateBackground,
    onSecondary = LightAccents,
    onBackground = LightAccents,
    onSurface = LightAccents,
    error = WarningRed
  )

private val LightColorScheme = DarkColorScheme // Forced Dark theme for high-end industrial contrast

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Force dark styling
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme // Strictly enforce dark slate theme for the HUD feel

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
