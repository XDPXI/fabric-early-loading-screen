/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.PolygonMode;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.pipeline.UniformType;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import net.minecraft.resources.Identifier;

/**
 * The render pipelines of the early display, built with RenderPearl (Blaze3D).
 * There is one pipeline for each {@link RenderType}. They share one vertex shader.
 * The shaders are in memory because no resource pack exists at this stage of the start.
 */
public class ElementPipeline implements AutoCloseable {
    /** The name of the sampler in all fragment shaders. */
    public static final String SAMPLER = "Sampler0";

    private static final Identifier VERTEX = Identifier.parse("early_loading_screen:element");

    private static final String VERTEX_SOURCE = """
            #version 330
            #extension GL_ARB_separate_shader_objects : require

            layout(location = 0) in vec3 Position;
            layout(location = 1) in vec2 UV0;
            layout(location = 2) in vec4 Color;

            layout(location = 0) out vec2 texCoord0;
            layout(location = 1) out vec4 vertexColor;

            void main() {
                // The position is in normalised device coordinates. The vertex builder converts it.
                gl_Position = vec4(Position, 1.0);
                texCoord0 = UV0;
                vertexColor = Color;
            }
            """;

    private static String fragment(String body) {
        return """
                #version 330
                #extension GL_ARB_separate_shader_objects : require

                uniform sampler2D Sampler0;

                layout(location = 0) in vec2 texCoord0;
                layout(location = 1) in vec4 vertexColor;

                layout(location = 0) out vec4 fragColor;

                void main() {
                """ + body + """
                }
                """;
    }

    public enum RenderType {
        /** Single channel font atlas. The glyph coverage is in the red channel. */
        FONT("font", fragment("fragColor = vec4(1.0, 1.0, 1.0, texture(Sampler0, texCoord0).r) * vertexColor;")),
        /** Full colour texture. */
        TEXTURE("texture", fragment("fragColor = texture(Sampler0, texCoord0) * vertexColor;")),
        /** Plain colour, the sampler is not read. */
        BAR("bar", fragment("fragColor = vertexColor;"));

        private final Identifier fragmentId;
        private final String fragmentSource;

        RenderType(String name, String fragmentSource) {
            this.fragmentId = Identifier.parse("early_loading_screen:element_" + name);
            this.fragmentSource = fragmentSource;
        }
    }

    private final Map<RenderType, CompiledRenderPipeline> compiled = new EnumMap<>(RenderType.class);

    public void init(GpuDevice device) {
        final ShaderSource source = new ShaderSource() {
            @Override
            public String getShader(Identifier id, ShaderType type) {
                if (type == ShaderType.VERTEX && id.equals(VERTEX)) {
                    return VERTEX_SOURCE;
                }
                for (var renderType : RenderType.values()) {
                    if (type == ShaderType.FRAGMENT && id.equals(renderType.fragmentId)) {
                        return renderType.fragmentSource;
                    }
                }
                return null;
            }

            @Override
            public ShaderSource.CachedIncludeSource getInclude(Identifier id) {
                return ShaderSource.CachedIncludeSource.createError("The early display has no shader includes: " + id);
            }

            @Override
            public void close() {}
        };
        // The compile runs on the calling thread, so the OpenGL context of this thread is the one in use.
        final Executor sameThread = Runnable::run;
        for (var renderType : RenderType.values()) {
            var pipeline = RenderPipeline.builder()
                    .withLocation(Identifier.parse("early_loading_screen:pipeline/" + renderType.name().toLowerCase()))
                    .withVertexShader(VERTEX)
                    .withFragmentShader(renderType.fragmentId)
                    .withBindGroupLayout(BindGroupLayout.builder().withUniform(SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER).build())
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                    .withPolygonMode(PolygonMode.FILL)
                    .withCull(false)
                    .withDepthStencilState(Optional.empty())
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .build();
            try {
                compiled.put(renderType, device.compilePipeline(pipeline, source, sameThread).get().finishCompile());
            } catch (Exception e) {
                throw new IllegalStateException("Could not compile the " + renderType + " pipeline of the early display", e);
            }
        }
    }

    public CompiledRenderPipeline get(RenderType type) {
        return compiled.get(type);
    }

    @Override
    public void close() {
        compiled.values().forEach(CompiledRenderPipeline::close);
        compiled.clear();
    }
}
