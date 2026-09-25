package com.example.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FirestoreLogEntry(
    val timestamp: Long,
    val timeString: String,
    val operation: String, // "READ", "WRITE", "LISTENER", "BATCH"
    val path: String,
    val details: String,
    val latencyMs: Long,
    val source: String, // "CACHE", "SERVER", "PENDING_WRITE", "UNKNOWN"
    val isSuccess: Boolean
)

object FirestoreLoggingInterceptor {
    private const val TAG = "FirestoreInterceptor"

    private val _logs = MutableStateFlow<List<FirestoreLogEntry>>(emptyList())
    val logs: StateFlow<List<FirestoreLogEntry>> = _logs.asStateFlow()

    private val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun addLog(
        operation: String,
        path: String,
        details: String,
        latencyMs: Long,
        source: String,
        isSuccess: Boolean
    ) {
        val now = System.currentTimeMillis()
        val entry = FirestoreLogEntry(
            timestamp = now,
            timeString = sdf.format(Date(now)),
            operation = operation,
            path = path,
            details = details,
            latencyMs = latencyMs,
            source = source,
            isSuccess = isSuccess
        )
        val currentList = _logs.value.toMutableList()
        currentList.add(0, entry) // Newest first
        if (currentList.size > 200) {
            currentList.removeAt(currentList.lastIndex)
        }
        _logs.value = currentList
    }

    // Trace Read Operations with OkHttp-style formatting
    fun logRead(path: String, docCount: Int, source: String, latencyMs: Long, isSuccess: Boolean = true, error: String? = null) {
        val details = if (isSuccess) {
            "Retrieved $docCount documents/fields."
        } else {
            "Read failed: $error"
        }
        addLog("READ", path, details, latencyMs, source, isSuccess)

        val currentThread = Thread.currentThread().name
        val separator = "┌────── FIRESTORE READ OPERATION ──────────────────────────────────────────"
        val header = "│ --> GET $path [Thread: $currentThread]"
        val metadata = "│ Metadata: INCLUDE_METADATA_CHANGES\n│ Source-Preference: LOCAL_CACHE_OR_SERVER"
        val divider = "├──────────────────────────────────────────────────────────────────────────"
        val response = if (isSuccess) {
            "│ <-- 200 OK (latency: ${latencyMs}ms, source: $source)\n│ Documents Retrieved: $docCount\n│ Details: $details"
        } else {
            "│ <-- 400 Bad Request (latency: ${latencyMs}ms, error: $error)\n│ Details: $details"
        }
        val footer = "└──────────────────────────────────────────────────────────────────────────"
        Log.i(TAG, "\n$separator\n$header\n$metadata\n$divider\n$response\n$footer")
    }

    // Trace Write Operations (Set, Update, Delete) with OkHttp-style formatting
    fun logWrite(operation: String, path: String, details: String, latencyMs: Long, isSuccess: Boolean = true, error: String? = null) {
        val finalDetails = if (isSuccess) details else "Write failed: $error"
        val source = if (isSuccess) "PENDING_WRITE (Optimistic)" else "ERROR"
        addLog(operation, path, finalDetails, latencyMs, source, isSuccess)

        val currentThread = Thread.currentThread().name
        val separator = "┌────── FIRESTORE WRITE OPERATION ─────────────────────────────────────────"
        val header = "│ --> $operation $path [Thread: $currentThread]"
        val divider = "├──────────────────────────────────────────────────────────────────────────"
        val response = if (isSuccess) {
            "│ <-- 200 OK (latency: ${latencyMs}ms, source: $source)\n│ Details: $details"
        } else {
            "│ <-- 500 Write Exception (latency: ${latencyMs}ms)\n│ Error: $error"
        }
        val footer = "└──────────────────────────────────────────────────────────────────────────"
        Log.i(TAG, "\n$separator\n$header\n$divider\n$response\n$footer")
    }

    // Trace Listener Events & Metadata Changes with OkHttp-style formatting
    fun logListenerEvent(
        collectionPath: String,
        docCount: Int,
        hasPendingWrites: Boolean,
        isFromCache: Boolean,
        latencyMs: Long,
        changeTypeSummary: String = ""
    ) {
        val source = when {
            hasPendingWrites -> "LOCAL_CACHE (Pending Server Ack)"
            isFromCache -> "LOCAL_CACHE"
            else -> "SERVER_CONFIRMED"
        }
        val details = "Listener emitted $docCount documents. $changeTypeSummary"
        addLog("LISTENER", collectionPath, details, latencyMs, source, true)

        val currentThread = Thread.currentThread().name
        val separator = "┌────── FIRESTORE SNAPSHOT TRIGGER ────────────────────────────────────────"
        val header = "│ --> WATCH $collectionPath [Thread: $currentThread]"
        val state = "│ Listener-State: ACTIVE\n│ MetadataChanges: MetadataChanges.INCLUDE"
        val divider = "├──────────────────────────────────────────────────────────────────────────"
        val response = "│ <-- EMIT (latency: ${latencyMs}ms, source: $source)\n│ Documents In View: $docCount\n│ Pending Writes: $hasPendingWrites\n│ From Cache: $isFromCache"
        val footer = "└──────────────────────────────────────────────────────────────────────────"
        Log.i(TAG, "\n$separator\n$header\n$state\n$divider\n$response\n$footer")
    }

    // Trace Server Acknowledgements
    fun logServerAcknowledgement(path: String, latencyMs: Long) {
        addLog("SERVER_ACK", path, "Server acknowledged local write.", latencyMs, "SERVER_CONFIRMED", true)
        Log.d(TAG, "<-- SERVER_ACK $path (${latencyMs}ms) | Local write confirmed and consolidated on cloud.")
    }
}
