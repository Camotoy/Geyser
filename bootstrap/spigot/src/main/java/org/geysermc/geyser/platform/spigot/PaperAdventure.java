/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.platform.spigot;

import com.github.steveice10.mc.protocol.data.DefaultComponentSerializer;
import net.kyori.adventure.text.Component;
import org.geysermc.geyser.GeyserImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

public final class PaperAdventure {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final Class<?> NATIVE_GSON_COMPONENT_SERIALIZER_CLASS = findClass(String.join(".", "net", "kyori", "adventure", "text", "serializer", "gson", "GsonComponentSerializer"));
    private static final Class<?> NATIVE_GSON_COMPONENT_SERIALIZER_IMPL_CLASS = findClass(String.join(".", "net", "kyori", "adventure", "text", "serializer", "gson", "GsonComponentSerializerImpl"));
    private static final MethodHandle NATIVE_GSON_COMPONENT_SERIALIZER_GSON_GETTER = findStaticMethod(NATIVE_GSON_COMPONENT_SERIALIZER_CLASS, "gson", NATIVE_GSON_COMPONENT_SERIALIZER_CLASS);
    private static final MethodHandle NATIVE_GSON_COMPONENT_SERIALIZER_DESERIALIZE_METHOD = findNativeDeserializeMethod();
    private static final MethodHandle NATIVE_GSON_COMPONENT_SERIALIZER_DESERIALIZE_METHOD_BOUND = createBoundNativeDeserializeMethodHandle();

    public static Object toNativeComponent(Component component) {
        try {
            return NATIVE_GSON_COMPONENT_SERIALIZER_DESERIALIZE_METHOD_BOUND.invoke(DefaultComponentSerializer.get().serialize(component));
        } catch (final Throwable throwable) {
            GeyserImpl.getInstance().getLogger().error("Failed to create native Component message", throwable);
            return null;
        }
    }

    private static @Nullable MethodHandle findNativeDeserializeMethod() {
        if (NATIVE_GSON_COMPONENT_SERIALIZER_IMPL_CLASS == null) {
            return null;
        }

        try {
            final Method method = NATIVE_GSON_COMPONENT_SERIALIZER_IMPL_CLASS.getDeclaredMethod("deserialize", String.class);
            method.setAccessible(true);
            return lookup().unreflect(method);
        } catch (final NoSuchMethodException | IllegalAccessException e) {
            return null;
        }
    }

    private static @Nullable MethodHandle createBoundNativeDeserializeMethodHandle() {
        if (NATIVE_GSON_COMPONENT_SERIALIZER_DESERIALIZE_METHOD != null && NATIVE_GSON_COMPONENT_SERIALIZER_GSON_GETTER != null) {
            try {
                return NATIVE_GSON_COMPONENT_SERIALIZER_DESERIALIZE_METHOD.bindTo(NATIVE_GSON_COMPONENT_SERIALIZER_GSON_GETTER.invoke());
            } catch (final Throwable throwable) {
                GeyserImpl.getInstance().getLogger().error("Failed to access native GsonComponentSerializer", throwable);
                return null;
            }
        }
        return null;
    }

    /**
     * Gets a class by the first name available.
     *
     * @return a class or {@code null} if not found
     */
    public static @Nullable Class<?> findClass(final String className) {
        try {
            return Class.forName(className);
        } catch (final ClassNotFoundException ignored) {
        }
        return null;
    }

    /**
     * Gets a handle for a class method.
     *
     * @param holderClass a class
     * @param methodNames a method name
     * @param returnClass a method return class
     * @param parameterClasses an array of method parameter classes
     * @return a method handle or {@code null} if not found
     */
    public static @Nullable MethodHandle findStaticMethod(final @Nullable Class<?> holderClass, final String methodNames, final @Nullable Class<?> returnClass, final Class<?>... parameterClasses) {
        return findStaticMethod(holderClass, new String[]{methodNames}, returnClass, parameterClasses);

    }

    /**
     * Gets a handle for a class method.
     *
     * @param holderClass a class
     * @param methodNames a method name
     * @param returnClass a method return class
     * @param parameterClasses an array of method parameter classes
     * @return a method handle or {@code null} if not found
     */
    public static @Nullable MethodHandle findStaticMethod(final @Nullable Class<?> holderClass, final String[] methodNames, final @Nullable Class<?> returnClass, final Class<?>... parameterClasses) {
        if (holderClass == null || returnClass == null) return null;
        for (final Class<?> parameterClass : parameterClasses) {
            if (parameterClass == null) return null;
        }

        for (final String methodName : methodNames) {
            try {
                return LOOKUP.findStatic(holderClass, methodName, MethodType.methodType(returnClass, parameterClasses));
            } catch (final NoSuchMethodException | IllegalAccessException e) {
            }
        }

        return null;
    }

    /**
     * Gets the singleton method handle lookup.
     *
     * @return the method handle lookup
     */
    public static MethodHandles.@NotNull Lookup lookup() {
        return LOOKUP;
    }

    private PaperAdventure() {
    }
}
