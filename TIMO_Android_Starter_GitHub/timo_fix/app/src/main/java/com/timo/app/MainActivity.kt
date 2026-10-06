package com.timo.app

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val ip = findViewById<EditText>(R.id.ipEdit)
        val button = findViewById<Button>(R.id.testButton)
        val status = findViewById<TextView>(R.id.status)
        val progress = findViewById<ProgressBar>(R.id.progress)

        button.setOnClickListener {

            val host = ip.text.toString().trim()

            if (host.isEmpty()) {
                status.text = "Please enter ESP32 IP"
                return@setOnClickListener
            }

            running = true
            status.text = "Connecting..."
            progress.progress = 0

            thread {

                try {
                    val url = URL("http://$host/")
                    val connection = url.openConnection() as HttpURLConnection

                    connection.connectTimeout = 3000
                    connection.readTimeout = 3000
                    connection.requestMethod = "GET"

                    val code = connection.responseCode
                    connection.disconnect()

                    runOnUiThread {

                        if (code in 200..299) {
                            status.text = "TIMO ONLINE ✓\nESP32 responded: HTTP $code"
                            progress.progress = 100

                            startMicMonitor(host, status, progress)

                        } else {
                            status.text = "ESP32 responded: HTTP $code"
                            progress.progress = 0
                        }
                    }

                } catch (e: Exception) {

                    runOnUiThread {
                        status.text =
                            "Connection failed:\n${e.message ?: "Unknown error"}"
                        progress.progress = 0
                    }
                }
            }
        }
    }

    private fun startMicMonitor(
        host: String,
        status: TextView,
        progress: ProgressBar
    ) {

        thread {

            while (running) {

                try {

                    val url = URL("http://$host/level")
                    val connection =
                        url.openConnection() as HttpURLConnection

                    connection.connectTimeout = 2000
                    connection.readTimeout = 2000
                    connection.requestMethod = "GET"

                    val value = connection.inputStream
                        .bufferedReader()
                        .readText()
                        .trim()
                        .toIntOrNull() ?: 0

                    connection.disconnect()

                    val level = value.coerceIn(0, 1000)

                    runOnUiThread {

                        status.text =
                            "TIMO ONLINE ✓\n" +
                            "ESP32: HTTP 200\n" +
                            "MIC LEVEL: $level"

                        progress.progress = level
                    }

                    Thread.sleep(150)

                } catch (e: Exception) {

                    runOnUiThread {

                        status.text =
                            "TIMO ONLINE ✓\n" +
                            "MIC connection lost"
                    }

                    Thread.sleep(1000)
                }
            }
        }
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }
}
