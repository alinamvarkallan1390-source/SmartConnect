package com.alinam.smartconnect.mobile.sync

import android.content.ClipboardManager
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import com.alinam.smartconnect.mobile.bluetooth.BluetoothManager
import com.alinam.smartconnect.mobile.data.repository.DeviceInfoRepository
import com.alinam.smartconnect.mobile.util.DeviceInfoCollector
import com.alinam.smartconnect.shared.protocol.MediaControlPayload
import com.alinam.smartconnect.shared.protocol.MediaInfoPayload
import com.alinam.smartconnect.shared.protocol.Message
import com.alinam.smartconnect.shared.protocol.MessageType
import com.alinam.smartconnect.shared.protocol.RemoteControlPayload
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothManager: BluetoothManager,
    private val deviceInfoRepository: DeviceInfoRepository,
    private val deviceInfoCollector: DeviceInfoCollector
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private var syncJob: Job? = null
    private var mediaJob: Job? = null

    companion object {
        private const val DEVICE_INFO_INTERVAL = 5_000L
        private const val MEDIA_SYNC_INTERVAL = 2_000L
    }

    fun startSync() {
        startDeviceInfoSync()
        startMediaSync()
        Timber.d("SyncManager started")
    }

    fun stopSync() {
        syncJob?.cancel()
        mediaJob?.cancel()
        Timber.d("SyncManager stopped")
    }

    private fun startDeviceInfoSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (true) {
                try {
                    val info = deviceInfoCollector.collectPhoneInfo()
                    bluetoothManager.sendMessage(
                        Message(MessageType.DEVICE_INFO, gson.toJson(info))
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Device info sync error")
                }
                delay(DEVICE_INFO_INTERVAL)
            }
        }
    }

    private fun startMediaSync() {
        mediaJob?.cancel()
        mediaJob = scope.launch {
            while (true) {
                try {
                    val mediaInfo = getActiveMediaInfo()
                    if (mediaInfo != null) {
                        bluetoothManager.sendMessage(
                            Message(MessageType.MEDIA_INFO, gson.toJson(mediaInfo))
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Media sync error")
                }
                delay(MEDIA_SYNC_INTERVAL)
            }
        }
    }

    private fun getActiveMediaInfo(): MediaInfoPayload? {
        return try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                ?: return null
            // Requires MEDIA_CONTENT_CONTROL or notification listener
            val controllers: List<MediaController> = try {
                msm.getActiveSessions(null)
            } catch (se: SecurityException) {
                emptyList()
            }
            val active = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: controllers.firstOrNull()
                ?: return null
            val metadata = active.metadata ?: return null
            val playbackState = active.playbackState
            MediaInfoPayload(
                title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "",
                artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "",
                album = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM) ?: "",
                isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING,
                duration = metadata.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION),
                position = playbackState?.position ?: 0L
            )
        } catch (e: Exception) { null }
    }

    fun processMessage(message: Message) {
        scope.launch {
            when (message.type) {
                MessageType.MEDIA_CONTROL -> handleMediaControl(
                    gson.fromJson(message.payload, MediaControlPayload::class.java)
                )
                MessageType.REMOTE_CONTROL -> handleRemoteControl(
                    gson.fromJson(message.payload, RemoteControlPayload::class.java)
                )
                MessageType.CLIPBOARD_SYNC -> handleClipboardSync(message.payload)
                MessageType.FIND_DEVICE -> handleFindPhone()
                MessageType.FIND_DEVICE_STOP -> stopRinging()
                else -> Timber.d("Unhandled message: ${message.type}")
            }
        }
    }

    private fun handleMediaControl(payload: MediaControlPayload) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (payload.action) {
            "PLAY", "PAUSE" -> simulateMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "NEXT" -> simulateMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
            "PREV" -> simulateMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "VOLUME_UP" -> am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI
            )
            "VOLUME_DOWN" -> am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI
            )
        }
    }

    private fun simulateMediaKey(keyCode: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
    }

    private fun handleRemoteControl(payload: RemoteControlPayload) {
        // Phone-side remote control from watch
        when (payload.action) {
            "VOLUME_UP", "VOLUME_DOWN",
            "PLAY", "PAUSE", "NEXT", "PREV" -> handleMediaControl(MediaControlPayload(payload.action))
            // Other actions are watch-side only
        }
    }

    private fun handleClipboardSync(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText("SmartConnect", text)
        )
        Timber.d("Clipboard synced from watch")
    }

    private var ringtonePlayer: android.media.Ringtone? = null
    private var isRinging = false

    private fun handleFindPhone() {
        isRinging = true
        // Max volume ring
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_RING)
        am.setStreamVolume(AudioManager.STREAM_RING, maxVol, 0)
        val ringtoneUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
        ringtonePlayer = android.media.RingtoneManager.getRingtone(context, ringtoneUri)
        ringtonePlayer?.play()
        // Flashlight strobe
        scope.launch {
            val camManager = context.getSystemService(android.hardware.camera2.CameraManager::class.java)
                ?: return@launch
            val cameraId = try {
                camManager.cameraIdList.firstOrNull { id ->
                    camManager.getCameraCharacteristics(id)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: camManager.cameraIdList.firstOrNull()
            } catch (e: Exception) { null } ?: return@launch
            repeat(20) {
                if (!isRinging) return@launch
                try {
                    camManager.setTorchMode(cameraId, it % 2 == 0)
                    delay(300)
                } catch (e: Exception) {
                    Timber.w(e, "Torch toggle failed")
                    return@launch
                }
            }
            try { camManager.setTorchMode(cameraId, false) } catch (e: Exception) {}
        }
        bluetoothManager.sendMessage(Message(MessageType.FIND_DEVICE_ACK))
    }

    private fun stopRinging() {
        isRinging = false
        ringtonePlayer?.stop()
        ringtonePlayer = null
    }
}
