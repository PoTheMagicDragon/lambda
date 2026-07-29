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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object LambdaColors {
    val Primary = Color(100, 180, 255)          // Bright blue accent
    val Secondary = Color(225, 130, 225)        // Pink/magenta accent
    val Surface = Color(18, 0, 8)               // Deep dark background
    val SurfaceVariant = Color(35, 3, 18)       // Slightly lighter dark
    val OnSurface = Color(240, 240, 245)        // Near-white text
    val OnSurfaceVariant = Color(140, 140, 150) // Dimmed text
    val ModuleEnabled = Color(88, 0, 35, 255)     // Solid blue when module is on
    val ModuleDisabled = Color(40, 5, 22, 200)  // Muted dark when module is off
    val Border = Color(140, 15, 65)             // Pink border — higher contrast
    val HeaderBg = Color(130, 5, 55)            // Category header background
}

private val LambdaColorScheme = darkColorScheme(
    primary = LambdaColors.Primary,
    secondary = LambdaColors.Secondary,
    surface = LambdaColors.Surface,
    surfaceVariant = LambdaColors.SurfaceVariant,
    onSurface = LambdaColors.OnSurface,
    onSurfaceVariant = LambdaColors.OnSurfaceVariant,
    outline = LambdaColors.Border,
    primaryContainer = LambdaColors.HeaderBg,
)

private val LambdaTypography = Typography(
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = LambdaColors.OnSurface,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = LambdaColors.OnSurface,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        color = LambdaColors.OnSurfaceVariant,
    ),
)

/**
 * Lambda Material3 theme wrapper.
 * Applies the dark color scheme and typography derived from the existing ImGui GUI.
 */
@Composable
fun LambdaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LambdaColorScheme,
        typography = LambdaTypography,
        content = content
    )
}
