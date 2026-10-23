package com.nida.kstudio

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ProjectSnapshot(val file: File, val count: Int, val sha256: String) {
    companion object {
        private val ignored = setOf(".git", ".gradle", ".idea", ".kstudio", "build", "node_modules",
            "local.properties", "keystore.properties", "secrets.properties")
        private val secretSuffixes = listOf(".jks", ".keystore", ".p12", ".pem", ".key", ".apk", ".aab")

        fun create(context: Context, tree: Uri, cancelled: () -> Unit): ProjectSnapshot {
            val output = File(context.cacheDir, "run-upload.zip")
            var bytes = 0L
            var count = 0
            try {
                ZipOutputStream(output.outputStream().buffered()).use { zip ->
                    fun scan(folder: Uri, prefix: String, depth: Int) {
                        cancelled()
                        require(depth <= 40) { "フォルダ階層が深すぎます。" }
                        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree,
                            DocumentsContract.getDocumentId(folder))
                        val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val cursor = context.contentResolver.query(children, columns, null, null, null)
                            ?: throw IOException("フォルダを読み取れません: $prefix")
                        cursor.use {
                            while (it.moveToNext()) {
                                cancelled()
                                val name = it.getString(1) ?: throw IOException("ファイル名を読み取れません。")
                                if (name in ignored || name.startsWith(".env") || secretSuffixes.any { suffix -> name.endsWith(suffix, true) }) continue
                                require(name.isNotBlank() && name != "." && name != ".." && '/' !in name && '\\' !in name && name.none { ch -> ch.code < 32 }) { "使えないファイル名: $name" }
                                val path = prefix + name
                                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(0))
                                if (it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                                    scan(uri, "$path/", depth + 1)
                                } else {
                                    require(++count <= 1000) { "取り込めるファイルは1000個までです。" }
                                    zip.putNextEntry(ZipEntry(path))
                                    context.contentResolver.openInputStream(uri)?.use { input ->
                                        val buffer = ByteArray(16384)
                                        var fileBytes = 0L
                                        while (true) {
                                            cancelled()
                                            val n = input.read(buffer)
                                            if (n < 0) break
                                            fileBytes += n; bytes += n
                                            require(fileBytes <= 10 * 1024 * 1024 && bytes <= 25 * 1024 * 1024) { "取り込み上限は1ファイル10MB・全体25MBです。" }
                                            zip.write(buffer, 0, n)
                                        }
                                    } ?: throw IOException("ファイルを読み取れません: $path")
                                    zip.closeEntry()
                                }
                            }
                        }
                    }
                    scan(DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)), "", 0)
                }
                require(count > 0) { "プロジェクトが空です。" }
                val digest = MessageDigest.getInstance("SHA-256")
                output.inputStream().use { input ->
                    val buffer = ByteArray(16384)
                    while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
                }
                return ProjectSnapshot(output, count, digest.digest().joinToString("") { "%02x".format(it) })
            } catch (e: Exception) { output.delete(); throw e }
        }
    }
}
