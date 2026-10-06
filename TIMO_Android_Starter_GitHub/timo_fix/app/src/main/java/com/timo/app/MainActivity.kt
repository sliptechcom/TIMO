 package com.timo.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var answer: TextView
    private lateinit var apiKey: EditText
    private lateinit var tts: TextToSpeech

    private val esp32Ip = "192.168.1.105"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
        }

        tts = TextToSpeech(this) {
            tts.language = Locale("fa", "IR")
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(40, 40, 40, 40)
        root.gravity = Gravity.CENTER_HORIZONTAL

        val title = TextView(this)
        title.text = "TIMO"
        title.textSize = 42f

        apiKey = EditText(this)
        apiKey.hint = "OpenAI API Key"
        apiKey.inputType = 0x00000081

        val speakButton = Button(this)
        speakButton.text = "🎤 TALK TO TIMO"

        val espButton = Button(this)
        espButton.text = "📡 TEST ESP32"

        status = TextView(this)
        status.text = "TIMO READY"
        status.textSize = 22f

        answer = TextView(this)
        answer.text = ""
        answer.textSize = 20f

        root.addView(title)
        root.addView(apiKey)
        root.addView(speakButton)
        root.addView(espButton)
        root.addView(status)
        root.addView(answer)

        setContentView(root)

        espButton.setOnClickListener {
            testEsp32()
        }

        speakButton.setOnClickListener {
            startListening()
        }
    }

    private fun startListening() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Speech recognition unavailable"
            return
        }

        status.text = "🎤 Listening..."

        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        val listener = object : android.speech.RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {}

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                status.text = "Processing..."
            }

            override fun onError(error: Int) {
                status.text = "Speech error: $error"
                recognizer.destroy()
            }

            override fun onResults(results: Bundle?) {

                val text = results
                    ?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION
                    )
                    ?.firstOrNull()

                recognizer.destroy()

                if (!text.isNullOrBlank()) {
                    answer.text = "You: $text"
                    askOpenAI(text)
                } else {
                    status.text = "I didn't hear anything"
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        recognizer.setRecognitionListener(listener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE,
            "fa-IR"
        )
        intent.putExtra(
            RecognizerIntent.EXTRA_PROMPT,
            "با TIMO صحبت کنید"
        )

        recognizer.startListening(intent)
    }

    private fun askOpenAI(question: String) {

        val key = apiKey.text.toString().trim()

        if (key.isEmpty()) {
            status.text = "Enter OpenAI API Key"
            return
        }

        status.text = "🤖 TIMO is thinking..."

        thread {

            try {

                val url = URL("https://api.openai.com/v1/responses")

                val connection =
                    url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.doOutput = true

                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $key"
                )

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                val json = JSONObject()

                json.put("model", "gpt-6-luna")

                json.put(
                    "instructions",
                    """
                    تو TIMO هستی؛ یک دستیار هوشمند فارسی‌زبان.
                    پاسخ‌ها را کوتاه، طبیعی و دوستانه بده.
                    اگر کاربر فارسی صحبت کرد، فارسی جواب بده.
                    """.trimIndent()
                )

                json.put("input", question)

                connection.outputStream.use {
                    it.write(json.toString().toByteArray(Charsets.UTF_8))
                }

                val code = connection.responseCode

                val stream =
                    if (code in 200..299)
                        connection.inputStream
                    else
                        connection.errorStream

                val response =
                    stream.bufferedReader().readText()

                connection.disconnect()

                if (code !in 200..299) {
                    runOnUiThread {
                        status.text = "OpenAI error: HTTP $code"
                    }
                    return@thread
                }

                val reply = extractResponseText(response)

                runOnUiThread {

                    status.text = "TIMO ONLINE ✓"

                    answer.text =
                        "TIMO:\n$reply"

                    speak(reply)
                }

            } catch (e: Exception) {

                runOnUiThread {
                    status.text =
                        "AI connection failed:\n${e.message}"
                }
            }
        }
    }

    private fun extractResponseText(jsonText: String): String {

        val json = JSONObject(jsonText)

        val output = json.optJSONArray("output")
            ?: return "No response"

        for (i in 0 until output.length()) {

            val item = output.optJSONObject(i)

            if (item?.optString("type") == "message") {

                val content =
                    item.optJSONArray("content")
                        ?: continue

                for (j in 0 until content.length()) {

                    val part =
                        content.optJSONObject(j)

                    if (part?.optString("type") == "output_text") {
                        return part.optString("text")
                    }
                }
            }
        }

        return "No text response"
    }

    private fun speak(text: String) {

        tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "TIMO_REPLY"
        )
    }

    private fun testEsp32() {

        status.text = "Connecting to ESP32..."

        thread {

            try {

                val url =
                    URL("http://$esp32Ip/")

                val connection =
                    url.openConnection() as HttpURLConnection

                connection.connectTimeout = 3000
                connection.readTimeout = 3000

                val code =
                    connection.responseCode

                connection.disconnect()

                runOnUiThread {

                    if (code in 200..299) {
                        status.text =
                            "TIMO ONLINE ✓\nESP32: HTTP $code"
                    } else {
                        status.text =
                            "ESP32 HTTP $code"
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {
                    status.text =
                        "ESP32 failed:\n${e.message}"
                }
            }
        }
    }

    override fun onDestroy() {

        tts.stop()
        tts.shutdown()

        super.onDestroy()
    }
}    
