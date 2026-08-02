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

package com.lambda.module.modules.client

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lambda.config.Config
import com.lambda.config.ConfigBlock
import com.lambda.config.ConfigEditor.forEachSetting
import com.lambda.config.Group
import com.lambda.config.withEdits
import com.lambda.module.Module
import com.lambda.module.modules.client.Style.LambdaTypography
import com.lambda.module.modules.client.Style.lambdaTheme
import com.lambda.module.tag.ModuleTag
import com.lambda.newui.theme.SystemThemeTracker.isSystemDark
import com.lambda.util.NamedEnum

object Style : Module(
	name = "Style",
	description = "Controls the style of the ui",
	tag = ModuleTag.CLIENT
) {
	const val DARK_THEME_GROUP = "Dark Theme"
	const val LIGHT_THEME_GROUP = "Light Theme"

	private enum class ThemeMode(
		override val displayName: String = "$this"
	) : NamedEnum {
		Auto,
		Dark,
		Light
	}

	private enum class TrueThemeMode(val theme: LambdaColorSettings) {
		Dark(darkTheme),
		Light(lightTheme)
	}

	private val themeMode by setting("Theme", ThemeMode.Auto)

	val enableGlow by setting("Enable Glow", true)
	val glowRadius by setting("Glow Radius", 35f, 0f..100f, 1f)
	val glowIntensity by setting("Glow Intensity", 1.2f, 0f..10f, 0.1f)
	val glowColor1 by setting("Glow Color 1", Color(0, 128, 255))
	val glowColor2 by setting("Glow Color 2", Color(255, 50, 153))

	@Group(DARK_THEME_GROUP) private val darkTheme: LambdaColorSettings by configBlock(
		LambdaColorSettings(
			this,
			true,
			primary = Color(100, 180, 255),
			secondary = Color(225, 130, 225),
			surface = Color(18, 0, 8),
			surfaceVariant = Color(35, 3, 18),
			onSurface = Color(240, 240, 245),
			onSurfaceVariant = Color(140, 140, 150),
			headerBg = Color(130, 5, 55),
			onHeaderBg = Color(240, 240, 245),
			moduleEnabled = Color(88, 0, 35, 255),
			onModuleEnabled = Color(240, 240, 245),
			moduleDisabled = Color(40, 5, 22, 200),
			border = Color(140, 15, 65),
			textShadow = Color(0, 0, 0, 180),
		)
	).withEdits {
		forEachSetting {
			onValueChange { _, _ ->
				val visible = themeMode == ThemeMode.Dark || (themeMode == ThemeMode.Auto && isSystemDark)
				if (visible) lambdaTheme.value = darkTheme.toLambdaPalette()
			}
		}
	}

	@Group(LIGHT_THEME_GROUP) private val lightTheme: LambdaColorSettings by configBlock(
		LambdaColorSettings(
			this,
			false,
			primary = Color(0, 100, 180),
			secondary = Color(160, 40, 155),
			surface = Color(250, 244, 246),
			surfaceVariant = Color(240, 226, 232),
			onSurface = Color(26, 10, 16),
			onSurfaceVariant = Color(110, 92, 100),
			headerBg = Color(196, 42, 94),
			onHeaderBg = Color(255, 250, 252),
			moduleEnabled = Color(248, 200, 216, 255),
			onModuleEnabled = Color(74, 6, 30),
			moduleDisabled = Color(236, 228, 231, 200),
			border = Color(198, 118, 148),
			textShadow = Color(0, 0, 0, 60),
		)
	).withEdits {
		forEachSetting {
			onValueChange { _, _ ->
				val visible = themeMode == ThemeMode.Light || (themeMode == ThemeMode.Auto && !isSystemDark)
				if (visible) lambdaTheme.value = lightTheme.toLambdaPalette()
			}
		}
	}

	private var trueThemeMode = getTrueThemeMode()
	val lambdaTheme =
		mutableStateOf(
			trueThemeMode.theme.toLambdaPalette(),
			referentialEqualityPolicy()
		)

	val LambdaTypography = Typography(
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

	fun updateLambdaTheme() {
		val currentTrueThemeMode = getTrueThemeMode()
		if (trueThemeMode == currentTrueThemeMode) return
		lambdaTheme.value = currentTrueThemeMode.theme.toLambdaPalette()
		trueThemeMode = currentTrueThemeMode
	}

	private fun getTrueThemeMode() =
		if (themeMode == ThemeMode.Dark || (themeMode == ThemeMode.Auto && isSystemDark)) TrueThemeMode.Dark
		else TrueThemeMode.Light
}

@Composable
fun LambdaTheme(content: @Composable () -> Unit) {
	MaterialTheme(
		colorScheme = lambdaTheme.value,
		typography = LambdaTypography,
		content = content
	)
}

class LambdaColorSettings(
	override val c: Config,
	private val dark: Boolean,
	primary: Color,
	secondary: Color,
	surface: Color,
	surfaceVariant: Color,
	onSurface: Color,
	onSurfaceVariant: Color,
	headerBg: Color,
	onHeaderBg: Color,
	moduleEnabled: Color,
	onModuleEnabled: Color,
	moduleDisabled: Color,
	border: Color,
	textShadow: Color,
) : ConfigBlock {
	val primary by c.setting("Primary", primary)
	val secondary by c.setting("Secondary", secondary)
	val surface by c.setting("Surface", surface)
	val surfaceVariant by c.setting("Surface Variant", surfaceVariant)
	val onSurface by c.setting("On Surface", onSurface)
	val onSurfaceVariant by c.setting("On Surface Variant", onSurfaceVariant)
	val headerBg by c.setting("Header Bg", headerBg)
	val onHeaderBg by c.setting("On Header Bg", onHeaderBg)
	val moduleEnabled by c.setting("Module Enabled", moduleEnabled)
	val onModuleEnabled by c.setting("On Module Enabled", onModuleEnabled)
	val moduleDisabled by c.setting("Module Disabled", moduleDisabled)
	val border by c.setting("Border", border)
	val textShadow by c.setting("Text Shadow", textShadow)

	fun toLambdaPalette() =
		(if (dark) darkColorScheme() else lightColorScheme()).copy(
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
}