
package tw.pococallrecorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val requestCode = 100
    private val projectionRequestCode = 200

    private lateinit var status: TextView

    private var pendingAction: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 36)
        }

        val title = TextView(this).apply {
            text = "POCO Call Recorder"
            textSize = 24f
        }

        status = TextView(this).apply {
            text = "免 Root 音訊擷取測試版\n\n請選擇錄音功能。"
            textSize = 16f
            setPadding(0, 28, 0, 28)
        }

        val autoStart = Button(this).apply {
            text = "啟用來電自動偵測"
            setOnClickListener {
                requestAndStart(
                    RecordingService.ACTION_ENABLE_AUTO
                )
            }
        }

        val manualStart = Button(this).apply {
            text = "手動開始錄音（一般麥克風）"
            setOnClickListener {
                requestAndStart(
                    RecordingService.ACTION_MANUAL_START
                )
            }
        }

        val playbackStart = Button(this).apply {
            text = "測試系統音訊擷取（LINE 測試）"
            setOnClickListener {
                requestPlaybackCapture()
            }
        }

        val stopRecording = Button(this).apply {
            text = "停止所有錄音"
            setOnClickListener {
                sendServiceAction(
                    RecordingService.ACTION_STOP_RECORDING
                )

                stopService(
                    Intent(
                        this@MainActivity,
                        AudioCaptureService::class.java
                    ).setAction(
                        AudioCaptureService.ACTION_STOP
                    )
                )

                status.text = "已送出停止指令。"
            }
        }

        val disableAuto = Button(this).apply {
            text = "停止自動偵測"
            setOnClickListener {
                stopService(
                    Intent(
                        this@MainActivity,
                        RecordingService::class.java
                    )
                )

                status.text = "自動偵測服務已停止。"
            }
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(autoStart)
        layout.addView(manualStart)
        layout.addView(playbackStart)
        layout.addView(stopRecording)
        layout.addView(disableAuto)

        setContentView(layout)
    }

    private fun requestAndStart(action: String) {

        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE
        )

        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        val missing = permissions.filter {
            checkSelfPermission(it) !=
                PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            pendingAction = action

            requestPermissions(
                missing.toTypedArray(),
                requestCode
            )
        } else {
            sendServiceAction(action)
        }
    }

    private fun requestPlaybackCapture() {

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingAction = "PLAYBACK"

            requestPermissions(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO
                ),
                requestCode
            )

            return
        }

        try {

            val manager =
                getSystemService(
                    MEDIA_PROJECTION_SERVICE
                ) as MediaProjectionManager

            val intent =
                manager.createScreenCaptureIntent()

            startActivityForResult(
                intent,
                projectionRequestCode
            )

            status.text =
                "等待 Android 系統音訊擷取授權..."

        } catch (e: Exception) {

            status.text =
                "無法啟動音訊擷取：${e.message}"
        }
    }

    private fun sendServiceAction(action: String) {

        try {

            val intent = Intent(
                this,
                RecordingService::class.java
            ).setAction(action)

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            status.text = when (action) {

                RecordingService.ACTION_ENABLE_AUTO ->
                    "來電自動偵測服務啟動中。"

                RecordingService.ACTION_MANUAL_START ->
                    "手動錄音啟動中。"

                RecordingService.ACTION_STOP_RECORDING ->
                    "已送出停止錄音指令。"

                else -> "正在執行..."
            }

        } catch (e: Exception) {

            status.text =
                "啟動失敗：${e.message}"
        }
    }

    private fun startPlaybackService(
        resultCode: Int,
        data: Intent
    ) {

        try {

            val intent = Intent(
                this,
                AudioCaptureService::class.java
            ).apply {

                action =
                    AudioCaptureService.ACTION_START

                putExtra(
                    AudioCaptureService.EXTRA_RESULT_CODE,
                    resultCode
                )

                putExtra(
                    AudioCaptureService.EXTRA_RESULT_DATA,
                    data
                )
            }

            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            status.text =
                "系統音訊擷取服務啟動中。"

        } catch (e: Exception) {

            status.text =
                "啟動擷取服務失敗：${e.message}"
        }
    }

    @Deprecated("使用系統 Activity Result 回呼")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (requestCode == projectionRequestCode) {

            if (
                resultCode == RESULT_OK &&
                data != null
            ) {

                startPlaybackService(
                    resultCode,
                    data
                )

            } else {

                status.text =
                    "你取消了系統音訊擷取授權。"
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == this.requestCode) {

            val audioGranted =
                checkSelfPermission(
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

            if (audioGranted) {

                when (pendingAction) {

                    "PLAYBACK" -> {
                        requestPlaybackCapture()
                    }

                    else -> {
                        val action = pendingAction

                        if (
                            action ==
                            RecordingService.ACTION_ENABLE_AUTO ||
                            action ==
                            RecordingService.ACTION_MANUAL_START
                        ) {
                            sendServiceAction(action)
                        }
                    }
                }

            } else {

                Toast.makeText(
                    this,
                    "需要麥克風權限才能錄音。",
                    Toast.LENGTH_LONG
                ).show()
            }

            pendingAction = null
        }
    }
}
