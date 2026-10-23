package com.nida.kstudio.compiler;

import java.lang.reflect.*;
import java.util.*;

/** Optional compiler telemetry on ART, which has no java.management module. */
public final class CompilerCompat {
    private CompilerCompat() {}
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
