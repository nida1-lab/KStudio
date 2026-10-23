package com.nida.kstudio

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class RunActivity : Activity() {
    private lateinit var page: LinearLayout
    private lateinit var title: TextView
    private lateinit var details: TextView
    private lateinit var progress: ProgressBar
    private lateinit var actions: LinearLayout
    private var waitingForInstallPermission = false
    private var observer: ((RunState) -> Unit)? = null
    private val dark: Boolean
        get() = when (getSharedPreferences("KStudio", MODE_PRIVATE).getString("theme_mode", "system")) {
            "dark" -> true
            "light" -> false
            else -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
    private val uiTextColor get() = if (dark) Color.WHITE else Color.rgb(25, 28, 34)
    private val uiBackground get() = if (dark) Color.rgb(19, 21, 25) else Color.WHITE
    private val tree get() = intent.getStringExtra("tree_uri")?.let(Uri::parse)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        waitingForInstallPermission = savedInstanceState?.getBoolean("waiting_install", false) ?: false
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
            setBackgroundColor(uiBackground)
        }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(page) })
        window.decorView.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            }
            insets
        }
        button("← エディタに戻る") { finish() }
        title = label("端末内で実行", 24f)
        label("コンパイル・APK生成・署名をこの端末で行います。", 14f)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate = true; visibility = View.GONE }
        page.addView(progress, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(16) })
        actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(actions)
        details = label("", 12f).apply { typeface = android.graphics.Typeface.MONOSPACE; setTextIsSelectable(true) }
        if (RunSession.state.busy || (savedInstanceState != null && RunSession.state.log.isNotBlank())) render(RunSession.state)
        else {
            showReady()
            if (savedInstanceState == null && tree != null) startBuild()
        }
    }
    override fun onStart() {
        super.onStart()
        observer = { render(it) }
        RunSession.observer = observer
        if (RunSession.state.busy) render(RunSession.state)
    }
    override fun onStop() {
        if (RunSession.observer === observer) RunSession.observer = null
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("waiting_install", waitingForInstallPermission)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() {
        super.onResume()
        if (waitingForInstallPermission) {
            waitingForInstallPermission = false
            if (packageManager.canRequestPackageInstalls()) installApk()
            else details.text = "インストール許可が必要です。「APKをインストール」から再試行できます。"
        }
    }
    private fun showReady() {
        actions.removeAllViews()
        button("端末内でビルド", actions) { startBuild() }
        details.text = "Kotlin / Android View / res / assets / libs内のJARに対応。\n\nコンパイラは同梱済みです。通信・アカウント設定は不要です。\n初回はツールの展開に時間がかかります。"
    }
    private fun startBuild() {
        if (tree == null) { details.text = "プロジェクトフォルダを開いてから実行してください。"; return }
        RunSession.start(this, tree)
        render(RunSession.state)
    }
    private fun render(state: RunState) {
        title.text = state.title
        progress.visibility = if (state.busy) View.VISIBLE else View.GONE
        details.text = state.log.takeLast(60000)
        actions.removeAllViews()
        if (state.busy) button("ビルドを停止", actions) { RunSession.cancel(); title.text = "停止を待っています" }
        else {
            if (state.apk?.isFile == true) {
                button("APKをインストール", actions) { installApk() }
                button("インストール済みアプリを起動", actions) { launchApk(state.apk) }
            }
            state.diagnostics.take(8).forEach { error ->
                button("${error.path.substringAfterLast('/')}:${error.line} を開く", actions) {
                    setResult(RESULT_OK, Intent().putExtra("error_path", error.path).putExtra("error_line", error.line))
                    finish()
                }
            }
            button("もう一度ビルド", actions) { startBuild() }
            button("ログをコピー", actions) {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("KStudio build log", state.log))
            }
        }
    }

    private fun installApk() {
        val file = RunSession.state.apk ?: return
        try {
            if (!packageManager.canRequestPackageInstalls()) {
                waitingForInstallPermission = true
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                return
            }
            val uri = Uri.Builder().scheme("content").authority("$packageName.run-apk")
                .appendPath(file.parentFile!!.name).appendPath(file.name).build()
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: Exception) { details.text = "インストーラーを開けません: ${e.message}" }
    }

    private fun launchApk(file: File) {
        try {
            @Suppress("DEPRECATION")
            val info = packageManager.getPackageArchiveInfo(file.absolutePath, android.content.pm.PackageManager.GET_ACTIVITIES)
                ?: error("APKを読み取れません。")
            // Explicit component launch also works with Android's package-visibility filtering.
            val launch = packageManager.getLaunchIntentForPackage(info.packageName)
                ?: error("起動できるActivityがありません。")
            startActivity(launch)
            setResult(RESULT_OK, Intent().putExtra("run_started", true))
        } catch (_: Exception) { details.text = "先にAPKをインストールしてください。インストール後は端末のアプリ一覧からも起動できます。" }
    }

    private fun label(value: String, size: Float): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(uiTextColor); setPadding(0, dp(10), 0, dp(10))
        page.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun button(value: String, parent: LinearLayout = page, action: () -> Unit) {
        parent.addView(Button(this).apply {
            text = value; isAllCaps = false; textSize = 14f; setTextColor(uiTextColor)
            background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(39, 43, 51) else Color.rgb(239, 242, 247))
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(8) })
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
