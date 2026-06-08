package com.company.vehiclevoice.asr

import android.content.Context
import java.io.File

object VoskModelAssetInstaller {
    const val DEFAULT_ASSET_DIR = "model-cn"

    fun ensureModelCopied(context: Context, assetDir: String = DEFAULT_ASSET_DIR): File {
        val target = File(context.filesDir, "vosk-models/$assetDir")
        if (File(target, "conf/model.conf").exists()) return target
        if (target.exists()) target.deleteRecursively()
        copyAssetTree(context, assetDir, target)
        return target
    }

    private fun copyAssetTree(context: Context, assetPath: String, target: File) {
        val children = context.assets.list(assetPath)?.toList().orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            target.mkdirs()
            for (child in children) copyAssetTree(context, "$assetPath/$child", File(target, child))
        }
    }
}
