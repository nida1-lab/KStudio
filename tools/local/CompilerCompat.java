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
    public static Method method(Class<?> owner, String name, Class<?>[] parameters) throws NoSuchMethodException {
        try { return owner.getMethod(name,parameters); }
        catch (NoSuchMethodException absent) {
            // IntelliJ probes HotSpot-only copyMemory during class initialization.
            // The CLI compiler never uses that native-memory path. Retain a
            // non-null handle; an attempted invocation will fail rather than
            // perform an unsafe memory operation.
            if (name.equals("copyMemory")) return Object.class.getMethod("toString");
            throw absent;
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
