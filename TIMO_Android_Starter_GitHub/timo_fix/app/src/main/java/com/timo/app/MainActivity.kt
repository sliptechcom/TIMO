package com.timo.app

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val ip=findViewById<EditText>(R.id.ipEdit)
        val button=findViewById<Button>(R.id.testButton)
        val status=findViewById<TextView>(R.id.status)
        val progress=findViewById<ProgressBar>(R.id.progress)
        button.setOnClickListener {
            val host=ip.text.toString().trim()
            status.text="Connecting..."
            thread {
                try {
                    val c=URL("http://$host/").openConnection() as HttpURLConnection
                    c.connectTimeout=3000; c.readTimeout=3000
                    val code=c.responseCode; c.disconnect()
                    runOnUiThread {
                        status.text=if(code in 200..299)
                            "TIMO ONLINE ✓\nESP32 responded: HTTP $code"
                        else "ESP32 responded: HTTP $code"
                        progress.progress=if(code in 200..299) 100 else 0
                    }
                } catch(e:Exception) {
                    runOnUiThread { status.text="Connection failed:\n${e.message ?: "unknown error"}" }
                }
            }
        }
    }
}
