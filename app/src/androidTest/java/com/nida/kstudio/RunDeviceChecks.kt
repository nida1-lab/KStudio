package com.nida.kstudio

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

/** Device checks without a test-framework dependency: run by `am instrument`. */
class RunDeviceChecks : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }
    override fun onStart() {
        val result = Bundle()
        try {
            checkDocumentSnapshot()
            checkApkProvider()
            checkLocalRunScreen()
            checkOfflineBuilds()
            result.putString("stream", "KSTUDIO_DEVICE_CHECKS_OK: recursive SAF snapshot, APK provider, local Run UI\nKSTUDIO_OFFLINE_BUILD_OK: counter XML, console, diagnostics, stable device signing\n")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("stream", failure.stackTraceToString())
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun checkDocumentSnapshot() {
        val tree = DocumentsContract.buildTreeDocumentUri(targetContext.packageName + ".snapshot-fixture", "root")
        val snapshot = ProjectSnapshot.create(targetContext, tree) {}
        try {
            check(snapshot.count == 3) { "Expected three portable files, got ${snapshot.count}" }
            ZipFile(snapshot.file).use { zip ->
                check(zip.getEntry("MainActivity.kt") != null)
                check(zip.getEntry("local.properties") == null)
                check(zip.getEntry("build/generated.kt") == null)
                check(zip.getInputStream(zip.getEntry("res/layout/main.xml")).readBytes().toString(Charsets.UTF_8) == "<TextView/>")
                check(zip.getInputStream(zip.getEntry("res/drawable/image.png")).readBytes().contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)))
            }
        } finally { snapshot.file.delete() }
    }

    private fun checkApkProvider() {
        val id = UUID.randomUUID().toString()
        val directory = File(targetContext.cacheDir, "run/$id").apply { mkdirs() }
        val apk = File(directory, "project.apk").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val authority = targetContext.packageName + ".run-apk"
        val uri = Uri.parse("content://$authority/$id/project.apk")
        try {
            check(targetContext.contentResolver.openInputStream(uri)!!.use { it.readBytes().contentEquals(apk.readBytes()) })
            check(runCatching { targetContext.contentResolver.openOutputStream(uri, "wt") }.isFailure)
            check(runCatching { targetContext.contentResolver.openInputStream(Uri.parse("content://$authority/../remote_build.xml")) }.isFailure)
        } finally { directory.deleteRecursively() }
    }

    private fun checkLocalRunScreen() {
        targetContext.startActivity(Intent(targetContext, RunActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var visible = false
        repeat(100) {
            val root = uiAutomation.rootInActiveWindow
            if (root != null && root.findAccessibilityNodeInfosByText("端末内でビルド").isNotEmpty()) visible = true
            if (!visible) Thread.sleep(100)
        }
        check(visible) { "Local Run screen did not open" }
        uiAutomation.executeShellCommand("input keyevent 4").close()
    }

    private fun checkOfflineBuilds() {
        check(targetContext.checkSelfPermission(android.Manifest.permission.INTERNET) == android.content.pm.PackageManager.PERMISSION_DENIED)
        val network = targetContext.getSystemService(android.net.ConnectivityManager::class.java)
        check(network.activeNetwork == null) { "This test must run with Wi-Fi and mobile data disabled" }
        val engine = LocalProjectBuilder(targetContext)
        val tests = File(targetContext.cacheDir, "offline-tests").apply { deleteRecursively(); mkdirs() }
        fun fixture(name: String): File {
            val root = File(tests, "$name/kstudio-work").apply { mkdirs() }
            fun copy(asset: String, destination: File) {
                val children = context.assets.list(asset).orEmpty()
                if (children.isNotEmpty()) { destination.mkdirs(); children.forEach { copy("$asset/$it", File(destination, it)) } }
                else { destination.parentFile!!.mkdirs(); context.assets.open(asset).use { input -> destination.outputStream().use { input.copyTo(it) } } }
            }
            copy(name, root)
            return root
        }
        val counter = fixture("run-counter")
        val first = engine.build(counter, File(tests, "counter-first"))
        first.copyTo(File(targetContext.filesDir, "device-test-counter.apk"), overwrite = true)
        val second = engine.build(counter, File(tests, "counter-second"))
        @Suppress("DEPRECATION")
        fun certificate(apk: File): ByteArray = targetContext.packageManager.getPackageArchiveInfo(apk.path,
            android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)!!.signingInfo!!.apkContentsSigners[0].toByteArray()
        check(certificate(first).contentEquals(certificate(second))) { "Debug signing identity changed between builds" }
        val console = engine.build(fixture("run-console"), File(tests, "console"))
        console.copyTo(File(targetContext.filesDir, "device-test-console.apk"), overwrite = true)
        val invalid = File(tests, "invalid/kstudio-work").apply { mkdirs() }
        File(invalid, "Main.kt").writeText("fun main() { doesNotExist() }")
        val failure = runCatching { engine.build(invalid, File(tests, "invalid-result")) }.exceptionOrNull()
        check(failure is LocalBuildException && failure.message.orEmpty().contains("doesNotExist")) { "The real compiler must report undefined references" }
        check(BuildDiagnostic.parse(failure.message.orEmpty()).any { it.path == "Main.kt" && it.line == 1 })
        check(network.activeNetwork == null) { "Network unexpectedly became available" }
    }
}
