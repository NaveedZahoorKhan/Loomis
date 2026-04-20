package com.stitchlens.app.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stitchlens.app.data.ScanHistory
import com.stitchlens.app.data.ScanRecord
import com.stitchlens.app.util.DocumentDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ScannedPage(
    val uri: Uri,
    val originalBitmap: Bitmap,
    val bitmap: Bitmap,
    val rotation: Float = 0f,
    val filter: FilterType = FilterType.AUTO
)

enum class FilterType { AUTO, DOCUMENT, GRAYSCALE, WHITEBOARD }

class ScanViewModel : ViewModel() {

    val pages = mutableStateListOf<ScannedPage>()
    var pdfUri by mutableStateOf<Uri?>(null)
        private set
    var isGenerating by mutableStateOf(false)
        private set
    var pdfFileName by mutableStateOf("Scan_${System.currentTimeMillis()}.pdf")

    fun addPage(uri: Uri, bitmap: Bitmap) {
        // Auto-crop on capture, but keep original for manual re-crop later
        val cropped = try {
            DocumentDetector.cropDocument(bitmap)
        } catch (_: Exception) {
            bitmap
        }
        pages.add(ScannedPage(uri = uri, originalBitmap = bitmap, bitmap = cropped))
    }

    fun removePage(index: Int) {
        if (index in pages.indices) pages.removeAt(index)
    }

    fun rotatePage(index: Int) {
        if (index in pages.indices) {
            val page = pages[index]
            pages[index] = page.copy(rotation = (page.rotation + 90f) % 360f)
        }
    }

    fun setFilter(index: Int, filter: FilterType) {
        if (index in pages.indices) {
            pages[index] = pages[index].copy(filter = filter)
        }
    }

    /**
     * Crop a page using fractional coords against the ORIGINAL bitmap.
     * This replaces the displayed bitmap but preserves the original.
     */
    fun cropPage(index: Int, leftFrac: Float, topFrac: Float, rightFrac: Float, bottomFrac: Float) {
        if (index !in pages.indices) return
        val page = pages[index]
        val bmp = page.originalBitmap
        val x = (leftFrac * bmp.width).toInt().coerceIn(0, bmp.width - 1)
        val y = (topFrac * bmp.height).toInt().coerceIn(0, bmp.height - 1)
        val w = ((rightFrac - leftFrac) * bmp.width).toInt().coerceIn(1, bmp.width - x)
        val h = ((bottomFrac - topFrac) * bmp.height).toInt().coerceIn(1, bmp.height - y)
        val cropped = Bitmap.createBitmap(bmp, x, y, w, h)
        pages[index] = page.copy(bitmap = cropped)
    }

    fun applyFilter(bitmap: Bitmap, filter: FilterType, rotation: Float): Bitmap {
        var result = bitmap
        if (rotation != 0f) {
            val matrix = Matrix().apply { postRotate(rotation) }
            result = Bitmap.createBitmap(result, 0, 0, result.width, result.height, matrix, true)
        }
        val filtered = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(filtered)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (filter) {
            FilterType.DOCUMENT -> {
                paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                    1.5f, 0f, 0f, 0f, -40f,
                    0f, 1.5f, 0f, 0f, -40f,
                    0f, 0f, 1.5f, 0f, -40f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            FilterType.GRAYSCALE -> {
                paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            }
            FilterType.WHITEBOARD -> {
                paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                    1.3f, 0f, 0f, 0f, 30f,
                    0f, 1.3f, 0f, 0f, 30f,
                    0f, 0f, 1.3f, 0f, 30f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
            FilterType.AUTO -> {
                paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                    1.2f, 0f, 0f, 0f, -10f,
                    0f, 1.2f, 0f, 0f, -10f,
                    0f, 0f, 1.2f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                )))
            }
        }
        canvas.drawBitmap(result, 0f, 0f, paint)
        return filtered
    }

    fun generatePdf(context: Context, onComplete: () -> Unit) {
        if (pages.isEmpty()) return
        isGenerating = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Save to persistent directory (not cache)
                    val pdfDir = ScanHistory.getPdfDir(context)
                    val pdfFile = File(pdfDir, pdfFileName)
                    val document = PdfDocument()

                    for ((index, page) in pages.withIndex()) {
                        val processed = applyFilter(page.bitmap, page.filter, page.rotation)
                        val pageInfo = PdfDocument.PageInfo.Builder(
                            processed.width, processed.height, index + 1
                        ).create()
                        val pdfPage = document.startPage(pageInfo)
                        val canvas = pdfPage.canvas
                        canvas.drawBitmap(processed, 0f, 0f, null)
                        document.finishPage(pdfPage)
                    }

                    FileOutputStream(pdfFile).use { out ->
                        document.writeTo(out)
                    }
                    document.close()

                    // Record in scan history
                    ScanHistory.addScan(context, ScanRecord(
                        fileName = pdfFileName,
                        filePath = pdfFile.absolutePath,
                        pageCount = pages.size,
                        timestamp = System.currentTimeMillis(),
                        fileSizeBytes = pdfFile.length()
                    ))

                    pdfUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        pdfFile
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            isGenerating = false
            onComplete()
        }
    }

    fun reset() {
        pages.clear()
        pdfUri = null
        pdfFileName = "Scan_${System.currentTimeMillis()}.pdf"
    }
}
