package com.feishin.mobile.debug

import android.content.Context
import android.graphics.Bitmap
import com.feishin.mobile.core.ui.ArtworkFixtures
import com.feishin.mobile.core.ui.ArtworkPixels
import java.io.File
import java.io.FileOutputStream

/**
 * Writes every [ArtworkFixtures.ALL] image as an ARGB_8888 PNG under
 * `context.filesDir/fixtures/<name>.png` for manual/ADB inspection.
 */
object DebugFixtureWriter {

    private const val FIXTURES_DIR = "fixtures"

    fun writeAll(context: Context): File {
        val dir = File(context.filesDir, FIXTURES_DIR).apply { mkdirs() }
        for ((name, pixels) in ArtworkFixtures.ALL) {
            writePng(File(dir, "$name.png"), pixels)
        }
        return dir
    }

    private fun writePng(target: File, pixels: ArtworkPixels) {
        val bitmap = Bitmap.createBitmap(
            pixels.argb,
            pixels.width,
            pixels.height,
            Bitmap.Config.ARGB_8888,
        )
        try {
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
