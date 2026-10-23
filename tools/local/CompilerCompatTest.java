import com.nida.kstudio.compiler.CompilerCompat;
import java.lang.invoke.*;
import java.util.*;
import java.util.concurrent.*;

/** Checks the ART fallback's old-value and concurrent atomic-update contract. */
public class CompilerCompatTest {
    public static class PrimitiveAtomic {
        private int value;
        public final byte[] memory = new byte[32];
        public int arrayBaseOffset(Class<?> array) { return 16; }
        public byte getByte(long address) { return memory[Math.toIntExact(address)]; }
        public void putByte(long address, byte value) { memory[Math.toIntExact(address)] = value; }
        public synchronized int getIntVolatile(Object target, long offset) { return value; }
        public synchronized boolean compareAndSwapInt(Object target, long offset, int before, int after) {
            if (value != before) return false;
            value = after; return true;
        }
    }
    public static void main(String[] args) throws Throwable {
        PrimitiveAtomic primitive = new PrimitiveAtomic();
        MethodHandle add = CompilerCompat.findVirtual(MethodHandles.publicLookup(), PrimitiveAtomic.class, "getAndAddInt",
            MethodType.methodType(int.class, Object.class, long.class, int.class)).bindTo(primitive);
        Set<Integer> previous = ConcurrentHashMap.newKeySet();
        ExecutorService threads = Executors.newFixedThreadPool(4);
        List<Future<?>> pending = new ArrayList<>();
        for (int worker = 0; worker < 4; worker++) pending.add(threads.submit(() -> {
            try {
                for (int i = 0; i < 1000; i++) {
                    int old = (int) add.invokeExact((Object)primitive, 0L, 1);
                    if (!previous.add(old)) throw new AssertionError("Lost atomic update");
                }
            } catch (Throwable error) { throw new RuntimeException(error); }
        }));
        threads.shutdown();
        for (Future<?> future : pending) future.get();
        if (primitive.value != 4000 || previous.size() != 4000 || !previous.contains(0) || !previous.contains(3999))
            throw new AssertionError("Fetch-and-add contract failed");
        MethodHandle copy = CompilerCompat.findVirtual(MethodHandles.publicLookup(), PrimitiveAtomic.class, "copyMemory",
            MethodType.methodType(void.class, Object.class, long.class, Object.class, long.class, long.class)).bindTo(primitive);
        byte[] source = {10, 20, 30, 40}, result = new byte[4];
        copy.invokeExact((Object)source, 16L, (Object)result, 16L, 4L);
        if (!Arrays.equals(source, result)) throw new AssertionError("Array copy failed");
        copy.invokeExact((Object)source, 16L, (Object)null, 0L, 4L);
        Arrays.fill(result, (byte)0);
        copy.invokeExact((Object)null, 0L, (Object)result, 16L, 4L);
        if (!Arrays.equals(source, result)) throw new AssertionError("Native buffer copy failed");
        copy.invokeExact((Object)null, 0L, (Object)null, 1L, 4L);
        if (primitive.memory[1] != 10 || primitive.memory[4] != 40) throw new AssertionError("Overlapping copy failed");
        System.out.println("Compiler ART atomic and buffer fallbacks verified");
    }
}
