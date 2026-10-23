package com.nida.kstudio

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class RunActivity : Activity() {
    private lateinit var settings: RunSettings
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
        settings = RunSettings(this)
        waitingForInstallPermission = savedInstanceState?.getBoolean("waiting_install", false) ?: false
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
            setBackgroundColor(uiBackground)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(page) }
        setContentView(scroll)
        val insetsView = window.decorView
        insetsView.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        button("← エディタに戻る") { finish() }
        title = label("コードを実行", 24f)
        label("Kotlinをコンパイルして、端末でAndroidアプリとして起動します。", 14f)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true; visibility = View.GONE
        }
        page.addView(progress, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(16) })
        actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(actions)
        details = label("", 12f).apply { typeface = Typeface.MONOSPACE; setTextIsSelectable(true) }

        if (RunSession.state.busy || (savedInstanceState != null && RunSession.state.log.isNotBlank())) {
            render(RunSession.state)
        } else {
            showReady()
            if (savedInstanceState == null && settings.uploadConsent && settings.pending() == null &&
                runCatching { settings.readToken().isNotBlank() }.getOrDefault(false)) startBuild()
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
        title.text = "Run — 実際のコードを実行"
        actions.removeAllViews()
        button("ビルドして実行", actions) { startBuild() }
        if (settings.pending() != null) button("前回のビルドに再接続", actions) {
            RunSession.start(this, tree, settings.pending())
        }
        button("GitHub接続設定", actions) { showSettings() }
        details.text = "実行先: ${settings.repository}\n\n初回は「GitHub接続設定」を開いてください。\nビルドにはインターネット接続とGitHub Actionsを使います。"
    }

    private fun startBuild() {
        if (RunSession.state.busy) return
        if (!settings.uploadConsent || !runCatching { settings.readToken().isNotBlank() }.getOrDefault(false)) {
            showSettings(); return
        }
        if (settings.pending() != null) {
            details.text = "前回のビルドが残っています。「前回のビルドに再接続」で結果を確認してください。"
            actions.removeAllViews()
            button("前回のビルドに再接続", actions) { RunSession.start(this, tree, settings.pending()) }
            button("接続設定", actions) { showSettings() }
            return
        }
        if (tree == null) { details.text = "プロジェクトフォルダを開いてからRunしてください。"; return }
        RunSession.start(this, tree)
        render(RunSession.state)
    }

    private fun showSettings() {
        actions.removeAllViews()
        title.text = "GitHub接続設定"
        val repo = EditText(this).apply {
            hint = "owner/repository"; setText(settings.repository)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(uiTextColor); setHintTextColor(Color.GRAY); setSingleLine()
        }
        actions.addView(repo)
        val token = EditText(this).apply {
            hint = "GitHubのFine-grained PAT"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(uiTextColor); setHintTextColor(Color.GRAY); setSingleLine()
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        actions.addView(token)
        val explanation = TextView(this).apply {
            text = "指定リポジトリへの Contents / Actions の Read and write 権限が必要です。\n" +
                "このリポジトリの最新版か、そのForkを指定できます。トークンは端末で暗号化して保存します。\n" +
                "保存済みトークンを使う場合は空欄のままにしてください。"
            setTextColor(uiTextColor); textSize = 13f; setPadding(0, dp(12), 0, dp(12))
        }
        actions.addView(explanation)
        val consent = CheckBox(this).apply {
            text = "Run時にプロジェクトをこのGitHubリポジトリへ送信する"
            setTextColor(uiTextColor); isChecked = settings.uploadConsent
        }
        val publicConsent = CheckBox(this).apply {
            text = "公開リポジトリを使い、ソースコードが公開されることに同意する"
            setTextColor(uiTextColor); isChecked = settings.allowPublic
        }
        actions.addView(consent); actions.addView(publicConsent)
        button("保存してRunへ戻る", actions) {
            val value = repo.text.toString().trim().removeSuffix(".git")
            if (!Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(value)) {
                details.text = "リポジトリは owner/repo の形式で入力してください。"; return@button
            }
            try {
                if (token.text.isNotBlank()) settings.saveToken(token.text.toString().trim())
                settings.repository = value
                settings.uploadConsent = consent.isChecked
                settings.allowPublic = publicConsent.isChecked
                token.text.clear()
                showReady()
            } catch (_: Exception) { details.text = "トークンを保存できません。再入力してください。" }
        }
        button("トークンを削除", actions) { settings.clearToken(); token.text.clear(); details.text = "保存済みトークンを削除しました。" }
        details.text = "Fine-grained PATはGitHubの Settings → Developer settings → Personal access tokens で作成できます。トークンをチャットに貼る必要はありません。"
    }

    private fun render(state: RunState) {
        title.text = state.title
        progress.visibility = if (state.busy) View.VISIBLE else View.GONE
        details.text = state.log.takeLast(60000)
        actions.removeAllViews()
        if (state.busy) {
            button("ビルドの停止を要求", actions) { RunSession.cancel() }
        } else {
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
            if (settings.pending() != null) button("前回のビルドに再接続", actions) { RunSession.start(this, tree, settings.pending()) }
            button("もう一度ビルド", actions) { startBuild() }
            button("GitHub接続設定", actions) { showSettings() }
            button("ログをコピー", actions) {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("KStudio build log", state.log))
            }
        }
        if (state.runUrl.startsWith("https://github.com/")) button("Actionsを開く", actions) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.runUrl))) }
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
