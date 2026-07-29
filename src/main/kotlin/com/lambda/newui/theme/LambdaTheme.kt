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

/**
 * Lambda color palette — derived from the existing ImGui ClickGuiLayout colors.
 */
object LambdaColors {
    val Primary = Color(130, 200, 255)         // Bright blue accent
    val Secondary = Color(225, 130, 225)       // Pink/magenta accent
    val Surface = Color(35, 0, 14)             // Dark background
    val SurfaceVariant = Color(55, 5, 25)      // Slightly lighter dark
    val OnSurface = Color(255, 255, 255)       // White text
    val OnSurfaceVariant = Color(180, 180, 180) // Dimmed text
    val ModuleEnabled = Color(130, 200, 255, 180)  // Blue glow when module is on
    val ModuleDisabled = Color(80, 10, 40, 150)    // Muted dark when module is off
    val Border = Color(130, 12, 60)            // Pink border
    val HeaderBg = Color(125, 0, 50)           // Category header background
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
