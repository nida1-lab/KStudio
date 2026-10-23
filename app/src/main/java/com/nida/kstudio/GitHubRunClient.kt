package com.nida.kstudio

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.zip.ZipInputStream

data class RunState(
    val title: String = "実行の準備",
    val log: String = "",
    val busy: Boolean = false,
    val runUrl: String = "",
    val apk: File? = null,
    val diagnostics: List<BuildDiagnostic> = emptyList()
)

private class GitHubRequestException(val code: Int, message: String) : IOException(message)

/** All source and network work runs on the session's worker thread. */
class GitHubRunClient(
    private val context: Context,
    private val token: String,
    private val checkCancelled: () -> Unit,
    private val publish: (RunState) -> Unit
) {
    private val settings = RunSettings(context)
    private var repository = ""
    private var branch = ""
    private var requestId = ""
    private var sourceDigest = ""
    private var runId = 0L
    private var runUrl = ""
    private var trace = ""

    private fun status(title: String, detail: String = "") {
        if (detail.isNotBlank()) trace = (trace + detail + "\n").takeLast(60000)
        publish(RunState(title, trace, true, runUrl))
    }

    fun run(tree: Uri?, resume: String? = null) {
        var terminal = false
        var finalState: RunState? = null
        try {
            if (resume == null) {
                repository = settings.repository
                require(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(repository)) { "リポジトリは owner/repo の形式で入力してください。" }
                status("GitHubに接続中")
                val repo = api("GET", "")
                require(repo.optJSONObject("permissions")?.optBoolean("push") == true) { "このリポジトリへの書き込み権限が必要です。" }
                require(repo.optBoolean("private") || settings.allowPublic) { "このリポジトリは公開設定です。設定画面でソース公開への同意を選ぶか、非公開リポジトリを指定してください。" }
                val defaultBranch = repo.getString("default_branch")
                val workflow = try { api("GET", "/actions/workflows/run-project.yml") } catch (e: Exception) {
                    throw IOException("実行用ワークフローがありません。最新版KStudioの .github/workflows/run-project.yml と tools をこのリポジトリの既定ブランチへ配置してください。", e)
                }
                require(workflow.optString("state") == "active") { "GitHubのActions画面で「Run project」ワークフローを有効化してください。" }
                status("プロジェクトを読み込み中")
                val snapshot = ProjectSnapshot.create(context, requireNotNull(tree), checkCancelled)
                try {
                    checkCancelled()
                    requestId = UUID.randomUUID().toString()
                    branch = "kstudio-run/$requestId"
                    sourceDigest = snapshot.sha256
                    val head = api("GET", "/commits/${encode(defaultBranch)}")
                    status("ソースを送信中", "${snapshot.count}ファイルを $repository の実行用ブランチへ送信します。")
                    val blob = api("POST", "/git/blobs", JSONObject().put("encoding", "base64")
                        .put("content", Base64.getEncoder().encodeToString(snapshot.file.readBytes())))
                    val gitTree = api("POST", "/git/trees", JSONObject()
                        .put("base_tree", head.getJSONObject("commit").getJSONObject("tree").getString("sha"))
                        .put("tree", JSONArray().put(JSONObject().put("path", ".kstudio/project.zip")
                            .put("mode", "100644").put("type", "blob").put("sha", blob.getString("sha")))))
                    val commit = api("POST", "/git/commits", JSONObject()
                        .put("message", "KStudio Run $requestId")
                        .put("tree", gitTree.getString("sha"))
                        .put("parents", JSONArray().put(head.getString("sha"))))
                    checkCancelled()
                    persist()
                    status("実行ブランチを作成中")
                    // A branch push starts the workflow directly. No workflow file or
                    // default-branch source is modified by the app.
                    try {
                        api("POST", "/git/refs", JSONObject().put("ref", "refs/heads/$branch").put("sha", commit.getString("sha")))
                    } catch (e: GitHubRequestException) {
                        terminal = true
                        throw e
                    }
                } finally { snapshot.file.delete() }
            } else {
                val saved = JSONObject(resume)
                repository = saved.getString("repository")
                branch = saved.getString("branch")
                requestId = saved.getString("request_id")
                sourceDigest = saved.getString("source_sha256")
                runId = saved.optLong("run_id")
                require(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(repository) &&
                    Regex("kstudio-run/[a-f0-9-]{36}").matches(branch)) { "前回の実行情報を読み取れません。" }
            }

            if (runId == 0L) {
                status("Actionsの開始を待機中")
                val deadline = System.currentTimeMillis() + 120000
                while (runId == 0L) {
                    checkCancelled()
                    val runs = api("GET", "/actions/workflows/run-project.yml/runs?event=push&branch=${encode(branch)}&per_page=100").getJSONArray("workflow_runs")
                    for (i in 0 until runs.length()) {
                        val candidate = runs.getJSONObject(i)
                        if (candidate.optString("display_title") == "KStudio Run $branch") {
                            runId = candidate.getLong("id")
                            runUrl = candidate.getString("html_url")
                            persist()
                            break
                        }
                    }
                    if (runId == 0L) {
                        if (System.currentTimeMillis() > deadline) {
                            terminal = true
                            throw IOException("Actionsの開始を確認できません。最新版の実行ワークフローとGitHub Actionsの有効化を確認して再実行してください。")
                        }
                        Thread.sleep(4000)
                    }
                }
            }
            val deadline = System.currentTimeMillis() + 35 * 60 * 1000
            var outcome: String
            while (true) {
                checkCancelled()
                val run = api("GET", "/actions/runs/$runId")
                runUrl = run.getString("html_url")
                val state = run.getString("status")
                if (state == "completed") {
                    outcome = run.optString("conclusion")
                    break
                }
                status(if (state == "queued") "ビルドの順番待ち" else "Kotlin・Androidをビルド中")
                if (System.currentTimeMillis() > deadline) throw IOException("待機時間を超えました。再接続して結果を確認できます。")
                Thread.sleep(5000)
            }
            status("ビルド結果を取得中")
            val artifacts = api("GET", "/actions/runs/$runId/artifacts?per_page=100").getJSONArray("artifacts")
            var artifact: JSONObject? = null
            for (i in 0 until artifacts.length()) {
                val candidate = artifacts.getJSONObject(i)
                if (candidate.optString("name") == "kstudio-run-$requestId" && !candidate.optBoolean("expired")) artifact = candidate
            }
            if (artifact == null) {
                val failedSteps = api("GET", "/actions/runs/$runId/jobs").getJSONArray("jobs")
                val summary = (0 until failedSteps.length()).joinToString("\n") { i ->
                    val job = failedSteps.getJSONObject(i)
                    "${job.optString("name")}: ${job.optString("conclusion")}" 
                }
                throw IOException("ビルド結果: $outcome\n$summary\n詳しいログは「Actionsを開く」から確認できます。")
            }
            val resultDir = File(context.cacheDir, "run/$requestId").apply { mkdirs() }
            downloadArtifact(artifact.getLong("id"), resultDir)
            val logFile = File(resultDir, "build.log")
            val log = if (logFile.isFile) logFile.readText().takeLast(100000) else "ビルドログはありません。"
            val metaFile = File(resultDir, "run-result.json")
            val meta = if (metaFile.isFile) JSONObject(metaFile.readText()) else null
            val apk = File(resultDir, "project.apk")
            val matches = meta != null && meta.optString("request_id") == requestId && meta.optString("source_sha256") == sourceDigest
            if (outcome != "success" || !matches || !apk.isFile) {
                terminal = true
                finalState = RunState("ビルド失敗", "$trace\n$log", false, runUrl, diagnostics = BuildDiagnostic.parse(log))
                return
            }
            require(meta!!.getString("apk_sha256") == sha256(apk)) { "APKのチェックサムが一致しません。再ビルドしてください。" }
            val packageInfo = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            require(packageInfo != null && packageInfo.packageName != context.packageName) { "実行APKが無効か、KStudio自身のパッケージ名と衝突しています。" }
            terminal = true
            finalState = RunState("ビルド成功 — インストールできます", "$trace\n$log", false, runUrl, apk)
        } catch (e: CancellationException) {
            // Before a run ID is visible, stopping observation cannot prove that
            // the pushed branch has not already queued a build.
            var confirmed = runId == 0L && settings.pending() == null
            if (runId != 0L) {
                confirmed = runCatching { api("POST", "/actions/runs/$runId/cancel"); true }.getOrDefault(false)
            }
            terminal = confirmed
            finalState = RunState(if (confirmed) "実行の停止を要求しました" else "監視を停止しました。Actionsの状態を確認してください。", trace, false, runUrl)
        } catch (e: Exception) {
            // A connection failure preserves the run ID so a restarted app can reconnect.
            finalState = RunState("実行できませんでした", trace + "\n" + (e.message ?: e.javaClass.simpleName).replace(token, "***"), false, runUrl)
        } finally {
            if (terminal || (runId == 0L && settings.pending() == null)) {
                if (branch.isNotBlank()) runCatching { api("DELETE", "/git/refs/heads/$branch") }
                settings.savePending(null)
            }
            publish(finalState ?: RunState("実行が終了しました", trace, false, runUrl))
        }
    }

    private fun persist() {
        settings.savePending(JSONObject().put("repository", repository).put("branch", branch)
            .put("request_id", requestId).put("source_sha256", sourceDigest).put("run_id", runId).toString())
    }

    private fun api(method: String, path: String, body: JSONObject? = null): JSONObject {
        val connection = connection(URL("https://api.github.com/repos/$repository$path"), method, true)
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val content = stream?.use { it.readBytes() }?.toString(Charsets.UTF_8).orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(content).optString("message") }.getOrDefault("")
                throw GitHubRequestException(code, "GitHub HTTP $code: ${message.take(500)}" + when (code) {
                    401 -> "\nトークンの期限と入力を確認してください。"
                    403, 404 -> "\n対象リポジトリの Contents / Actions の読み書き権限を確認してください。"
                    else -> ""
                })
            }
            return if (content.isBlank()) JSONObject() else JSONObject(content)
        } finally { connection.disconnect() }
    }

    private fun connection(url: URL, method: String, authenticated: Boolean): HttpURLConnection {
        require(url.protocol == "https") { "HTTPS接続が必要です。" }
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000
            readTimeout = 30000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "KStudio-Android")
            if (authenticated) {
                require(url.host == "api.github.com")
                setRequestProperty("Authorization", "Bearer $token")
            }
        }
    }

    private fun downloadArtifact(id: Long, directory: File) {
        var url = URL("https://api.github.com/repos/$repository/actions/artifacts/$id/zip")
        repeat(5) {
            checkCancelled()
            val c = connection(url, "GET", url.host == "api.github.com")
            try {
                val code = c.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    url = URL(url, c.getHeaderField("Location") ?: throw IOException("ダウンロード先がありません。"))
                } else {
                    require(code == 200) { "成果物を取得できません: HTTP $code" }
                    var bytes = 0L
                    ZipInputStream(c.inputStream.buffered()).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            require(entry.name in setOf("project.apk", "build.log", "run-result.json") && !entry.isDirectory) { "成果物に予期しないファイルがあります。" }
                            File(directory, entry.name).outputStream().use { out ->
                                val buffer = ByteArray(16384)
                                while (true) {
                                    checkCancelled()
                                    val n = zip.read(buffer)
                                    if (n < 0) break
                                    bytes += n
                                    require(bytes <= 200L * 1024 * 1024) { "成果物が大きすぎます。" }
                                    out.write(buffer, 0, n)
                                }
                            }
                        }
                    }
                    return
                }
            } finally { c.disconnect() }
        }
        throw IOException("ダウンロードの転送回数を超えました。")
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16384)
            while (true) { val n = input.read(buffer); if (n < 0) break; md.update(buffer, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
    private fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
