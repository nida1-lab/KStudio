package com.nida.kstudio

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Grants the installer read access to one generated APK, never the rest of app storage. */
class RunApkProvider : ContentProvider() {
    override fun onCreate() = true
    private fun apk(uri: Uri): File {
        val parts = uri.pathSegments
        if (parts.size != 2 || !Regex("[a-f0-9-]{36}").matches(parts[0]) || parts[1] != "project.apk") throw FileNotFoundException()
        val base = File(requireNotNull(context).cacheDir, "run").canonicalFile
        val file = File(base, parts.joinToString("/")).canonicalFile
        if (!file.path.startsWith(base.path + File.separator) || !file.isFile) throw FileNotFoundException()
        return file
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Read only")
        return ParcelFileDescriptor.open(apk(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun getType(uri: Uri) = "application/vnd.android.package-archive"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val file = apk(uri)
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {
            addRow(columns.map { when (it) { OpenableColumns.DISPLAY_NAME -> file.name; OpenableColumns.SIZE -> file.length(); else -> null } })
        }
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw SecurityException("Read only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw SecurityException("Read only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw SecurityException("Read only")
}
