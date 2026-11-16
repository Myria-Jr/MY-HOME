@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.alltag

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/* Fallback palette for Android 8–11 (no wallpaper colours): blue primary, teal tertiary. */
private val FallbackLight = lightColorScheme(
    primary = Color(0xFF415F91), onPrimary = Color.White, primaryContainer = Color(0xFFD6E3FF), onPrimaryContainer = Color(0xFF284777),
    secondary = Color(0xFF565F71), onSecondary = Color.White, secondaryContainer = Color(0xFFDAE2F9), onSecondaryContainer = Color(0xFF3E4759),
    tertiary = Color(0xFF006A60), onTertiary = Color.White, tertiaryContainer = Color(0xFF9EF2E4), onTertiaryContainer = Color(0xFF005048),
    background = Color(0xFFF9F9FF), surface = Color(0xFFF9F9FF), onSurface = Color(0xFF191C20), onSurfaceVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF3F3FA), surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE7E8EE), surfaceContainerHighest = Color(0xFFE2E2E9),
    outline = Color(0xFF74777F), outlineVariant = Color(0xFFC4C6D0),
)
private val FallbackDark = darkColorScheme(
    primary = Color(0xFFAAC7FF), onPrimary = Color(0xFF0A305F), primaryContainer = Color(0xFF284777), onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBEC6DC), onSecondary = Color(0xFF283141), secondaryContainer = Color(0xFF3E4759), onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFF82D5C8), onTertiary = Color(0xFF003731), tertiaryContainer = Color(0xFF005048), onTertiaryContainer = Color(0xFF9EF2E4),
    background = Color(0xFF111318), surface = Color(0xFF111318), onSurface = Color(0xFFE2E2E9), onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Color(0xFF191C20), surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F), surfaceContainerHighest = Color(0xFF33353A),
    outline = Color(0xFF8E9099), outlineVariant = Color(0xFF44474E),
)

/** Work mode swaps primary and tertiary, so the whole UI shifts hue but stays in the wallpaper's palette. */
private fun ColorScheme.forWork() = copy(
    primary = tertiary, onPrimary = onTertiary, primaryContainer = tertiaryContainer, onPrimaryContainer = onTertiaryContainer,
    tertiary = primary, onTertiary = onPrimary, tertiaryContainer = primaryContainer, onTertiaryContainer = onPrimaryContainer,
)

@Composable
fun AlltagTheme(dark: Boolean, work: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val base = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> FallbackDark
        else -> FallbackLight
    }
    val t = if (work) base.forWork() else base

    @Composable
    fun a(c: Color) = animateColorAsState(c, tween(650), label = "scheme").value

    val scheme = t.copy(
        primary = a(t.primary), onPrimary = a(t.onPrimary), primaryContainer = a(t.primaryContainer), onPrimaryContainer = a(t.onPrimaryContainer),
        tertiary = a(t.tertiary), onTertiary = a(t.onTertiary), tertiaryContainer = a(t.tertiaryContainer), onTertiaryContainer = a(t.onTertiaryContainer),
    )
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), typography = AlltagType, content = content)
}

/* Heavier, tighter headlines for the expressive look. */
private val AlltagType: Typography = Typography().let { t ->
    fun TextStyle.w(w: Int, ls: Double = 0.0) = copy(fontWeight = FontWeight(w), letterSpacing = if (ls != 0.0) ls.sp else letterSpacing)
    t.copy(
        displayLarge = t.displayLarge.w(500, -1.5), displayMedium = t.displayMedium.w(500, -1.0), displaySmall = t.displaySmall.w(600, -0.8),
        headlineLarge = t.headlineLarge.w(600, -0.8), headlineMedium = t.headlineMedium.w(650, -0.6), headlineSmall = t.headlineSmall.w(600, -0.3),
        titleLarge = t.titleLarge.w(600), titleMedium = t.titleMedium.w(600), titleSmall = t.titleSmall.w(600),
        displayLargeEmphasized = t.displayLargeEmphasized.w(700, -2.0), displayMediumEmphasized = t.displayMediumEmphasized.w(700, -1.4),
        displaySmallEmphasized = t.displaySmallEmphasized.w(750, -1.0),
        headlineLargeEmphasized = t.headlineLargeEmphasized.w(750, -0.8), headlineMediumEmphasized = t.headlineMediumEmphasized.w(750, -0.6),
        headlineSmallEmphasized = t.headlineSmallEmphasized.w(700, -0.3),
        titleLargeEmphasized = t.titleLargeEmphasized.w(700), titleMediumEmphasized = t.titleMediumEmphasized.w(700), titleSmallEmphasized = t.titleSmallEmphasized.w(700),
        labelLargeEmphasized = t.labelLargeEmphasized.w(700), labelMediumEmphasized = t.labelMediumEmphasized.w(700),
    )
}

/** Money colours that sit well next to any wallpaper palette. */
object Money {
    val good: Color
        @Composable @ReadOnlyComposable get() = if (MaterialTheme.colorScheme.isDark()) Color(0xFF7DDB9A) else Color(0xFF1B6D36)
    val bad: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error
}

fun ColorScheme.isDark() = surface.luminanceApprox() < 0.5f
private fun Color.luminanceApprox() = 0.2126f * red + 0.7152f * green + 0.0722f * blue
