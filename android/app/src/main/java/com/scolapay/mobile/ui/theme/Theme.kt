package com.scolapay.mobile.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Couleurs personnalisées ScolaPay
val Blue700 = Color(0xFF1976D2)
val Blue600 = Color(0xFF1E88E5)
val Blue500 = Color(0xFF2196F3)
val Blue50 = Color(0xFFE3F2FD)
val Green600 = Color(0xFF43A047)
val Green50 = Color(0xFFE8F5E9)
val Red600 = Color(0xFFE53935)
val Red50 = Color(0xFFFFEBEE)
val Orange600 = Color(0xFFFB8C00)
val Orange50 = Color(0xFFFFF3E0)
val Grey50 = Color(0xFFF5F5F5)
val Grey100 = Color(0xFFEEEEEE)
val Grey200 = Color(0xFFE0E0E0)
val Grey600 = Color(0xFF757575)
val Grey900 = Color(0xFF212121)

private val LightColorScheme = lightColorScheme(
    primary = Blue700,
    onPrimary = Color.White,
    primaryContainer = Blue50,
    onPrimaryContainer = Blue700,
    secondary = Green600,
    onSecondary = Color.White,
    secondaryContainer = Green50,
    onSecondaryContainer = Color(0xFF1B5E20),
    tertiary = Orange600,
    onTertiary = Color.White,
    tertiaryContainer = Orange50,
    onTertiaryContainer = Color(0xFFE65100),
    error = Red600,
    onError = Color.White,
    errorContainer = Red50,
    onErrorContainer = Color(0xFFC62828),
    background = Grey50,
    onBackground = Grey900,
    surface = Color.White,
    onSurface = Grey900,
    surfaceVariant = Grey100,
    onSurfaceVariant = Grey600,
    outline = Grey200,
    outlineVariant = Grey100,
)

@Composable
fun ScolaPayTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
