package com.company.vehiclevoice.update

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

class UpdateApkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = GitHubReleaseUpdateClient.APK_MIME

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Update APK provider is read-only")
        val fileName = uri.lastPathSegment ?: UPDATE_APK_FILE_NAME
        val file = File(File(requireNotNull(context).cacheDir, UPDATE_CACHE_DIR), fileName)
        if (!file.exists()) throw FileNotFoundException(file.absolutePath)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val UPDATE_CACHE_DIR = "updates"
        const val UPDATE_APK_FILE_NAME = "target-version.apk"

        fun contentUri(context: Context, fileName: String = UPDATE_APK_FILE_NAME): Uri = Uri.Builder()
            .scheme("content")
            .authority("${context.packageName}.updateapk")
            .appendPath(fileName)
            .build()
    }
}
