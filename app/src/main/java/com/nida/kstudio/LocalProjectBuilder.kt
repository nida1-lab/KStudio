package com.nida.kstudio

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import org.w3c.dom.Element
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.security.auth.x500.X500Principal
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class LocalBuildException(message: String) : Exception(message)

class LocalProjectBuilder(private val context: Context) {
    private val toolchain = LocalToolchain(context)
    private val androidNS = "http://schemas.android.com/apk/res/android"

    fun build(project: File, resultDir: File, checkCancelled: () -> Unit = {}, status: (String, String) -> Unit = { _, _ -> }): File {
        resultDir.mkdirs()
        val log = File(resultDir, "build.log")
        fun report(title: String, detail: String = "") {
            checkCancelled()
            if (detail.isNotBlank()) log.appendText(detail + "\n")
            status(title, if (log.isFile) log.readText().takeLast(60000) else "")
        }
        try {
            report("端末内のビルドツールを準備中")
            toolchain.prepare(checkCancelled)
            val main = listOf(File(project, "app/src/main"), File(project, "src/main")).firstOrNull { it.isDirectory } ?: project
            val ignored = setOf("build", ".gradle", ".git", ".idea", "node_modules", "androidTest", "test")
            val sources = main.walkTopDown().onEnter { it == main || it.name !in ignored }.filter { it.isFile && it.extension == "kt" }.toMutableList()
            require(sources.isNotEmpty()) { "Kotlinファイルがありません。MainActivity.kt または fun main() を用意してください。" }
            require(main.walkTopDown().onEnter { it.name !in ignored }.none { it.isFile && it.extension == "java" }) { "端末内ビルドは現在Kotlinに対応しています。Javaソースの混在には未対応です。" }
            val activity = sources.firstOrNull { it.name == "MainActivity.kt" }
            val console = if (activity == null) sources.firstOrNull { Regex("\\bfun\\s+main\\s*\\(").containsMatchIn(it.readText()) } else null
            require(activity != null || console != null) { "MainActivity.kt またはトップレベルの fun main() が必要です。" }
            val entry = activity ?: console!!
            val declaredPackage = Regex("(?m)^\\s*package\\s+([A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*)*)").find(entry.readText())?.groupValues?.get(1).orEmpty()
            require(activity == null || declaredPackage.isNotBlank()) { "MainActivity.kt に package 宣言を追加してください。" }
            val namespace = declaredPackage.ifBlank { "com.kstudio.project" }
            require(namespace != context.packageName) { "KStudio自身と異なるpackage名を指定してください。" }
            val generated = File(resultDir, "generated").apply { mkdirs() }
            var launchName = "$namespace.MainActivity"
            if (console != null) {
                launchName = "$namespace.KStudioConsoleActivity"
                val hasArgs = Regex("fun\\s+main\\s*\\(\\s*\\w+\\s*:\\s*Array").containsMatchIn(console.readText())
                val wrapper = File(generated, "KStudioConsoleActivity.kt")
                wrapper.writeText(consoleSource(namespace, declaredPackage.isEmpty(), hasArgs))
                sources.add(wrapper)
            }
            val manifestFile = File(resultDir, "AndroidManifest.xml")
            prepareManifest(main, manifestFile, namespace, launchName)
            val resources = File(main, "res")
            val compiled = File(resultDir, "resources.zip")
            if (resources.isDirectory && resources.walkTopDown().any { it.isFile }) {
                report("Androidリソースをコンパイル中")
                execute(listOf(toolchain.aapt2.absolutePath, "compile", "--dir", resources.absolutePath, "-o", compiled.absolutePath), resultDir, checkCancelled, log)
            }
            val packagedResources = File(resultDir, "resources.ap_")
            val symbols = File(resultDir, "symbols").apply { mkdirs() }
            val link = mutableListOf(toolchain.aapt2.absolutePath, "link", "-o", packagedResources.absolutePath,
                "--manifest", manifestFile.absolutePath, "-I", toolchain.androidJar.absolutePath,
                "--min-sdk-version", "26", "--target-sdk-version", "35", "--auto-add-overlay", "--output-text-symbols", symbols.absolutePath)
            if (compiled.isFile) link.addAll(listOf("-R", compiled.absolutePath))
            File(main, "assets").takeIf { it.isDirectory }?.let { link.addAll(listOf("-A", it.absolutePath)) }
            report("Androidリソースをリンク中")
            execute(link, resultDir, checkCancelled, log)
            File(symbols, "R.txt").takeIf { it.isFile }?.let { table ->
                val resourceClass = File(generated, "R.kt")
                resourceClass.writeText(resourceSource(namespace, table.readLines()))
                sources.add(resourceClass)
            }
            val libraries = File(project, "libs").takeIf { it.isDirectory }?.listFiles()?.filter { it.extension == "jar" }.orEmpty()
            val classes = File(resultDir, "classes").apply { mkdirs() }
            report("Kotlinを端末内でコンパイル中", "${sources.size} Kotlin files / ローカルコンパイラ 2.2.10")
            val compilerOutput = toolchain.compile(sources, libraries, classes)
            report("Android用コードへ変換中", compilerOutput)
            val bytecode = File(resultDir, "classes.jar")
            ZipOutputStream(bytecode.outputStream().buffered()).use { zip ->
                classes.walkTopDown().filter { it.isFile }.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.relativeTo(classes).invariantSeparatorsPath)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
            val dex = File(resultDir, "dex").apply { mkdirs() }
            D8.run(D8Command.builder().addProgramFiles((listOf(bytecode, toolchain.stdlib) + libraries).map { it.toPath() })
                .addLibraryFiles(toolchain.androidJar.toPath()).setMinApiLevel(26).setMode(CompilationMode.RELEASE)
                .setOutput(dex.toPath(), OutputMode.DexIndexed).build())
            report("APKを作成中")
            val unsigned = File(resultDir, "unsigned.apk")
            ZipOutputStream(unsigned.outputStream().buffered()).use { zip ->
                ZipFile(packagedResources).use { base ->
                    val entries = base.entries()
                    while (entries.hasMoreElements()) {
                        val entryZip = entries.nextElement()
                        if (entryZip.isDirectory) continue
                        zip.putNextEntry(ZipEntry(entryZip.name)); base.getInputStream(entryZip).use { it.copyTo(zip) }; zip.closeEntry()
                    }
                }
                dex.listFiles()!!.filter { it.extension == "dex" }.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
            report("端末の鍵でAPKに署名中")
            val apk = File(resultDir, "project.apk")
            val store = signingStore()
            val key = store.getKey("kstudio.local.debug", null) as PrivateKey
            val certificate = store.getCertificate("kstudio.local.debug") as X509Certificate
            ApkSigner.Builder(listOf(ApkSigner.SignerConfig.Builder("KStudio", key, listOf(certificate)).build()))
                .setInputApk(unsigned).setOutputApk(apk).setMinSdkVersion(26)
                .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(false).build().sign()
            checkCancelled()
            val info = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            require(info != null && info.packageName != context.packageName) { "生成したAPKのパッケージを確認できません。" }
            report("ビルド成功", "端末内でコンパイル・リソース生成・DEX変換・APK署名が完了しました。\n${apk.length()} bytes")
            return apk
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Throwable) {
            log.appendText("\n" + (failure.message ?: failure.javaClass.simpleName) + "\n")
            throw LocalBuildException(log.readText().takeLast(100000))
        }
    }

    private fun execute(command: List<String>, directory: File, cancelled: () -> Unit, log: File) {
        val process = ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log)).start()
        try {
            val deadline = System.currentTimeMillis() + 90000
            while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                cancelled()
                require(System.currentTimeMillis() < deadline) { "リソース処理がタイムアウトしました。" }
            }
            require(process.exitValue() == 0) { "リソース処理に失敗しました。" }
        } finally { process.destroy() }
    }

    private fun signingStore(): KeyStore {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias("kstudio.local.debug")) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            generator.initialize(KeyGenParameterSpec.Builder("kstudio.local.debug", KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(X500Principal("CN=KStudio Local Debug"))
                .setCertificateSerialNumber(java.math.BigInteger.ONE)
                .setCertificateNotBefore(Date(0)).setCertificateNotAfter(Date(4102444800000L)).build())
            generator.generateKeyPair()
        }
        return store
    }

    private fun prepareManifest(main: File, destination: File, namespace: String, launch: String) {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        val parser = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) }
        }
        val source = File(main, "AndroidManifest.xml")
        val document = if (source.isFile) parser.parse(source) else parser.newDocument().apply { appendChild(createElement("manifest")) }
        val root = document.documentElement
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:android", androidNS)
        root.setAttribute("package", namespace)
        if (!root.hasAttributeNS(androidNS, "versionCode")) root.setAttributeNS(androidNS, "android:versionCode", "1")
        if (!root.hasAttributeNS(androidNS, "versionName")) root.setAttributeNS(androidNS, "android:versionName", "1.0")
        var app = root.getElementsByTagName("application").item(0) as? Element
        if (app == null) { app = document.createElement("application"); root.appendChild(app) }
        if (!app.hasAttributeNS(androidNS, "label")) app.setAttributeNS(androidNS, "android:label", "KStudio Run")
        if (!app.hasAttributeNS(androidNS, "theme")) app.setAttributeNS(androidNS, "android:theme", "@android:style/Theme.Material.Light.NoActionBar")
        app.setAttributeNS(androidNS, "android:debuggable", "true")
        val activities = app.getElementsByTagName("activity")
        var target: Element? = null
        for (i in 0 until activities.length) {
            val item = activities.item(i) as Element
            val name = item.getAttributeNS(androidNS, "name")
            if (name == launch || name == ".${launch.substringAfterLast('.')}") target = item
        }
        if (target == null) { target = document.createElement("activity"); target.setAttributeNS(androidNS, "android:name", launch); app.appendChild(target) }
        target.setAttributeNS(androidNS, "android:exported", "true")
        if (target.getElementsByTagName("intent-filter").length == 0) {
            val filter = document.createElement("intent-filter")
            for ((tag, value) in listOf("action" to "android.intent.action.MAIN", "category" to "android.intent.category.LAUNCHER")) {
                val node = document.createElement(tag); node.setAttributeNS(androidNS, "android:name", value); filter.appendChild(node)
            }
            target.appendChild(filter)
        }
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(destination))
    }

    private fun resourceSource(namespace: String, symbols: List<String>): String {
        val groups = linkedMapOf<String, MutableList<String>>()
        symbols.forEach { line ->
            val match = Regex("^(int(?:\\[\\])?) (\\w+) (\\w+) (.*)$").find(line) ?: return@forEach
            val (type, group, name, value) = match.destructured
            val declaration = if (type == "int[]") "val `$name` = intArrayOf(${value.removePrefix("{").removeSuffix("}").trim()})"
                else "const val `$name`: Int = $value"
            groups.getOrPut(group) { mutableListOf() }.add(declaration)
        }
        return "package $namespace\nobject R {\n" + groups.entries.joinToString("\n") { (group, values) -> "object `$group` {\n${values.joinToString("\n")}\n}" } + "\n}\n"
    }

    private fun consoleSource(namespace: String, defaultPackage: Boolean, args: Boolean): String = """
        package $namespace
        ${if (defaultPackage) "import main" else ""}
        import android.app.Activity
        import android.os.Bundle
        import android.widget.TextView
        import android.widget.ScrollView
        import java.io.OutputStream
        import java.io.PrintStream
        class KStudioConsoleActivity : Activity() {
            override fun onCreate(state: Bundle?) {
                super.onCreate(state)
                val text = TextView(this).apply { textSize = 16f; setPadding(24, 32, 24, 24) }
                setContentView(ScrollView(this).apply { addView(text) })
                val output = StringBuilder()
                val stream = object : OutputStream() {
                    override fun write(value: Int) { write(byteArrayOf(value.toByte()), 0, 1) }
                    override fun write(bytes: ByteArray, offset: Int, count: Int) {
                        synchronized(output) {
                            output.append(String(bytes, offset, count, Charsets.UTF_8))
                            if (output.length > 200000) output.delete(0, output.length - 200000)
                        }
                        runOnUiThread { text.text = synchronized(output) { output.toString() } }
                    }
                }
                System.setOut(PrintStream(stream, true, "UTF-8")); System.setErr(PrintStream(stream, true, "UTF-8"))
                Thread { try { ${if (args) "main(emptyArray<String>())" else "main()"} } catch (failure: Throwable) { failure.printStackTrace() } }.start()
            }
        }
    """.trimIndent()
}
