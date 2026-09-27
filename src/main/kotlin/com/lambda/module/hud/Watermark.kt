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

package com.lambda.module.hud

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lambda.graphics.texture.TextureOwner.upload
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImGui
import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag
import com.lambda.newui.Res
import com.lambda.newui.lambda
import com.lambda.newui.state.LambdaState.observe
import org.jetbrains.compose.resources.painterResource

@Suppress("unused")
object Watermark : HudModule(
    name = "Watermark",
    tag = ModuleTag.HUD,
    enabledByDefault = true,
) {
    private val texture = upload("drawable/lambda.png")
    // Kept as a Setting reference so the Compose element can observe() it.
    private val scaleSetting = setting("Scale", 0.15f, 0.01f..1f, 0.01f)
    private val scale by scaleSetting

    override fun ImGuiBuilder.buildLayout() {
        val width = texture.width * scale
        val height = texture.height * scale
        ImGui.image(texture.id.toLong(), width, height)
    }

    @Composable
    override fun Content() {
        val imageScale by scaleSetting.observe()
        val painter = painterResource(Res.drawable.lambda)
        // Sized in dp from the image's pixel size, so the watermark grows with the screen.
        Image(
            painter = painter,
            contentDescription = "Lambda watermark",
            modifier = Modifier.size(
                (painter.intrinsicSize.width * imageScale).dp,
                (painter.intrinsicSize.height * imageScale).dp
            )
        )
    }
}
