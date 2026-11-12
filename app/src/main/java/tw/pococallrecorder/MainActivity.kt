
package tw.pococallrecorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val requestCode = 100
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
            text = "來電自動錄音測試版\n\n請先啟用自動偵測，讓服務在背景待命。"
            textSize = 16f
            setPadding(0, 28, 0, 28)
        }

        val autoStart = Button(this).apply {
            text = "啟用來電自動偵測"
            setOnClickListener {
                requestAndStart(RecordingService.ACTION_ENABLE_AUTO)
            }
        }

        val manualStart = Button(this).apply {
            text = "手動開始錄音（撥出電話使用）"
            setOnClickListener {
                requestAndStart(RecordingService.ACTION_MANUAL_START)
            }
        }

        val stopRecording = Button(this).apply {
            text = "停止錄音"
            setOnClickListener {
                sendServiceAction(RecordingService.ACTION_STOP_RECORDING)
            }
        }

        val disableAuto = Button(this).apply {
            text = "停止自動偵測"
            setOnClickListener {
                stopService(
                    Intent(this@MainActivity, RecordingService::class.java)
                )
                status.text = "自動偵測服務已停止。"
            }
        }

        layout.addView(title)
        layout.addView(status)
        layout.addView(autoStart)
        layout.addView(manualStart)
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
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            pendingAction = action
            requestPermissions(missing.toTypedArray(), requestCode)
        } else {
            sendServiceAction(action)
        }
    }

    private fun sendServiceAction(action: String) {
        try {
            val intent = Intent(this, RecordingService::class.java)
                .setAction(action)

            startForegroundService(intent)

            status.text = when (action) {
                RecordingService.ACTION_ENABLE_AUTO ->
                    "正在啟用來電自動偵測，請確認通知列。"

                RecordingService.ACTION_MANUAL_START ->
                    "正在啟動手動錄音。"

                RecordingService.ACTION_STOP_RECORDING ->
                    "已送出停止錄音指令。"

                else -> "正在執行..."
            }

        } catch (e: Exception) {
            status.text = "啟動失敗：${e.message}"
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
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED

            val phoneGranted =
                checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
                    PackageManager.PERMISSION_GRANTED

            if (audioGranted && phoneGranted) {
                pendingAction?.let {
                    sendServiceAction(it)
                }
            } else {
                Toast.makeText(
                    this,
                    "需要麥克風及電話狀態權限才能使用自動偵測。",
                    Toast.LENGTH_LONG
                ).show()
            }

            pendingAction = null
        }
    }
}
