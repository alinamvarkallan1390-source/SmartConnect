package com.alinam.smartconnect.mobile.data.repository

import android.content.Context
import com.alinam.smartconnect.mobile.data.db.dao.FileTransferDao
import com.alinam.smartconnect.mobile.data.db.entity.FileTransferEntity
import com.alinam.smartconnect.mobile.data.model.FileTransferState
import com.alinam.smartconnect.mobile.bluetooth.BluetoothManager
import com.alinam.smartconnect.mobile.security.SecurityManager
import com.alinam.smartconnect.shared.protocol.FileTransferChunkPayload
import com.alinam.smartconnect.shared.protocol.FileTransferStartPayload
import com.alinam.smartconnect.shared.protocol.Message
import com.alinam.smartconnect.shared.protocol.MessageType
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileTransferRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileTransferDao: FileTransferDao,
    private val bluetoothManager: BluetoothManager,
    private val securityManager: SecurityManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val CHUNK_SIZE = 4096
    private val BUFFER_BYTES = ByteArray(CHUNK_SIZE)

    private val _transferState = MutableStateFlow<FileTransferState>(FileTransferState.Idle)
    val transferState: StateFlow<FileTransferState> = _transferState.asStateFlow()

    private var transferJob: Job? = null
    @Volatile private var isPaused = false
    @Volatile private var isCancelled = false
    private var currentTransferId: String? = null

    fun getAllTransfers(): Flow<List<FileTransferEntity>> = fileTransferDao.getAllTransfers()

    fun sendFile(filePath: String) {
        val file = File(filePath)
        if (!file.exists() || !file.canRead()) {
            _transferState.value = FileTransferState.Failed("File not readable: $filePath")
            return
        }
        val transferId = UUID.randomUUID().toString()
        currentTransferId = transferId
        isPaused = false
        isCancelled = false

        transferJob?.cancel()
        transferJob = scope.launch {
            FileInputStream(file).use { input ->
                try {
                    val sessionKey = securityManager.generateSessionKey()
                    val fileSize = file.length()
                    val totalChunks = ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
                    val mimeType = getMimeType(filePath)
                    // First pass: compute checksum
                    val checksum = computeFileChecksum(file, sessionKey)

                    // Save to DB
                    fileTransferDao.insert(FileTransferEntity(
                        transferId = transferId,
                        fileName = file.name,
                        filePath = filePath,
                        fileSize = fileSize,
                        mimeType = mimeType,
                        direction = "SEND",
                        status = "IN_PROGRESS",
                        checksum = checksum
                    ))

                    // Send SESSION_KEY first so receiver can decrypt
                    val sessionKeyB64 = android.util.Base64.encodeToString(
                        sessionKey, android.util.Base64.NO_WRAP
                    )
                    bluetoothManager.sendMessage(
                        Message(MessageType.SESSION_KEY_EXCHANGE, sessionKeyB64)
                    )
                    delay(200)

                    // Send START packet
                    val startPayload = FileTransferStartPayload(
                        transferId = transferId,
                        fileName = file.name,
                        fileSize = fileSize,
                        mimeType = mimeType,
                        checksum = checksum,
                        chunkSize = CHUNK_SIZE,
                        totalChunks = totalChunks
                    )
                    bluetoothManager.sendMessage(
                        Message(MessageType.FILE_TRANSFER_START, gson.toJson(startPayload))
                    )
                    delay(200)

                    var bytesSent = 0L
                    val startTime = System.currentTimeMillis()

                    for (i in 0 until totalChunks) {
                        while (isPaused && !isCancelled) delay(500)
                        if (isCancelled) {
                            _transferState.value = FileTransferState.Failed("Cancelled")
                            fileTransferDao.markFailed(transferId)
                            return@launch
                        }

                        val read = input.read(BUFFER_BYTES)
                        if (read <= 0) {
                            Timber.w("Unexpected EOF at chunk $i (read=$read)")
                            break
                        }
                        val chunk: ByteArray = if (read == CHUNK_SIZE) BUFFER_BYTES
                            else BUFFER_BYTES.copyOf(read)

                        val encryptedChunk = securityManager.encryptWithSessionKey(chunk, sessionKey)
                        val chunkChecksum = securityManager.computeChecksum(chunk)

                        val chunkPayload = FileTransferChunkPayload(
                            transferId = transferId,
                            chunkIndex = i,
                            totalChunks = totalChunks,
                            data = encryptedChunk,
                            checksum = chunkChecksum
                        )
                        bluetoothManager.sendMessage(
                            Message(MessageType.FILE_TRANSFER_CHUNK, gson.toJson(chunkPayload))
                        )

                        bytesSent += read
                        val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                        val speedBps = bytesSent * 1000 / elapsed
                        val remaining = if (speedBps > 0) (fileSize - bytesSent) / speedBps else 0
                        val progress = if (fileSize > 0) bytesSent.toFloat() / fileSize else 1f

                        _transferState.value = FileTransferState.Transferring(
                            fileName = file.name,
                            progress = progress.coerceIn(0f, 1f),
                            speedBps = speedBps,
                            remainingSeconds = remaining,
                            transferId = transferId,
                            isPaused = isPaused
                        )
                        fileTransferDao.updateProgress(transferId, "IN_PROGRESS", progress)

                        delay(10) // Flow control
                    }

                    // Send COMPLETE
                    bluetoothManager.sendMessage(
                        Message(MessageType.FILE_TRANSFER_COMPLETE, transferId)
                    )
                    _transferState.value = FileTransferState.Completed(file.name, transferId)
                    fileTransferDao.markCompleted(transferId)
                    Timber.i("File transfer completed: ${file.name}")

                } catch (e: Exception) {
                    Timber.e(e, "File transfer failed")
                    _transferState.value = FileTransferState.Failed(e.message ?: "Unknown error")
                    fileTransferDao.markFailed(transferId)
                }
            }
        }
    }

    private fun computeFileChecksum(file: File, key: ByteArray): String {
        // Compute plaintext checksum for integrity
        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            android.util.Base64.encodeToString(md.digest(), android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.w(e, "Checksum compute failed")
            ""
        }
    }

    fun pauseTransfer() {
        isPaused = true
        val state = _transferState.value
        if (state is FileTransferState.Transferring) {
            _transferState.value = state.copy(isPaused = true)
            currentTransferId?.let {
                scope.launch {
                    bluetoothManager.sendMessage(Message(MessageType.FILE_TRANSFER_PAUSE, it))
                }
            }
        }
    }

    fun resumeTransfer() {
        isPaused = false
        val state = _transferState.value
        if (state is FileTransferState.Transferring) {
            _transferState.value = state.copy(isPaused = false)
            currentTransferId?.let {
                scope.launch {
                    bluetoothManager.sendMessage(Message(MessageType.FILE_TRANSFER_RESUME, it))
                }
            }
        }
    }

    fun cancelTransfer() {
        isCancelled = true
        transferJob?.cancel()
        currentTransferId?.let { id ->
            scope.launch {
                bluetoothManager.sendMessage(Message(MessageType.FILE_TRANSFER_CANCEL, id))
                fileTransferDao.markFailed(id)
            }
        }
        _transferState.value = FileTransferState.Idle
    }

    private fun getMimeType(path: String): String {
        val ext = path.substringAfterLast(".", "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif", "webp" -> "image/$ext"
            "mp4", "mkv", "webm" -> "video/$ext"
            "mp3", "aac", "wav", "ogg" -> "audio/$ext"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
