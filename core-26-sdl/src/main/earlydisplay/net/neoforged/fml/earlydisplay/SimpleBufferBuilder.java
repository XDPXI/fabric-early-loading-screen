/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.earlydisplay;

import java.io.Closeable;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/**
 * Builds the vertices of quads. The layout is the one of the RenderPearl format POSITION_TEX_COLOR:
 * position (3 floats), texture coordinates (2 floats) and colour (4 bytes).
 * The positions are in pixels. {@link EarlyGraphics} converts them when it draws.
 */
public class SimpleBufferBuilder implements Closeable {
    public static final int STRIDE = 3 * 4 + 2 * 4 + 4;
    private static final MemoryUtil.MemoryAllocator ALLOCATOR = MemoryUtil.getAllocator(false);

    private long bufferAddr;   // Pointer to the backing buffer.
    private ByteBuffer buffer; // ByteBuffer view of the backing buffer.

    private boolean building;  // If we are building the buffer.
    private int elementIndex;  // The part of the vertex we write next: 0 is position, 1 is texture and 2 is colour. 3 means we expect 'endVertex'.
    private int index;         // The current index into the buffer we are writing to.
    private int vertices;      // The number of complete vertices we have buffered.

    public SimpleBufferBuilder(int capacity) {
        bufferAddr = ALLOCATOR.malloc(capacity);
        buffer = MemoryUtil.memByteBuffer(bufferAddr, capacity);
    }

    public SimpleBufferBuilder begin() {
        if (bufferAddr == MemoryUtil.NULL) {
            throw new IllegalStateException("Buffer has been freed.");
        }
        if (building) {
            throw new IllegalStateException("Already building.");
        }
        building = true;
        elementIndex = 0;
        index = 0;
        vertices = 0;
        ensureSpace(STRIDE);
        return this;
    }

    private void expect(int element) {
        if (!building) throw new IllegalStateException("Not building.");
        if (elementIndex == 3) throw new IllegalStateException("Expected endVertex");
        if (elementIndex != element) throw new IllegalArgumentException("Expected element " + elementIndex);
    }

    public SimpleBufferBuilder pos(float x, float y) {
        expect(0);
        buffer.putFloat(index, x);
        buffer.putFloat(index + 4, y);
        buffer.putFloat(index + 8, 0f);
        index += 12;
        elementIndex++;
        return this;
    }

    public SimpleBufferBuilder tex(float u, float v) {
        expect(1);
        buffer.putFloat(index, u);
        buffer.putFloat(index + 4, v);
        index += 8;
        elementIndex++;
        return this;
    }

    public SimpleBufferBuilder colour(int packedColor) {
        expect(2);
        buffer.putInt(index, packedColor);
        index += 4;
        elementIndex++;
        return this;
    }

    public SimpleBufferBuilder endVertex() {
        if (!building) throw new IllegalStateException("Not building.");
        if (elementIndex != 3) throw new IllegalStateException("The vertex is not complete.");
        elementIndex = 0;
        vertices++;
        ensureSpace(STRIDE);
        return this;
    }

    private void ensureSpace(int newBytes) {
        int cap = buffer.capacity();
        if (index + newBytes > cap) {
            int newCap = Math.max(3 * cap / 2, index + newBytes);
            bufferAddr = ALLOCATOR.realloc(bufferAddr, newCap);
            buffer = MemoryUtil.memByteBuffer(bufferAddr, newCap);
        }
    }

    /**
     * Send the quads to the GPU with the given pipeline and texture, then clear this builder.
     */
    public void draw(EarlyGraphics graphics, ElementPipeline.RenderType type, EarlyGraphics.TextureBinding texture) {
        if (!building) throw new IllegalStateException("Not building.");
        try {
            if (elementIndex != 0) throw new IllegalStateException("The last vertex is not complete.");
            if (vertices % 4 != 0) throw new IllegalStateException("The vertices are not a whole number of quads.");
            graphics.drawQuads(type, texture, buffer.position(0).limit(index), vertices);
        } finally {
            buffer.clear();
            building = false;
            vertices = 0;
            index = 0;
        }
    }

    @Override
    public void close() {
        ALLOCATOR.free(bufferAddr);
        bufferAddr = MemoryUtil.NULL;
    }
}
