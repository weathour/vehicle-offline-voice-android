package com.company.vehiclevoice.asr

import android.content.Context
import com.company.vehiclevoice.copyAssetTree
import java.io.File
import java.security.MessageDigest

object VoskModelAssetInstaller {
    const val DEFAULT_ASSET_DIR = "model-cn"

    fun ensureModelCopied(context: Context, assetDir: String = DEFAULT_ASSET_DIR): File {
        val target = File(context.filesDir, "vosk-models/$assetDir")
        if (isValidModel(target)) return target
        if (target.exists()) target.deleteRecursively()
        copyAssetTree(context, assetDir, target)
        require(isValidModel(target)) { "Copied Vosk model failed manifest validation: ${target.absolutePath}" }
        return target
    }


    fun isValidModel(target: File): Boolean {
        val manifest = File(target, "MODEL_MANIFEST.json")
        if (!manifest.exists()) return false
        val manifestText = manifest.readText()
        val entries = Regex(
            """\{\s*"path"\s*:\s*"(.*?)".*?"sha256"\s*:\s*"(.*?)"""",
            RegexOption.DOT_MATCHES_ALL
        ).findAll(manifestText)
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()
        if (entries.isEmpty()) return false
        return entries.all { (relativePath, expectedSha) ->
            val file = File(target, relativePath)
            file.exists() && sha256(file) == expectedSha
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

}
