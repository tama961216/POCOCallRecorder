package tw.pococallrecorder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val requestCode = 100
    private lateinit var status: TextView

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
            text = "測試版：目前提供麥克風錄音測試。\nAndroid 系統可能限制通話雙向錄音。"
            textSize = 16f
            setPadding(0, 28, 0, 28)
        }
        val start = Button(this).apply {
            text = "開始錄音測試"
            setOnClickListener { checkAndStart() }
        }
        val stop = Button(this).apply {
            text = "停止錄音"
            setOnClickListener {
                stopService(Intent(this@MainActivity, RecordingService::class.java))
                status.text = "已送出停止錄音指令。"
            }
        }
        layout.addView(title)
        layout.addView(status)
        layout.addView(start)
        layout.addView(stop)
        setContentView(layout)
    }

    private fun checkAndStart() {
        val permissions = arrayOf(Manifest.permission.RECORD_AUDIO)
        if (permissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(permissions, requestCode)
        } else {
            startRecorder()
        }
    }

    private fun startRecorder() {
        try {
            startForegroundService(Intent(this, RecordingService::class.java))
            status.text = "錄音中。請確認通知列的錄音通知。"
        } catch (e: Exception) {
            status.text = "無法開始：${e.message}"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == this.requestCode && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecorder()
        } else {
            Toast.makeText(this, "需要麥克風權限才能測試錄音", Toast.LENGTH_LONG).show()
        }
    }
}
