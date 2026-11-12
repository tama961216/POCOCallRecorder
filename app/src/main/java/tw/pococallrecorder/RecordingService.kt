
package tw.pococallrecorder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingService : Service() {

    companion object {
        const val ACTION_ENABLE_AUTO =
            "tw.pococallrecorder.ENABLE_AUTO"

        const val ACTION_MANUAL_START =
            "tw.pococallrecorder.MANUAL_START"

        const val ACTION_STOP_RECORDING =
            "tw.pococallrecorder.STOP_RECORDING"
    }

    private var recorder: MediaRecorder? = null
    private var outputUri: android.net.Uri? = null
    private var outputFd: ParcelFileDescriptor? = null

    private val channelId = "recording_status"
    private val notificationId = 10

    private var autoEnabled = false
    private var wasRinging = false
    private var callWasActive = false
    private var isIncomingRecording = false
    private var isManualRecording = false

    private var telephonyManager: TelephonyManager? = null

    private val phoneListener =
        object : PhoneStateListener() {

            override fun onCallStateChanged(
                state: Int,
                phoneNumber: String?
            ) {
                super.onCallStateChanged(state, phoneNumber)

                when (state) {

                    TelephonyManager.CALL_STATE_RINGING -> {
                        wasRinging = true
                        updateNotification("偵測到來電，等待接通")
                    }

                    TelephonyManager.CALL_STATE_OFFHOOK -> {

                        callWasActive = true

                        if (autoEnabled && wasRinging) {

                            wasRinging = false
                            isIncomingRecording = true

                            startRecording("來電錄音")
                        }
                    }

                    TelephonyManager.CALL_STATE_IDLE -> {

                        wasRinging = false

                        if (callWasActive) {

                            callWasActive = false

                            if (recorder != null) {
                                stopRecording()
                            }

                            isIncomingRecording = false
                            isManualRecording = false

                            updateNotification(
                                if (autoEnabled)
                                    "自動偵測待命中"
                                else
                                    "服務待命中"
                            )
                        }
                    }
                }
            }
        }

    override fun onCreate() {
        super.onCreate()

        createChannel()

        startForeground(
            notificationId,
            notification("服務啟動中")
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_ENABLE_AUTO -> {
                enableAutoDetection()
            }

            ACTION_MANUAL_START -> {
                isManualRecording = true
                isIncomingRecording = false
                startRecording("手動錄音")
            }

            ACTION_STOP_RECORDING -> {
                stopRecording()
                isManualRecording = false
                isIncomingRecording = false

                updateNotification(
                    if (autoEnabled)
                        "自動偵測待命中"
                    else
                        "錄音已停止"
                )
            }
        }

        return START_STICKY
    }

    private fun enableAutoDetection() {

        if (
            checkSelfPermission(
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            updateNotification("缺少電話狀態權限")
            return
        }

        autoEnabled = true

        if (telephonyManager == null) {

            telephonyManager =
                getSystemService(TELEPHONY_SERVICE)
                        as TelephonyManager

            telephonyManager?.listen(
                phoneListener,
                PhoneStateListener.LISTEN_CALL_STATE
            )
        }

        updateNotification("來電自動偵測已啟用")
    }

    private fun startRecording(message: String) {

        if (recorder != null) return

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            updateNotification("缺少麥克風權限")
            return
        }

        var newUri: android.net.Uri? = null

        try {

            val name =
                "POCO_Record_" +
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.US
                ).format(Date()) +
                ".m4a"

            val values = ContentValues().apply {

                put(
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    name
                )

                put(
                    MediaStore.Audio.Media.MIME_TYPE,
                    "audio/mp4"
                )

                put(
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    "Music/POCOCallRecorder"
                )

                if (Build.VERSION.SDK_INT >= 29) {
                    put(
                        MediaStore.Audio.Media.IS_PENDING,
                        1
                    )
                }
            }

            newUri = contentResolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: throw IllegalStateException(
                "無法建立錄音檔"
            )

            val fd = contentResolver.openFileDescriptor(
                newUri,
                "w"
            ) ?: throw IllegalStateException(
                "無法開啟錄音檔"
            )

            outputUri = newUri
            outputFd = fd

            recorder =
                if (Build.VERSION.SDK_INT >= 31) {
                    MediaRecorder(this)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

            recorder?.apply {

                setAudioSource(
                    MediaRecorder.AudioSource.MIC
                )

                setOutputFormat(
                    MediaRecorder.OutputFormat.MPEG_4
                )

                setAudioEncoder(
                    MediaRecorder.AudioEncoder.AAC
                )

                setAudioSamplingRate(44100)

                setAudioEncodingBitRate(128000)

                setOutputFile(fd.fileDescriptor)

                prepare()
                start()
            }

            updateNotification(
                "$message：$name"
            )

        } catch (e: Exception) {

            try {
                recorder?.release()
            } catch (_: Exception) {}

            recorder = null

            try {
                outputFd?.close()
            } catch (_: Exception) {}

            outputFd = null

            newUri?.let {
                try {
                    contentResolver.delete(it, null, null)
                } catch (_: Exception) {}
            }

            outputUri = null

            isManualRecording = false
            isIncomingRecording = false

            updateNotification(
                "錄音失敗：${e.message}"
            )
        }
    }

    private fun stopRecording() {

        val currentRecorder = recorder

        if (currentRecorder == null) return

        var success = false

        try {
            currentRecorder.stop()
            success = true
        } catch (_: Exception) {
            // 錄音時間過短時可能無法正常停止
        }

        try {
            currentRecorder.release()
        } catch (_: Exception) {}

        recorder = null

        try {
            outputFd?.close()
        } catch (_: Exception) {}

        outputFd = null

        outputUri?.let { uri ->

            if (success) {

                try {

                    val values = ContentValues()

                    if (Build.VERSION.SDK_INT >= 29) {
                        values.put(
                            MediaStore.Audio.Media.IS_PENDING,
                            0
                        )
                    }

                    contentResolver.update(
                        uri,
                        values,
                        null,
                        null
                    )

                } catch (_: Exception) {}

            } else {

                try {
                    contentResolver.delete(
                        uri,
                        null,
                        null
                    )
                } catch (_: Exception) {}
            }
        }

        outputUri = null

        updateNotification("錄音已停止並儲存")
    }

    private fun createChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            val channel = NotificationChannel(
                channelId,
                "錄音狀態",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(channel)
        }
    }

    private fun notification(
        message: String
    ): Notification {

        val builder =
            if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(this, channelId)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

        return builder
            .setContentTitle("POCO Call Recorder")
            .setContentText(message)
            .setSmallIcon(
                android.R.drawable.ic_btn_speak_now
            )
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(
        message: String
    ) {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            notificationId,
            notification(message)
        )
    }

    override fun onDestroy() {

        try {
            telephonyManager?.listen(
                phoneListener,
                PhoneStateListener.LISTEN_NONE
            )
        } catch (_: Exception) {}

        telephonyManager = null

        stopRecording()

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}
