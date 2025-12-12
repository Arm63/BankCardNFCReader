package com.example.bankcardreader.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Cyan400,
    onPrimary = Navy900,
    primaryContainer = Navy700,
    onPrimaryContainer = Gray100,
    secondary = Green500,
    onSecondary = Navy900,
    secondaryContainer = Navy700,
    onSecondaryContainer = Gray100,
    tertiary = Amber500,
    onTertiary = Navy900,
    tertiaryContainer = Navy700,
    onTertiaryContainer = Gray100,
    error = Red500,
    onError = Color.White,
    errorContainer = Red400,
    onErrorContainer = Navy900,
    background = Navy900,
    onBackground = Gray100,
    surface = Navy800,
    onSurface = Gray100,
    surfaceVariant = Navy700,
    onSurfaceVariant = Gray200,
    outline = Navy600,
    outlineVariant = Navy700
)

private val LightColorScheme = lightColorScheme(
    primary = Cyan400,
    onPrimary = Navy900,
    primaryContainer = Cyan300,
    onPrimaryContainer = Navy900,
    secondary = Green500,
    onSecondary = Color.White,
    secondaryContainer = Green400,
    onSecondaryContainer = Navy900,
    tertiary = Amber500,
    onTertiary = Navy900,
    tertiaryContainer = Amber400,
    onTertiaryContainer = Navy900,
    error = Red500,
    onError = Color.White,
    errorContainer = Red400,
    onErrorContainer = Navy900,
    background = Gray100,
    onBackground = Navy900,
    surface = Color.White,
    onSurface = Navy900,
    surfaceVariant = Gray200,
    onSurfaceVariant = Navy700,
    outline = Navy600,
    outlineVariant = Gray200
)

@Composable
fun BankCardReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Navy900.toArgb()
            window.navigationBarColor = Navy900.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
