package com.alinam.smartconnect.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Purple = Color(0xFF6C63FF)
val PurpleLight = Color(0xFF9C94FF)
val PurpleDark = Color(0xFF3D35CC)
val Cyan = Color(0xFF00E5FF)
val GlassWhite = Color(0x1AFFFFFF)
val GlassBorder = Color(0x33FFFFFF)
val Dark100 = Color(0xFF000000)
val Dark90 = Color(0xFF0A0A0F)
val Dark80 = Color(0xFF12121A)
val Dark70 = Color(0xFF1A1A28)
val Dark60 = Color(0xFF22223A)
val SuccessGreen = Color(0xFF00E676)
val WarningOrange = Color(0xFFFF6D00)
val ErrorRed = Color(0xFFFF1744)

private val DarkColors = darkColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = PurpleDark,
    onPrimaryContainer = PurpleLight,
    secondary = Cyan,
    onSecondary = Dark100,
    background = Dark90,
    onBackground = Color.White,
    surface = Dark80,
    onSurface = Color.White,
    surfaceVariant = Dark70,
    onSurfaceVariant = Color(0xFFBBBBCC),
    error = ErrorRed,
    onError = Color.White
)

private val LightColors = lightColorScheme(
    primary = PurpleDark,
    onPrimary = Color.White,
    primaryContainer = PurpleLight,
    onPrimaryContainer = PurpleDark,
    secondary = Color(0xFF0097A7),
    onSecondary = Color.White,
    background = Color(0xFFF5F5FF),
    onBackground = Color(0xFF111122),
    surface = Color.White,
    onSurface = Color(0xFF111122),
    surfaceVariant = Color(0xFFEEEEFF),
    onSurfaceVariant = Color(0xFF444466),
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun SmartConnectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
