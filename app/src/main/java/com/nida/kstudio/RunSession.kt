package com.nida.kstudio

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

data class RunState(
    val title: String = "端末内で実行",
    val log: String = "",
    val busy: Boolean = false,
    val apk: File? = null,
    val diagnostics: List<BuildDiagnostic> = emptyList()
)

/** The Run Activity and compiler live in :run, separate from the editor process. */
object RunSession {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cancelled = AtomicBoolean(false)
    private var builder: LocalProjectBuilder? = null
    @Volatile var state = RunState()
        private set
    var observer: ((RunState) -> Unit)? = null

    @Synchronized fun start(context: Context, tree: Uri?) {
        if (state.busy) return
        cancelled.set(false)
        update(RunState("プロジェクトを取り込み中", busy = true))
        val app = context.applicationContext
        executor.execute {
            val directory = File(app.cacheDir, "run/${UUID.randomUUID()}").apply { mkdirs() }
            val project = File(directory, "kstudio-work").apply { mkdirs() }
            fun checkCancelled() { if (cancelled.get()) throw CancellationException() }
            try {
                require(tree != null) { "先にプロジェクトフォルダを開いてください。" }
                val snapshot = ProjectSnapshot.create(app, tree, ::checkCancelled)
                try {
                    ZipInputStream(snapshot.file.inputStream().buffered()).use { zip ->
                        while (true) {
                            checkCancelled()
                            val entry = zip.nextEntry ?: break
                            val target = File(project, entry.name).canonicalFile
                            require(target.path.startsWith(project.canonicalPath + File.separator)) { "使えないプロジェクトパスです。" }
                            if (entry.isDirectory) target.mkdirs() else {
                                target.parentFile!!.mkdirs()
                                target.outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                } finally { snapshot.file.delete() }
                val engine = builder ?: LocalProjectBuilder(app).also { builder = it }
                val apk = engine.build(project, directory, ::checkCancelled) { title, log -> update(RunState(title, log, true)) }
                update(RunState("端末内ビルド成功", File(directory, "build.log").readText(), apk = apk))
            } catch (_: CancellationException) {
                update(RunState("ビルドを停止しました", "端末内の処理を終了しました。"))
            } catch (failure: Throwable) {
                val log = failure.message ?: failure.javaClass.simpleName
                update(RunState("端末内ビルド失敗", log, diagnostics = BuildDiagnostic.parse(log)))
            } finally {
                directory.listFiles()?.filter { it.name !in setOf("project.apk", "build.log") }?.forEach { it.deleteRecursively() }
                File(app.cacheDir, "run").listFiles()?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach { it.deleteRecursively() }
            }
        }
    }
    fun cancel() { cancelled.set(true) }
    private fun update(next: RunState) {
        state = next
        main.post { observer?.invoke(state) }
    }
}
