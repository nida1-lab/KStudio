package com.nida.kstudio.compiler;

import java.lang.reflect.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;

/** Optional compiler telemetry on ART, which has no java.management module. */
public final class CompilerCompat {
    private CompilerCompat() {}
    private static volatile Method intRead;
    private static volatile Method intCompare;
    public static MethodHandle findVirtual(MethodHandles.Lookup lookup, Class<?> owner, String name, MethodType type)
            throws NoSuchMethodException, IllegalAccessException {
        try { return lookup.findVirtual(owner, name, type); }
        catch (NoSuchMethodException absent) {
            if (name.equals("copyMemory") && type.equals(MethodType.methodType(void.class, Object.class, long.class, Object.class, long.class, long.class))) {
                // Resolve this optional ByteBuffer operation lazily, so a JVM
                // startup probe does not require an ART-only memory-copy API.
                return MethodHandles.lookup().findStatic(CompilerCompat.class, "copyMemory",
                    type.insertParameterTypes(0, Object.class)).asType(type.insertParameterTypes(0, owner));
            }
            if (!name.equals("getAndAddInt") || !type.equals(MethodType.methodType(int.class, Object.class, long.class, int.class))) throw absent;
            // ART exposes the primitive volatile read and CAS operations but
            // omits HotSpot's convenience fetch-and-add method. Preserve its
            // atomic semantics with a CAS loop, including the previous value.
            intRead = owner.getMethod("getIntVolatile", Object.class, long.class);
            intCompare = owner.getMethod("compareAndSwapInt", Object.class, long.class, int.class, int.class);
            return MethodHandles.lookup().findStatic(CompilerCompat.class, "getAndAddInt",
                MethodType.methodType(int.class, Object.class, Object.class, long.class, int.class))
                .asType(type.insertParameterTypes(0, owner));
        }
    }
    public static int getAndAddInt(Object unsafe, Object object, long offset, int amount) throws ReflectiveOperationException {
        while (true) {
            int before = (Integer) intRead.invoke(unsafe, object, offset);
            if ((Boolean) intCompare.invoke(unsafe, object, offset, before, before + amount)) return before;
        }
    }
    public static void copyMemory(Object unsafe, Object source, long sourceOffset, Object destination, long destinationOffset, long length)
            throws ReflectiveOperationException {
        if (length < 0) throw new IllegalArgumentException("Negative copy length");
        if (length == 0) return;
        if ((source != null && !(source instanceof byte[])) || (destination != null && !(destination instanceof byte[])))
            throw new UnsupportedOperationException("Compiler memory copying supports native buffers and byte arrays");
        Class<?> owner = unsafe.getClass();
        long base = ((Number) owner.getMethod("arrayBaseOffset", Class.class).invoke(unsafe, byte[].class)).longValue();
        int from = source == null ? 0 : Math.toIntExact(sourceOffset - base);
        int to = destination == null ? 0 : Math.toIntExact(destinationOffset - base);
        if (source != null && destination != null) {
            System.arraycopy(source, from, destination, to, Math.toIntExact(length));
            return;
        }
        if (source != null && (from < 0 || length > ((byte[])source).length - (long)from)) throw new IndexOutOfBoundsException();
        if (destination != null && (to < 0 || length > ((byte[])destination).length - (long)to)) throw new IndexOutOfBoundsException();
        Method read = source == null ? owner.getMethod("getByte", long.class) : null;
        Method write = destination == null ? owner.getMethod("putByte", long.class, byte.class) : null;
        boolean reverse = source == null && destination == null && destinationOffset > sourceOffset && destinationOffset - sourceOffset < length;
        for (long step = 0; step < length; step++) {
            long index = reverse ? length - step - 1 : step;
            byte value = source == null ? (Byte)read.invoke(unsafe, sourceOffset + index) : ((byte[])source)[Math.toIntExact(from + index)];
            if (destination == null) write.invoke(unsafe, destinationOffset + index, value);
            else ((byte[])destination)[Math.toIntExact(to + index)] = value;
        }
    }
    public static Object value(Class<?> type, String name) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte)0;
        if (type == short.class) return (short)0;
        if (type == char.class) return (char)0;
        if (type == int.class) return name.equals("getAvailableProcessors") ? Runtime.getRuntime().availableProcessors() : 0;
        if (type == long.class) {
            Runtime runtime = Runtime.getRuntime();
            if (name.equals("getMax")) return runtime.maxMemory();
            if (name.equals("getCommitted")) return runtime.totalMemory();
            if (name.equals("getUsed")) return runtime.totalMemory()-runtime.freeMemory();
            if (name.equals("getUptime")) return System.nanoTime()/1000000;
            return 0L;
        }
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == void.class) return null;
        if (type == String.class) return "KStudio";
        if (List.class.isAssignableFrom(type)) return Collections.emptyList();
        if (Map.class.isAssignableFrom(type)) return Collections.emptyMap();
        if (type.isArray()) return Array.newInstance(type.getComponentType(),0);
        if (type.isEnum()) return type.getEnumConstants()[0];
        if (type.isInterface()) return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
            if (method.getName().equals("equals")) return proxy == args[0];
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            return value(method.getReturnType(),method.getName());
        });
        try { return type.getDeclaredConstructor().newInstance(); } catch (ReflectiveOperationException unavailable) { return null; }
    }
}
