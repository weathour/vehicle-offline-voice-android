package com.company.vehiclevoice

import android.content.Context
import java.io.File

internal fun copyAssetTree(context: Context, assetPath: String, target: File) {
    val children = context.assets.list(assetPath)?.toList().orEmpty()
    if (children.isEmpty()) {
        target.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            target.outputStream().use(input::copyTo)
        }
    } else {
        target.mkdirs()
        children.forEach { child -> copyAssetTree(context, "$assetPath/$child", File(target, child)) }
    }
}
