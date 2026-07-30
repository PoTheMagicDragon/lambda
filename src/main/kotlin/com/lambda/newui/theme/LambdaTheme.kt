/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.newui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The colors the Compose GUI draws with, named for what they paint rather than for
 * Material3's slots.
 *
 * Lambda's surfaces are not M3 concepts — a category header and a module card have no slot
 * of their own — so the palette is declared in Lambda's own terms here and projected onto
 * M3 by [toColorScheme]. Components never read this directly; they read
 * `MaterialTheme.colorScheme`, which is what lets the whole GUI swap palettes at once.
 */
data class LambdaPalette(
    val primary: Color,
    val secondary: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    /** Category header strip, and the text drawn on it. */
    val headerBg: Color,
    val onHeaderBg: Color,
    /** Module card while the module is on, and the text drawn on it. */
    val moduleEnabled: Color,
    val onModuleEnabled: Color,
    /** Module card while the module is off. Its text uses [onSurfaceVariant]. */
    val moduleDisabled: Color,
    val border: Color,
    /** Hard offset shadow behind GUI text, in the style of Minecraft's own font. */
    val textShadow: Color,
)

/** The original crimson-on-near-black palette. Also the fallback when the OS has no preference. */
val LambdaDarkPalette = LambdaPalette(
    primary = Color(100, 180, 255),          // Bright blue accent
    secondary = Color(225, 130, 225),        // Pink/magenta accent
    surface = Color(18, 0, 8),               // Deep dark background
    surfaceVariant = Color(35, 3, 18),       // Slightly lighter dark
    onSurface = Color(240, 240, 245),        // Near-white text
    onSurfaceVariant = Color(140, 140, 150), // Dimmed text
    headerBg = Color(130, 5, 55),
    onHeaderBg = Color(240, 240, 245),
    moduleEnabled = Color(88, 0, 35, 255),   // Lifted crimson when module is on
    onModuleEnabled = Color(240, 240, 245),
    moduleDisabled = Color(40, 5, 22, 200),  // Muted dark when module is off
    border = Color(140, 15, 65),             // Pink border — higher contrast
    textShadow = Color(0, 0, 0, 180),
)

/**
 * Light counterpart to [LambdaDarkPalette].
 *
 * Lightness is inverted but the crimson identity is not: the header stays a saturated
 * crimson with near-white text on it, so the GUI still reads as the same thing rather than
 * as a generic light theme. The enabled module card becomes a pale crimson wash — a tint
 * light enough for dark text, since on a light surface an enabled row has to read as
 * *lifted* rather than as *darkened*.
 */
val LambdaLightPalette = LambdaPalette(
    primary = Color(0, 100, 180),            // Deep blue — the dark palette's accent is too pale here
    secondary = Color(160, 40, 155),
    surface = Color(250, 244, 246),          // Near-white, faintly warmed toward the crimson
    surfaceVariant = Color(240, 226, 232),
    onSurface = Color(26, 10, 16),           // Near-black text
    onSurfaceVariant = Color(110, 92, 100),  // Dimmed text
    headerBg = Color(196, 42, 94),
    onHeaderBg = Color(255, 250, 252),
    moduleEnabled = Color(248, 200, 216, 255),
    onModuleEnabled = Color(74, 6, 30),
    moduleDisabled = Color(236, 228, 231, 200),
    border = Color(198, 118, 148),
    textShadow = Color(0, 0, 0, 60),         // Much softer — a hard black shadow muddies dark text
)

/**
 * Projects a [LambdaPalette] onto Material3's semantic slots.
 *
 * A few slots are chosen for the role they play rather than their name, since Lambda's
 * surfaces have no M3 equivalent: `primaryContainer` carries the header strip (M3's
 * prominent-container slot), `secondaryContainer` the enabled module card (the slot M3's
 * own components use for a selected row), `surfaceContainer` the disabled one (a resting
 * container drawn on top of `surface`), and `scrim` the text shadow (M3 has no shadow
 * slot, and scrim is the closest thing — an overlay darkening what is behind it).
 *
 * The unset slots come from M3's own light/dark baselines, which is why [isDark] is passed
 * separately rather than inferred: it picks sensible defaults for everything Lambda does
 * not paint yet, such as the error colors.
 */
private fun LambdaPalette.toColorScheme(isDark: Boolean): ColorScheme =
    (if (isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = primary,
        secondary = secondary,
        surface = surface,
        surfaceVariant = surfaceVariant,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
        outline = border,
        primaryContainer = headerBg,
        onPrimaryContainer = onHeaderBg,
        secondaryContainer = moduleEnabled,
        onSecondaryContainer = onModuleEnabled,
        surfaceContainer = moduleDisabled,
        scrim = textShadow,
    )

/**
 * Typography carries no colors: a baked-in color would survive the palette swap and leave
 * near-white text on a near-white surface in light mode. Color comes from the color scheme
 * at each call site instead.
 */
private val LambdaTypography = Typography(
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
    ),
)

/**
 * Lambda Material3 theme wrapper.
 *
 * Selects [LambdaLightPalette] or [LambdaDarkPalette] from the OS setting tracked by
 * [SystemThemeTracker], and recomposes the GUI when that changes.
 */
@Composable
fun LambdaTheme(content: @Composable () -> Unit) {
    val isDark by SystemThemeTracker.isDark

    val colorScheme = remember(isDark) {
        (if (isDark) LambdaDarkPalette else LambdaLightPalette).toColorScheme(isDark)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LambdaTypography,
        content = content
    )
}
