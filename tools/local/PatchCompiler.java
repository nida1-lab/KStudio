/* KStudio compiler adaptation. Uses ASM (BSD-3-Clause) and Kotlin (Apache-2.0). */
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class PatchCompiler {
    static final String OLD = "java/lang/management/";
    static final String NEW = "com/nida/kstudio/compiler/management/";
    static final String COMPAT = "com/nida/kstudio/compiler/CompilerCompat";
    static final Map<String, Boolean> interfaces = new TreeMap<>();
    static final Map<String, Map<String, Integer>> methods = new TreeMap<>();
    static final Map<String, Map<String, String>> fields = new TreeMap<>();
    static String mapped(String name) {
        if (name.startsWith(OLD)) {
            interfaces.putIfAbsent(name, false);
            return NEW + name.substring(OLD.length());
        }
        return name;
    }
    static final Remapper REMAPPER = new Remapper() {
        @Override public String map(String name) { return mapped(name); }
    };

    public static void main(String[] args) throws Exception {
        Path input = Paths.get(args[0]), output = Paths.get(args[1]), shims = Paths.get(args[2]);
        Files.createDirectories(output);
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(input, "*.jar")) {
            for (Path jar : jars) {
                try (ZipFile source = new ZipFile(jar.toFile()); ZipOutputStream dest = new ZipOutputStream(Files.newOutputStream(output.resolve(jar.getFileName())))) {
                    Enumeration<? extends ZipEntry> entries = source.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (entry.isDirectory()) continue;
                        byte[] data;
                        try (InputStream stream = source.getInputStream(entry)) { data = read(stream); }
                        if (entry.getName().endsWith(".class")) data = transform(data);
                        dest.putNextEntry(new ZipEntry(entry.getName())); dest.write(data); dest.closeEntry();
                    }
                }
            }
        }
        for (String owner : new ArrayList<>(interfaces.keySet())) emit(owner, shims);
        System.out.println("Generated " + interfaces.size() + " compiler telemetry adapters");
    }
    static byte[] transform(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(0);
        ClassVisitor mappedVisitor = new ClassRemapper(writer, REMAPPER);
        reader.accept(new ClassVisitor(Opcodes.ASM9, mappedVisitor) {
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(a,n,d,s,ex)) {
                    @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        if (owner.startsWith(OLD)) {
                            interfaces.put(owner, itf || interfaces.getOrDefault(owner, false));
                            methods.computeIfAbsent(owner, k -> new TreeMap<>()).put(name + desc, op);
                        }
                        if (reader.getClassName().equals("org/jetbrains/kotlin/com/intellij/util/containers/Unsafe") &&
                            owner.equals("java/lang/Class") && name.equals("getMethod")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, COMPAT, "method",
                                "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
                        } else if (reader.getClassName().equals("org/jetbrains/kotlin/com/intellij/util/containers/Unsafe") &&
                            owner.equals("java/lang/invoke/MethodHandles$Lookup") && name.equals("findVirtual")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, COMPAT, "findVirtual",
                                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
                        } else super.visitMethodInsn(op,owner,name,desc,itf);
                    }
                    @Override public void visitFieldInsn(int op, String owner, String name, String desc) {
                        if (owner.startsWith(OLD)) {
                            mapped(owner);
                            fields.computeIfAbsent(owner,k -> new TreeMap<>()).put(name, desc);
                        }
                        super.visitFieldInsn(op,owner,name,desc);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }
    static void emit(String original, Path directory) throws Exception {
        String name = mapped(original);
        boolean itf = interfaces.get(original);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | (itf ? Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT : Opcodes.ACC_SUPER), name, null, "java/lang/Object", null);
        if (!itf) constructor(cw, "()V");
        for (Map.Entry<String,String> f : fields.getOrDefault(original, Collections.emptyMap()).entrySet())
            cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, f.getKey(), REMAPPER.mapDesc(f.getValue()), null, null).visitEnd();
        for (Map.Entry<String,Integer> method : methods.getOrDefault(original, Collections.emptyMap()).entrySet()) {
            String spec = method.getKey(), mn = spec.substring(0, spec.indexOf('('));
            String md = REMAPPER.mapMethodDesc(spec.substring(spec.indexOf('(')));
            if (mn.equals("<init>")) { if (!md.equals("()V")) constructor(cw, md); continue; }
            boolean stat = method.getValue() == Opcodes.INVOKESTATIC;
            int flags = Opcodes.ACC_PUBLIC | (stat ? Opcodes.ACC_STATIC : 0);
            if (itf && !stat) { cw.visitMethod(flags | Opcodes.ACC_ABSTRACT,mn,md,null,null).visitEnd(); continue; }
            MethodVisitor mv = cw.visitMethod(flags,mn,md,null,null); mv.visitCode();
            Type result = Type.getReturnType(md);
            if (result.getSort() == Type.VOID) mv.visitInsn(Opcodes.RETURN);
            else {
                if (result.getSort() <= Type.DOUBLE) {
                    String box = box(result);
                    mv.visitFieldInsn(Opcodes.GETSTATIC, box, "TYPE", "Ljava/lang/Class;");
                } else mv.visitLdcInsn(result);
                mv.visitLdcInsn(mn);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, COMPAT, "value", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Object;", false);
                if (result.getSort() <= Type.DOUBLE) {
                    String box = box(result); mv.visitTypeInsn(Opcodes.CHECKCAST,box);
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,box,result.getClassName()+"Value","()"+result.getDescriptor(),false);
                    mv.visitInsn(result.getOpcode(Opcodes.IRETURN));
                } else { mv.visitTypeInsn(Opcodes.CHECKCAST,result.getInternalName()); mv.visitInsn(Opcodes.ARETURN); }
            }
            mv.visitMaxs(0,0); mv.visitEnd();
        }
        cw.visitEnd();
        Path file = directory.resolve(name+".class"); Files.createDirectories(file.getParent()); Files.write(file,cw.toByteArray());
    }
    static String box(Type t) {
        switch(t.getSort()) {
            case Type.BOOLEAN:return "java/lang/Boolean"; case Type.BYTE:return "java/lang/Byte";
            case Type.CHAR:return "java/lang/Character"; case Type.SHORT:return "java/lang/Short";
            case Type.INT:return "java/lang/Integer"; case Type.LONG:return "java/lang/Long";
            case Type.FLOAT:return "java/lang/Float"; default:return "java/lang/Double";
        }
    }
    static void constructor(ClassWriter cw, String desc) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC,"<init>",desc,null,null); mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD,0); mv.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
        mv.visitInsn(Opcodes.RETURN); mv.visitMaxs(0,0); mv.visitEnd();
    }
    static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer=new byte[16384];
        for(int n;(n=input.read(buffer))!=-1;) output.write(buffer,0,n); return output.toByteArray();
    }
}
