package com.alinam.smartconnect.mobile.ui.viewmodel

import android.net.Uri
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alinam.smartconnect.mobile.data.repository.FileTransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class FileTransferViewModel @Inject constructor(
    private val fileTransferRepository: FileTransferRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val transferState = fileTransferRepository.transferState
    val allTransfers = fileTransferRepository.getAllTransfers()

    fun sendFile(filePath: String) {
        fileTransferRepository.sendFile(filePath)
    }

    fun sendFileFromUri(uri: Uri) {
        viewModelScope.launch {
            var tempFile: File? = null
            try {
                val fileName = getFileName(uri)
                tempFile = File(context.cacheDir, fileName)
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open URI: $uri")
                input.use { src ->
                    FileOutputStream(tempFile).use { dst -> src.copyTo(dst) }
                }
                if (!tempFile.exists() || tempFile.length() == 0L) {
                    throw IllegalStateException("Empty file")
                }
                fileTransferRepository.sendFile(tempFile.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
                tempFile?.delete()
            }
        }
    }

    fun pauseTransfer() = fileTransferRepository.pauseTransfer()
    fun resumeTransfer() = fileTransferRepository.resumeTransfer()
    fun cancelTransfer() = fileTransferRepository.cancelTransfer()

    private fun getFileName(uri: Uri): String {
        var name = "file_${System.currentTimeMillis()}"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx) ?: name
            }
        }
        return name
    }
}
