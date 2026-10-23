package com.nida.kstudio

import android.content.Context
import dalvik.system.PathClassLoader
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** Compiler and platform files are installed from this APK; no download path exists. */
class LocalToolchain(private val context: Context) {
    val directory = File(context.noBackupFilesDir, "toolchain-v1")
    val androidJar get() = File(directory, "android.jar")
    val stdlib get() = File(directory, "kotlin-stdlib.jar")
    val annotations get() = File(directory, "annotations.jar")
    val aapt2 get() = File(context.applicationInfo.nativeLibraryDir, "libaapt2.so")
    private var compilerLoader: ClassLoader? = null

    fun prepare(checkCancelled: () -> Unit) {
        require(android.os.Build.VERSION.SDK_INT >= 30) { "端末内ビルドにはAndroid 11以降が必要です。" }
        directory.mkdirs()
        val manifest = context.assets.open("toolchain/manifest.json").bufferedReader().use { JSONObject(it.readText()) }
        for (name in listOf("android.jar", "kotlin-stdlib.jar", "annotations.jar", "kotlinc.jar")) {
            checkCancelled()
            val expected = manifest.getJSONObject(name)
            val target = File(directory, name)
            if (target.isFile && target.length() == expected.getLong("size") && sha256(target) == expected.getString("sha256")) continue
            target.delete()
            val partial = File(directory, "$name.partial").apply { delete() }
            FileOutputStream(partial).use { output ->
                // The file is read-only before code bytes are written (Android 14+ DCL).
                check(partial.setReadOnly()) { "コンパイラを読み取り専用にできません。" }
                context.assets.open("toolchain/$name").use { input ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        checkCancelled()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(sha256(partial) == expected.getString("sha256")) { "同梱ツールのチェックサムが一致しません: $name" }
            check(partial.renameTo(target)) { "ツールの準備に失敗しました: $name" }
        }
        require(aapt2.isFile && aapt2.canExecute()) { "この端末のCPUに対応するリソースコンパイラがありません。対応: arm64 / x86_64" }
    }

    fun compile(sources: List<File>, extraClasspath: List<File>, destination: File): String {
        val loader = compilerLoader ?: PathClassLoader(File(directory, "kotlinc.jar").absolutePath,
            Any::class.java.classLoader).also { compilerLoader = it }
        val home = File(directory, "home").apply { File(this, "lib").mkdirs() }
        val args = listOf("-no-jdk", "-no-stdlib", "-no-reflect", "-kotlin-home", home.absolutePath,
            "-classpath", (listOf(androidJar, stdlib, annotations) + extraClasspath).joinToString(File.pathSeparator) { it.absolutePath },
            "-jvm-target", "1.8", "-module-name", "kstudio_app", "-d", destination.absolutePath) + sources.map { it.absolutePath }
        val output = ByteArrayOutputStream()
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader
        thread.contextClassLoader = loader
        try {
            val cli = loader.loadClass("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
            val compiler = cli.getDeclaredConstructor().newInstance()
            val result = PrintStream(output, true, "UTF-8").use {
                cli.getMethod("exec", PrintStream::class.java, Array<String>::class.java).invoke(compiler, it, args.toTypedArray())
            }
            val text = output.toString("UTF-8")
            if (result.toString() != "OK") throw LocalBuildException(text.ifBlank { "Kotlinコンパイル失敗: $result" })
            return text
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw LocalBuildException(output.toString("UTF-8") + "\n" + (e.targetException?.stackTraceToString() ?: e.stackTraceToString()))
        } finally { thread.contextClassLoader = previous }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
