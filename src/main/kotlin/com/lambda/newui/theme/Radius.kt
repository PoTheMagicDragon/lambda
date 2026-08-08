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

import androidx.compose.ui.unit.dp

/**
 * Corner radii, on the same four steps and the same tiers as [Spacing]: a component's
 * radius comes from the tier it belongs to, so its corners stay in proportion to the
 * room it is given.
 */
object Radius {
	/**
	 * Internal interactive elements.
	 * Checkboxes, toggle switches, colour swatches, small module status tags.
	 */
	val ExtraSmall = 2.dp

	/**
	 * Standard actionable elements, and the baseline for anything unlisted.
	 * Sliders, dropdown fields, text inputs, tab bars, configuration buttons.
	 */
	val Small = 4.dp

	/**
	 * Structural components.
	 * Module panels, draggable menus, HUD widgets, setting popups.
	 */
	val Medium = 8.dp

	/**
	 * Major layout windows.
	 * The GUI wrapper that hosts every panel.
	 */
	val Large = 12.dp
}
