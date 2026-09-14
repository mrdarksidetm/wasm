package com.mrdartsidetm.wasm.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF80D4FF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF003548),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF004D67),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFBFE9FF),
    secondary = androidx.compose.ui.graphics.Color(0xFFB5C9D7),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF20333E),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF364955),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFD1E5F3),
    background = androidx.compose.ui.graphics.Color(0xFF191C1E),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE1E2E5),
    surface = androidx.compose.ui.graphics.Color(0xFF191C1E),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE1E2E5),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF40484C),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFC0C8CD)
)

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF006687),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFBFE9FF),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF001F2C),
    secondary = androidx.compose.ui.graphics.Color(0xFF4E616D),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFD1E5F3),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF0A1D28),
    background = androidx.compose.ui.graphics.Color(0xFFFBFCFF),
    onBackground = androidx.compose.ui.graphics.Color(0xFF191C1E),
    surface = androidx.compose.ui.graphics.Color(0xFFFBFCFF),
    onSurface = androidx.compose.ui.graphics.Color(0xFF191C1E),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFDCE4E9),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF40484C)
)

@Composable
fun WasmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    val appFontFamily = remember(context) { getAppFontFamily(context) }
    val typography = remember(appFontFamily) { buildWasmTypography(appFontFamily) }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
