package com.nida.kstudio

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Retains a build across Activity recreation; pending run IDs also survive process death. */
object RunSession {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cancelled = AtomicBoolean(false)
    @Volatile var state = RunState()
        private set
    var observer: ((RunState) -> Unit)? = null

    @Synchronized fun start(context: Context, tree: Uri?, resume: String? = null) {
        if (state.busy) return
        cancelled.set(false)
        update(RunState("実行を開始中", busy = true))
        val app = context.applicationContext
        executor.execute {
            try {
                val token = RunSettings(app).readToken()
                require(token.isNotBlank()) { "GitHubトークンを設定してください。" }
                GitHubRunClient(app, token, {
                    if (cancelled.get()) throw CancellationException()
                }, ::update).run(tree, resume)
            } catch (e: Exception) {
                update(RunState("実行できませんでした", e.message.orEmpty()))
            }
        }
    }
    fun cancel() { cancelled.set(true) }
    private fun update(next: RunState) {
        state = next
        main.post { observer?.invoke(state) }
    }
}
