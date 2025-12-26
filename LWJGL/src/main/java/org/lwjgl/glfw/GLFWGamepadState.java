/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
package org.lwjgl.glfw;

import org.lwjgl.system.*;

import javax.annotation.*;
import java.nio.*;

import static org.lwjgl.system.Checks.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Describes the input state of a gamepad.
 * 
 * <h3>Layout</h3>
 * 
 * <pre><code>
 * struct GLFWgamepadstate {
 *     unsigned char buttons[15];
 *     float axes[6];
 * }</code></pre>
 */
@NativeType("struct GLFWgamepadstate")
public class GLFWGamepadState extends Struct<GLFWGamepadState> implements NativeResource {

    /** The struct size in bytes. */
    public static final int SIZEOF;

    /** The struct alignment in bytes. */
    public static final int ALIGNOF;

    /** The struct member offsets. */
    public static final int
        BUTTONS,
        AXES;

    static {
        Layout layout = __struct(
            __array(1, 15),  // buttons
            __array(4, 6)    // axes
        );

        SIZEOF = layout.getSize();
        ALIGNOF = layout.getAlignment();

        BUTTONS = layout.offsetof(0);
        AXES = layout.offsetof(1);
    }

    protected GLFWGamepadState(long address, @Nullable ByteBuffer container) {
        super(address, container);
    }

    @Override
    protected GLFWGamepadState create(long address, @Nullable ByteBuffer container) {
        return new GLFWGamepadState(address, container);
    }

    /**
     * Creates a {@code GLFWGamepadState} instance at the current position of the specified {@link ByteBuffer} container. Changes to the buffer's content will be
     * visible to the struct instance and vice versa.
     *
     * <p>The created instance holds a strong reference to the container object.</p>
     */
    public GLFWGamepadState(ByteBuffer container) {
        super(memAddress(container), __checkContainer(container, SIZEOF));
    }

    @Override
    public int sizeof() { return SIZEOF; }

    /**
     * Returns a {@link ByteBuffer} view of the {@code buttons} field.
     */
    @NativeType("unsigned char[15]")
    public ByteBuffer buttons() { return nbuttons(address()); }
    /**
     * Returns the value at the specified index of the {@code buttons} field.
     */
    @NativeType("unsigned char")
    public byte buttons(int index) { return nbuttons(address(), index); }
    /**
     * Returns a {@link FloatBuffer} view of the {@code axes} field.
     */
    @NativeType("float[6]")
    public FloatBuffer axes() { return naxes(address()); }
    /**
     * Returns the value at the specified index of the {@code axes} field.
     */
    public float axes(int index) { return naxes(address(), index); }

    // -----------------------------------

    /** Returns a new {@code GLFWGamepadState} instance allocated with {@link MemoryUtil#memAlloc memAlloc}. The instance must be explicitly freed. */
    public static GLFWGamepadState malloc() {
        return new GLFWGamepadState(nmemAllocChecked(SIZEOF), null);
    }

    /** Returns a new {@code GLFWGamepadState} instance allocated with {@link MemoryUtil#memCalloc memCalloc}. The instance must be explicitly freed. */
    public static GLFWGamepadState calloc() {
        return new GLFWGamepadState(nmemCallocChecked(1, SIZEOF), null);
    }

    /** Returns a new {@code GLFWGamepadState} instance allocated with {@link BufferUtils}. */
    public static GLFWGamepadState create() {
        return new GLFWGamepadState(nmemAllocChecked(SIZEOF), null);
    }

    /** Returns a new {@code GLFWGamepadState} instance for the specified memory address. */
    public static GLFWGamepadState create(long address) {
        return new GLFWGamepadState(address, null);
    }

    /** Like {@link #create(long) create}, but returns {@code null} if {@code address} is {@code NULL}. */
    @Nullable
    public static GLFWGamepadState createSafe(long address) {
        return address == NULL ? null : new GLFWGamepadState(address, null);
    }

    /**
     * Returns a new {@link Buffer} instance allocated with {@link MemoryUtil#memAlloc memAlloc}. The instance must be explicitly freed.
     *
     * @param capacity the buffer capacity
     */
    public static Buffer malloc(int capacity) {
        return new Buffer(nmemAllocChecked(__checkMalloc(capacity, SIZEOF)), capacity);
    }

    /**
     * Returns a new {@link Buffer} instance allocated with {@link MemoryUtil#memCalloc memCalloc}. The instance must be explicitly freed.
     *
     * @param capacity the buffer capacity
     */
    public static Buffer calloc(int capacity) {
        return new Buffer(nmemCallocChecked(capacity, SIZEOF), capacity);
    }

    /**
     * Returns a new {@link Buffer} instance allocated with {@link MemoryUtil#memAlloc memAlloc}.
     *
     * @param capacity the buffer capacity
     */
    public static Buffer create(int capacity) {
        return new Buffer(nmemAllocChecked(__checkMalloc(capacity, SIZEOF)), capacity);
    }

    /**
     * Create a {@link Buffer} instance at the specified memory.
     *
     * @param address  the memory address
     * @param capacity the buffer capacity
     */
    public static Buffer create(long address, int capacity) {
        return new Buffer(address, capacity);
    }

    /** Like {@link #create(long, int) create}, but returns {@code null} if {@code address} is {@code NULL}. */
    @Nullable
    public static Buffer createSafe(long address, int capacity) {
        return address == NULL ? null : new Buffer(address, capacity);
    }

    // -----------------------------------

    /** Unsafe version of {@link #buttons() buttons}. */
    public static ByteBuffer nbuttons(long struct) { return memByteBuffer(struct + GLFWGamepadState.BUTTONS, 15); }
    /** Unsafe version of {@link #buttons(int) buttons}. */
    public static byte nbuttons(long struct, int index) {
        return UNSAFE.getByte(null, struct + GLFWGamepadState.BUTTONS + check(index, 15) * 1);
    }
    /** Unsafe version of {@link #axes() axes}. */
    public static FloatBuffer naxes(long struct) { return memFloatBuffer(struct + GLFWGamepadState.AXES, 6); }
    /** Unsafe version of {@link #axes(int) axes}. */
    public static float naxes(long struct, int index) {
        return UNSAFE.getFloat(null, struct + GLFWGamepadState.AXES + check(index, 6) * 4);
    }

    // -----------------------------------

    /** An array of {@link GLFWGamepadState} structs. */
    public static class Buffer extends StructBuffer<GLFWGamepadState, Buffer> implements NativeResource {

        private static final GLFWGamepadState ELEMENT_FACTORY = GLFWGamepadState.create(-1L);

        /**
         * Creates a new {@code GLFWGamepadState.Buffer} instance backed by the specified container.
         *
         * <p>Changes to the container's content will be visible to the struct buffer instance and vice versa. The two buffers' position, limit, and mark values
         * will be independent. The new buffer's position will be zero, its capacity and its limit will be the number of bytes remaining in this buffer divided
         * by {@link GLFWGamepadState#SIZEOF}, and its mark will be undefined.</p>
         *
         * <p>The created buffer instance holds a strong reference to the container object.</p>
         */
        public Buffer(ByteBuffer container) {
            super(container, container.remaining() / SIZEOF);
        }

        public Buffer(long address, int cap) {
            super(address, null, -1, 0, cap, cap);
        }

        Buffer(long address, @Nullable ByteBuffer container, int mark, int pos, int lim, int cap) {
            super(address, container, mark, pos, lim, cap);
        }

        @Override
        protected Buffer self() {
            return this;
        }

        @Override
        protected GLFWGamepadState getElementFactory() {
            return ELEMENT_FACTORY;
        }

        /** @return a {@link ByteBuffer} view of the {@code buttons} field. */
        @NativeType("unsigned char[15]")
        public ByteBuffer buttons() { return GLFWGamepadState.nbuttons(address()); }
        /** @return the value at the specified index of the {@code buttons} field. */
        @NativeType("unsigned char")
        public byte buttons(int index) { return GLFWGamepadState.nbuttons(address(), index); }
        /** @return a {@link FloatBuffer} view of the {@code axes} field. */
        @NativeType("float[6]")
        public FloatBuffer axes() { return GLFWGamepadState.naxes(address()); }
        /** @return the value at the specified index of the {@code axes} field. */
        public float axes(int index) { return GLFWGamepadState.naxes(address(), index); }

    }

}

