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

package com.lambda.newui

import com.lambda.Lambda.mc
import com.lambda.graphics.mc.LambdaRenderPipelines
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.VertexFormat
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.system.MemoryUtil
import java.util.*
import androidx.compose.ui.graphics.Color

object GuiGlowRenderer {
    private var fullscreenQuadBuffer: GpuBuffer? = null

    private fun ensureFullscreenQuad() {
        if (fullscreenQuadBuffer != null) return

        val vertexSize = 20
        val buffer = MemoryUtil.memAlloc(4 * vertexSize)
        try {
            buffer.putFloat(-1f).putFloat(1f).putFloat(0f).putFloat(0f).putFloat(1f)
            buffer.putFloat(-1f).putFloat(-1f).putFloat(0f).putFloat(0f).putFloat(0f)
            buffer.putFloat(1f).putFloat(-1f).putFloat(0f).putFloat(1f).putFloat(0f)
            buffer.putFloat(1f).putFloat(1f).putFloat(0f).putFloat(1f).putFloat(1f)
            buffer.flip()

            fullscreenQuadBuffer = RenderSystem.getDevice().createBuffer(
                { "Lambda Gui Glow Fullscreen Quad" },
                GpuBuffer.USAGE_VERTEX,
                buffer
            )
        } finally {
            MemoryUtil.memFree(buffer)
        }
    }

    fun renderGlow(textureView: GpuTextureView, radius: Float, intensity: Float, color1: Color, color2: Color) {
        val framebuffer = mc.framebuffer ?: return
        
        ensureFullscreenQuad()
        val quadBuffer = fullscreenQuadBuffer ?: return

        val styleMat = Matrix4f()
        styleMat.m00(radius)
        styleMat.m01(intensity)
        styleMat.m10(color1.red)
        styleMat.m11(color1.green)
        styleMat.m12(color1.blue)
        styleMat.m20(color2.red)
        styleMat.m21(color2.green)
        styleMat.m22(color2.blue)

        val time = (System.currentTimeMillis() % 100000L) / 1000f

        val dynamicTransform = RenderSystem.getDynamicUniforms().write(
            Matrix4f(), Vector4f(1f, 1f, 1f, 1f), Vector3f(time, 0f, 0f), styleMat
        )

        RenderSystem.getDevice()
            .createCommandEncoder()
            .createRenderPass(
                { "Lambda Gui Glow Pass" },
                framebuffer.colorAttachmentView,
                OptionalInt.empty(),
                null,
                OptionalDouble.empty()
            )?.use { pass ->
                pass.setPipeline(LambdaRenderPipelines.GUI_GLOW)
                val nearestSampler = RenderSystem.getSamplerCache().get(FilterMode.NEAREST)
                pass.bindTexture("Sampler0", textureView, nearestSampler)
                pass.setUniform("DynamicTransforms", dynamicTransform)
                pass.setVertexBuffer(0, quadBuffer)
                
                val shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS)
                val indexBuffer = shapeIndexBuffer.getIndexBuffer(4)
                pass.setIndexBuffer(indexBuffer, shapeIndexBuffer.indexType)
                pass.drawIndexed(0, 0, 6, 1)
            }
    }

    fun cleanup() {
        fullscreenQuadBuffer?.close()
        fullscreenQuadBuffer = null
    }
}
