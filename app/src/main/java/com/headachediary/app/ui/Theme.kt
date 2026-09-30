package com.headachediary.app.ui

import androidx.annotation.StringRes
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.headachediary.app.R

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4B9A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF1B0F4A),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE4FF),
    onSecondaryContainer = Color(0xFF12224F),
    tertiary = Color(0xFF8E4A6B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E8),
    onTertiaryContainer = Color(0xFF3A0B24),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF7A7582),
    outlineVariant = Color(0xFFCAC4D0),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F0FA),
    surfaceContainer = Color(0xFFF1ECF6),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0EB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCFBCFF),
    onPrimary = Color(0xFF33216F),
    primaryContainer = Color(0xFF4A3887),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFB8C4F5),
    onSecondary = Color(0xFF212F5E),
    secondaryContainer = Color(0xFF384677),
    onSecondaryContainer = Color(0xFFDCE4FF),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF4A2535),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E8),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
)

@Composable
fun HeadacheTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

/** Мягкий цвет карточек: чуть отличается от фона, без тяжёлой серой заливки. */
@Composable
fun softCardColors(): CardColors =
    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)

/** Цвет по силе боли: жёлтый — слабая, оранжевый — средняя, красный — сильная. */
fun painColor(intensity: Int?): Color = when {
    intensity == null -> Color(0xFF8E8E93)
    intensity <= 3 -> Color(0xFFF9A825)
    intensity <= 6 -> Color(0xFFEF6C00)
    else -> Color(0xFFD32F2F)
}

fun onPainColor(intensity: Int?): Color =
    if (intensity != null && intensity <= 3) Color.Black else Color.White

@StringRes
fun intensityLabelRes(intensity: Int?): Int = when {
    intensity == null -> R.string.intensity_none
    intensity <= 3 -> R.string.intensity_weak
    intensity <= 6 -> R.string.intensity_medium
    intensity <= 8 -> R.string.intensity_strong
    else -> R.string.intensity_very_strong
}
