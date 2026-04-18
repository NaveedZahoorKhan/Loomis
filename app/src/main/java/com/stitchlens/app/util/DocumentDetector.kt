package com.stitchlens.app.util

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.sqrt

/**
 * Document edge detector using OpenCV.
 * Finds the largest quadrilateral contour (document) and returns its bounds.
 */
object DocumentDetector {

    private var initialized = false

    fun init(): Boolean {
        if (!initialized) {
            initialized = OpenCVLoader.initDebug()
        }
        return initialized
    }

    data class FractionalBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

    /**
     * Auto-crop: detect document edges and crop.
     */
    fun cropDocument(bitmap: Bitmap): Bitmap {
        if (!init()) return bitmap
        val bounds = detectBoundsFractional(bitmap) ?: return bitmap
        val x = (bounds.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val y = (bounds.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val w = ((bounds.right - bounds.left) * bitmap.width).toInt().coerceIn(1, bitmap.width - x)
        val h = ((bounds.bottom - bounds.top) * bitmap.height).toInt().coerceIn(1, bitmap.height - y)
        return Bitmap.createBitmap(bitmap, x, y, w, h)
    }

    /**
     * Detect document bounds as fractional coordinates (0..1).
     * Uses OpenCV Canny edge detection + contour finding to locate the document.
     */
    fun detectBoundsFractional(bitmap: Bitmap): FractionalBounds? {
        if (!init()) return null
        return try {
            detectWithOpenCV(bitmap)
        } catch (_: Exception) {
            null
        }
    }

    private fun detectWithOpenCV(bitmap: Bitmap): FractionalBounds? {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        // Downscale for speed
        val maxDim = 500.0
        val ratio = maxDim / maxOf(src.rows(), src.cols())
        val small = Mat()
        Imgproc.resize(src, small, Size(src.cols() * ratio, src.rows() * ratio))

        // Convert to grayscale
        val gray = Mat()
        Imgproc.cvtColor(small, gray, Imgproc.COLOR_BGR2GRAY)

        // Gaussian blur to reduce noise
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        // Canny edge detection
        val edges = Mat()
        Imgproc.Canny(gray, edges, 30.0, 100.0)

        // Dilate to close gaps in edges
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(edges, edges, kernel, Point(-1.0, -1.0), 2)

        // Find contours
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        // Sort by area descending
        val imageArea = small.rows().toDouble() * small.cols()
        val sorted = contours.sortedByDescending { Imgproc.contourArea(it) }

        var result: FractionalBounds? = null

        for (contour in sorted) {
            val area = Imgproc.contourArea(contour)
            // Must be at least 10% of image
            if (area < imageArea * 0.10) continue

            val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.02 * peri, true)

            if (approx.rows() == 4) {
                // Found a quadrilateral — get bounding rect
                val pts = approx.toArray()
                val xs = pts.map { it.x / ratio }
                val ys = pts.map { it.y / ratio }

                val pad = 0.005f
                result = FractionalBounds(
                    left = ((xs.min() / bitmap.width) - pad).toFloat().coerceAtLeast(0f),
                    top = ((ys.min() / bitmap.height) - pad).toFloat().coerceAtLeast(0f),
                    right = ((xs.max() / bitmap.width) + pad).toFloat().coerceAtMost(1f),
                    bottom = ((ys.max() / bitmap.height) + pad).toFloat().coerceAtMost(1f)
                )
                break
            }
        }

        // If no quad found, try bounding rect of largest contour
        if (result == null && sorted.isNotEmpty()) {
            val largest = sorted.first()
            val area = Imgproc.contourArea(largest)
            if (area > imageArea * 0.10) {
                val rect = Imgproc.boundingRect(largest)
                val pad = 0.005f
                result = FractionalBounds(
                    left = ((rect.x / ratio / bitmap.width) - pad).toFloat().coerceAtLeast(0f),
                    top = ((rect.y / ratio / bitmap.height) - pad).toFloat().coerceAtLeast(0f),
                    right = (((rect.x + rect.width) / ratio / bitmap.width) + pad).toFloat().coerceAtMost(1f),
                    bottom = (((rect.y + rect.height) / ratio / bitmap.height) + pad).toFloat().coerceAtMost(1f)
                )
            }
        }

        // Cleanup
        src.release()
        small.release()
        gray.release()
        edges.release()
        hierarchy.release()

        return result
    }
}
