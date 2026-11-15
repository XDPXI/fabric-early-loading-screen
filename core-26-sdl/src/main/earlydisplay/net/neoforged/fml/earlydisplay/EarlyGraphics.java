/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

/**
 * All the drawing of the early display goes through this class.
 * It only uses the RenderPearl (Blaze3D) API: a {@link GpuDevice}, a {@link CommandEncoder} and {@link RenderPass}es.
 */
public final class EarlyGraphics implements AutoCloseable {
    /** The most quads one draw call can have. */
    public static final int MAX_QUADS = 8192;

    /** A texture, the view of it and the sampler to read it with. */
    public record TextureBinding(GpuTextureView view, GpuSampler sampler) {}

    /** A texture that this class owns. The samplers are shared, so close it with {@link #closeTexture(OwnedTexture)}. */
    public record OwnedTexture(GpuTexture texture, GpuTextureView view, GpuSampler sampler) {
        public TextureBinding binding() {
            return new TextureBinding(view, sampler);
        }
    }

    private final GpuDevice device;
    private final ElementPipeline pipelines = new ElementPipeline();
    private final GpuSampler nearest;
    private final GpuSampler linear;
    private final GpuBuffer indexBuffer;
    private final List<OwnedTexture> ownedTextures = new ArrayList<>();
    private final OwnedTexture white;

    private RenderPass pass;
    private int viewportWidth;
    private int viewportHeight;
    private boolean flipY;

    public EarlyGraphics(GpuDevice device) {
        this.device = device;
        this.pipelines.init(device);
        this.nearest = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, 1, OptionalDouble.empty());
        this.linear = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());

        // Two triangles for each quad. The vertices of a quad are top left, top right, bottom left and bottom right.
        var indices = MemoryUtil.memAlloc(MAX_QUADS * 6 * Integer.BYTES);
        try {
            for (int i = 0; i < MAX_QUADS; i++) {
                indices.putInt(i * 4).putInt(i * 4 + 1).putInt(i * 4 + 2);
                indices.putInt(i * 4 + 1).putInt(i * 4 + 3).putInt(i * 4 + 2);
            }
            indices.flip();
            this.indexBuffer = device.createBuffer(() -> "Early display indices", GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, indices);
        } finally {
            MemoryUtil.memFree(indices);
        }

        var whitePixel = MemoryUtil.memAlloc(4);
        try {
            whitePixel.putInt(0xFFFFFFFF).flip();
            this.white = createTexture("Early display white", GpuFormat.RGBA8_UNORM, 1, 1, whitePixel, false);
        } finally {
            MemoryUtil.memFree(whitePixel);
        }
    }

    public GpuDevice device() {
        return device;
    }

    public TextureBinding whiteBinding() {
        return white.binding();
    }

    /**
     * Create a texture and upload the pixels. The texture is closed when this object is closed.
     */
    public OwnedTexture createTexture(String label, GpuFormat format, int width, int height, ByteBuffer pixels, boolean smooth) {
        var texture = device.createTexture(label, GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST, format, width, height, 1, 1);
        var encoder = device.createCommandEncoder();
        encoder.writeToTexture(texture, pixels, 0, 0, 0, 0, width, height);
        var owned = new OwnedTexture(texture, device.createTextureView(texture), smooth ? linear : nearest);
        // The samplers are shared, so only the texture and its view are closed with it
        ownedTextures.add(new OwnedTexture(texture, owned.view(), null));
        return owned;
    }

    /**
     * Create a texture that can be drawn to and read.
     */
    public OwnedTexture createRenderTarget(String label, int width, int height) {
        var texture = device.createTexture(label, GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
        var owned = new OwnedTexture(texture, device.createTextureView(texture), nearest);
        ownedTextures.add(new OwnedTexture(texture, owned.view(), null));
        return owned;
    }

    public void closeTexture(OwnedTexture texture) {
        ownedTextures.removeIf(t -> t.texture() == texture.texture());
        texture.view().close();
        texture.texture().close();
    }

    public CommandEncoder encoder() {
        return device.createCommandEncoder();
    }

    /**
     * Start a render pass that clears the target and then draws on it.
     * With flipY the top of the pixel space is the first row of the texture, as the game samples textures.
     */
    public void beginPass(String label, GpuTextureView target, int width, int height, float red, float green, float blue, float alpha, boolean flipY) {
        if (pass != null) {
            throw new IllegalStateException("A render pass is open already");
        }
        this.viewportWidth = width;
        this.viewportHeight = height;
        this.flipY = flipY;
        this.pass = device.createCommandEncoder().createRenderPass(() -> label, target, Optional.of(new Vector4f(red, green, blue, alpha)));
    }

    public void endPass() {
        if (pass == null) {
            return;
        }
        pass.close();
        pass = null;
        device.createCommandEncoder().submit();
    }

    /**
     * Draw quads. The vertices are in pixels of the current render pass, with the origin at the top left.
     */
    public void drawQuads(ElementPipeline.RenderType type, TextureBinding texture, ByteBuffer vertices, int vertexCount) {
        if (pass == null) {
            throw new IllegalStateException("No render pass is open");
        }
        if (vertexCount == 0) {
            return;
        }
        if (vertexCount % 4 != 0 || vertexCount / 4 > MAX_QUADS) {
            throw new IllegalStateException("Bad quad vertex count " + vertexCount);
        }
        // Pixels to normalised device coordinates
        for (int i = 0; i < vertexCount; i++) {
            int base = i * SimpleBufferBuilder.STRIDE;
            vertices.putFloat(base, vertices.getFloat(base) / viewportWidth * 2f - 1f);
            float ny = vertices.getFloat(base + 4) / viewportHeight * 2f;
            vertices.putFloat(base + 4, flipY ? ny - 1f : 1f - ny);
        }
        var slice = device.createCommandEncoder().transientMemory().uploadGpu(vertices, 16, GpuBuffer.USAGE_VERTEX);
        pass.setPipeline(pipelines.get(type));
        pass.setUniform(ElementPipeline.SAMPLER, texture.view(), texture.sampler());
        pass.setVertexBuffer(0, slice);
        pass.setIndexBuffer(indexBuffer, IndexType.INT);
        pass.drawIndexed(vertexCount / 4 * 6, 1, 0, 0, 0);
    }

    @Override
    public void close() {
        if (pass != null) {
            pass.close();
            pass = null;
        }
        for (var texture : ownedTextures) {
            texture.view().close();
            texture.texture().close();
        }
        ownedTextures.clear();
        pipelines.close();
        indexBuffer.close();
        nearest.close();
        linear.close();
    }
}
