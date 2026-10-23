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
            checkEncryptedToken()
            checkDocumentSnapshot()
            checkApkProvider()
            checkRunSettingsScreen()
            result.putString("stream", "KSTUDIO_DEVICE_CHECKS_OK: Keystore, recursive SAF snapshot, APK provider, Run settings UI\n")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("stream", failure.stackTraceToString())
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun checkEncryptedToken() {
        val settings = RunSettings(targetContext)
        val previous = settings.readToken()
        val token = "device-test-${UUID.randomUUID()}"
        try {
            settings.saveToken(token)
            check(settings.readToken() == token)
            val encoded = targetContext.getSharedPreferences("remote_build", 0).getString("token", "")!!
            check(!encoded.contains(token))
        } finally {
            if (previous.isEmpty()) settings.clearToken() else settings.saveToken(previous)
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

    private fun checkRunSettingsScreen() {
        val activity = startActivitySync(Intent(targetContext, RunActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            waitForIdleSync()
            runOnMainSync {
                val root = activity.window.decorView
                findText(root, "GitHub接続設定")!!.performClick()
                check(findText(root, "保存してRunへ戻る") != null)
                check(findText(root, "Run時にプロジェクトをこのGitHubリポジトリへ送信する") != null)
            }
        } finally { runOnMainSync { activity.finish() } }
    }

    private fun findText(view: View, text: String): TextView? {
        if (view is TextView && view.text.toString() == text) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findText(view.getChildAt(index), text)?.let { return it }
        }
        return null
    }
}
