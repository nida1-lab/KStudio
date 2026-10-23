package com.nida.kstudio

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import java.io.File
import java.io.FileNotFoundException

/** Test-only document tree with nested text, binary data and ignored build files. */
class SnapshotFixtureProvider : ContentProvider() {
    private val files = mapOf(
        "MainActivity.kt" to "package demo\nclass MainActivity".toByteArray(),
        "res/layout/main.xml" to "<TextView/>".toByteArray(),
        "res/drawable/image.png" to byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10),
        "local.properties" to "sdk.dir=/not/portable".toByteArray(),
        "build/generated.kt" to "should not upload".toByteArray()
    )
    private val directories = setOf("root", "res", "res/layout", "res/drawable", "build")
    override fun onCreate() = true
    override fun getType(uri: Uri) = "application/octet-stream"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val parent = DocumentsContract.getDocumentId(uri)
        val columns = projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        val ids = (files.keys + directories.filter { it != "root" }).filter {
            it.substringBeforeLast('/', "root") == parent
        }
        return MatrixCursor(columns).apply {
            ids.forEach { id -> addRow(columns.map { column ->
                when (column) {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID -> id
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME -> id.substringAfterLast('/')
                    DocumentsContract.Document.COLUMN_MIME_TYPE -> if (id in directories) DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream"
                    else -> null
                }
            }) }
        }
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val id = DocumentsContract.getDocumentId(uri)
        val bytes = files[id] ?: throw FileNotFoundException(id)
        val file = File(requireNotNull(context).cacheDir, id.replace('/', '_')).apply { writeBytes(bytes) }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
}
