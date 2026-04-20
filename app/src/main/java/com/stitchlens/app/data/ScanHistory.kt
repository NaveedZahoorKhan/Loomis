package com.stitchlens.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ScanRecord(
    val fileName: String,
    val filePath: String,
    val pageCount: Int,
    val timestamp: Long,
    val fileSizeBytes: Long
) {
    val exists: Boolean get() = File(filePath).exists()
}

/**
 * Persists scan history using SharedPreferences + JSON.
 * PDFs are saved to app's files directory (survives cache clears).
 */
object ScanHistory {

    private const val PREFS_NAME = "loomis_scan_history"
    private const val KEY_SCANS = "scans"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getScans(context: Context): List<ScanRecord> {
        val json = prefs(context).getString(KEY_SCANS, "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<ScanRecord>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val record = ScanRecord(
                fileName = obj.getString("fileName"),
                filePath = obj.getString("filePath"),
                pageCount = obj.getInt("pageCount"),
                timestamp = obj.getLong("timestamp"),
                fileSizeBytes = obj.getLong("fileSizeBytes")
            )
            if (record.exists) list.add(record)
        }
        return list.sortedByDescending { it.timestamp }
    }

    fun addScan(context: Context, record: ScanRecord) {
        val current = getScans(context).toMutableList()
        // Remove duplicate by path
        current.removeAll { it.filePath == record.filePath }
        current.add(0, record)
        // Keep max 50
        val trimmed = current.take(50)
        save(context, trimmed)
    }

    fun deleteScan(context: Context, record: ScanRecord) {
        File(record.filePath).delete()
        val current = getScans(context).toMutableList()
        current.removeAll { it.filePath == record.filePath }
        save(context, current)
    }

    private fun save(context: Context, scans: List<ScanRecord>) {
        val arr = JSONArray()
        for (s in scans) {
            arr.put(JSONObject().apply {
                put("fileName", s.fileName)
                put("filePath", s.filePath)
                put("pageCount", s.pageCount)
                put("timestamp", s.timestamp)
                put("fileSizeBytes", s.fileSizeBytes)
            })
        }
        prefs(context).edit().putString(KEY_SCANS, arr.toString()).apply()
    }

    /** Returns the persistent PDF directory. */
    fun getPdfDir(context: Context): File =
        File(context.filesDir, "scans").apply { mkdirs() }
}
