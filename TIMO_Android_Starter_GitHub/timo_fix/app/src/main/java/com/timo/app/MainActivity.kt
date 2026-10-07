package com.timo.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var conversationText: TextView
    private lateinit var apiKeyEdit: EditText
    private lateinit var talkButton: Button

    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null

    private val esp32Ip = "192.168.1.102"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()
        initializeTts()
        requestMicrophonePermission()
    }

    private fun createInterface() {

        val root = LinearLayout(this)

        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(32, 32, 32, 32)

        val title = TextView(this)

        title.text = "TIMO"
        title.textSize = 42f
        title.gravity = Gravity.CENTER

        val subtitle = TextView(this)

        subtitle.text = "هوش مصنوعی شخصی شما"
        subtitle.textSize = 18f
        subtitle.gravity = Gravity.CENTER

        apiKeyEdit = EditText(this)

        apiKeyEdit.hint = "OpenAI API Key"

        apiKeyEdit.inputType =
            InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_PASSWORD

        talkButton = Button(this)

        talkButton.text = "🎤  صحبت با TIMO"
        talkButton.textSize = 18f

        val espButton = Button(this)

        espButton.text = "📡  وضعیت TIMO"

        statusText = TextView(this)

        statusText.text = "TIMO آماده است"
        statusText.textSize = 19f
        statusText.gravity = Gravity.CENTER
        statusText.setPadding(0, 20, 0, 20)

        conversationText = TextView(this)

        conversationText.text = ""
        conversationText.textSize = 19f
        conversationText.setPadding(10, 20, 10, 20)

        val scroll = ScrollView(this)

        scroll.addView(conversationText)

        root.addView(title)
        root.addView(subtitle)
        root.addView(apiKeyEdit)
        root.addView(talkButton)
        root.addView(espButton)
        root.addView(statusText)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        talkButton.setOnClickListener {
            startListening()
        }

        espButton.setOnClickListener {
            checkEsp32()
        }
    }

    private fun initializeTts() {

        tts = TextToSpeech(this) { result ->

            if (result == TextToSpeech.SUCCESS) {

                val languageResult =
                    tts?.setLanguage(
                        Locale("fa", "IR")
                    )

                if (
                    languageResult ==
                    TextToSpeech.LANG_MISSING_DATA ||
                    languageResult ==
                    TextToSpeech.LANG_NOT_SUPPORTED
                ) {

                    statusText.text =
                        "صدای فارسی روی گوشی نصب نیست"
                }
            }
        }
    }

    private fun speak(text: String) {

        if (text.isBlank()) {
            return
        }

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "TIMO_REPLY"
        )
    }

    private fun requestMicrophonePermission() {

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            requestPermissions(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO
                ),
                100
            )
        }
    }

    private fun startListening() {

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            requestMicrophonePermission()
            return
        }

        if (
            !SpeechRecognizer.isRecognitionAvailable(
                this
            )
        ) {

            statusText.text =
                "سرویس تشخیص صدا در گوشی موجود نیست"

            return
        }

        recognizer?.destroy()

        recognizer =
            SpeechRecognizer.createSpeechRecognizer(
                this
            )

        recognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {

                    statusText.text =
                        "🎤 آماده‌ام، صحبت کنید..."
                }

                override fun onBeginningOfSpeech() {

                    statusText.text =
                        "🎤 دارم گوش می‌کنم..."
                }

                override fun onRmsChanged(
                    rmsdB: Float
                ) {
                }

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {
                }

                override fun onEndOfSpeech() {

                    statusText.text =
                        "🤖 در حال فکر کردن..."
                }

                override fun onError(
                    error: Int
                ) {

                    statusText.text =
                        speechError(error)

                    recognizer?.destroy()
                    recognizer = null
                }

                override fun onResults(
                    results: Bundle?
                ) {

                    val text =
                        results
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()

                    recognizer?.destroy()
                    recognizer = null

                    if (!text.isNullOrBlank()) {

                        conversationText.text =
                            "شما:\n$text"

                        askOpenAI(text)

                    } else {

                        statusText.text =
                            "متوجه صحبت شما نشدم"
                    }
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {
                }
            }
        )

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            )

        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE,
            "fa-IR"
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
            "fa-IR"
        )

        intent.putExtra(
            RecognizerIntent.EXTRA_PROMPT,
            "با TIMO صحبت کنید"
        )

        recognizer?.startListening(intent)
    }

    private fun speechError(
        error: Int
    ): String {

        return when (error) {

            SpeechRecognizer.ERROR_AUDIO ->
                "خطا در میکروفن"

            SpeechRecognizer.ERROR_CLIENT ->
                "خطای برنامه"

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "اجازه میکروفن داده نشده"

            SpeechRecognizer.ERROR_NETWORK ->
                "خطای اینترنت"

            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "اتصال اینترنت زمان‌بر شد"

            SpeechRecognizer.ERROR_NO_MATCH ->
                "صدای واضح دریافت نشد"

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "تشخیص صدا مشغول است"

            SpeechRecognizer.ERROR_SERVER ->
                "خطای سرویس تشخیص صدا"

            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "صدایی شنیده نشد"

            else ->
                "خطای تشخیص صدا: $error"
        }
    }

    private fun askOpenAI(
        question: String
    ) {

        val key =
            apiKeyEdit.text
                .toString()
                .trim()

        if (key.isEmpty()) {

            statusText.text =
                "ابتدا OpenAI API Key را وارد کنید"

            return
        }

        talkButton.isEnabled = false

        statusText.text =
            "🤖 TIMO در حال فکر کردن..."

        thread {

            var connection: HttpURLConnection? = null

            try {

                val url =
                    URL(
                        "https://api.openai.com/v1/responses"
                    )

                connection =
                    url.openConnection()
                        as HttpURLConnection

                connection.requestMethod = "POST"

                connection.connectTimeout = 15000

                connection.readTimeout = 60000

                connection.doOutput = true

                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $key"
                )

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                val request =
                    JSONObject().apply {

                        put(
                            "model",
                            "gpt-6-luna"
                        )

                        put(
                            "instructions",
                            """
                            تو TIMO هستی.
                            یک دستیار هوشمند فارسی‌زبان،
                            طبیعی، دوستانه و سریع.

                            همیشه به زبان کاربر پاسخ بده.

                            اگر کاربر فارسی صحبت کرد،
                            پاسخ را فارسی بده.

                            پاسخ‌ها را برای گفتار صوتی
                            کوتاه و طبیعی نگه دار.

                            از Markdown، جدول و علامت‌های
                            غیرضروری استفاده نکن.

                            اگر سؤال ساده است،
                            کوتاه جواب بده.
                            """.trimIndent()
                        )

                        put(
                            "input",
                            question
                        )
                    }

                connection.outputStream.use { output ->

                    output.write(
                        request
                            .toString()
                            .toByteArray(
                                Charsets.UTF_8
                            )
                    )
                }

                val responseCode =
                    connection.responseCode

                val responseStream =
                    if (responseCode in 200..299) {

                        connection.inputStream

                    } else {

                        connection.errorStream
                    }

                val response =
                    responseStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                if (responseCode !in 200..299) {

                    runOnUiThread {

                        talkButton.isEnabled = true

                        statusText.text =
                            "خطای OpenAI: HTTP $responseCode"

                        conversationText.text =
                            response
                    }

                    return@thread
                }

                val reply =
                    extractReply(response)

                runOnUiThread {

                    talkButton.isEnabled = true

                    statusText.text =
                        "TIMO ONLINE ✓"

                    conversationText.text =
                        "شما:\n$question\n\nTIMO:\n$reply"

                    speak(reply)
                }

            } catch (e: Exception) {

                runOnUiThread {

                    talkButton.isEnabled = true

                    statusText.text =
                        "خطا در ارتباط با TIMO"

                    conversationText.text =
                        "جزئیات خطا:\n${e.message}"
                }

            } finally {

                connection?.disconnect()
            }
        }
    }

    private fun extractReply(
        responseText: String
    ): String {

        return try {

            val json =
                JSONObject(responseText)

            val direct =
                json.optString(
                    "output_text"
                )

            if (direct.isNotBlank()) {

                direct

            } else {

                val output =
                    json.optJSONArray(
                        "output"
                    )

                if (output == null) {

                    "پاسخی دریافت نشد."

                } else {

                    var result =
                        "پاسخ متنی دریافت نشد."

                    for (
                        i in 0 until output.length()
                    ) {

                        val item =
                            output.optJSONObject(i)
                                ?: continue

                        if (
                            item.optString("type")
                            != "message"
                        ) {
                            continue
                        }

                        val content =
                            item.optJSONArray(
                                "content"
                            )
                                ?: continue

                        for (
                            j in 0 until content.length()
                        ) {

                            val part =
                                content.optJSONObject(j)
                                    ?: continue

                            if (
                                part.optString("type")
                                == "output_text"
                            ) {

                                val text =
                                    part.optString(
                                        "text"
                                    )

                                if (
                                    text.isNotBlank()
                                ) {

                                    result = text
                                    break
                                }
                            }
                        }

                        if (
                            result !=
                            "پاسخ متنی دریافت نشد."
                        ) {
                            break
                        }
                    }

                    result
                }
            }

        } catch (e: Exception) {

            "خطا در خواندن پاسخ TIMO."
        }
    }

    private fun checkEsp32() {

        statusText.text =
            "📡 بررسی TIMO..."

        thread {

            try {

                val url =
                    URL(
                        "http://$esp32Ip/"
                    )

                val connection =
                    url.openConnection()
                        as HttpURLConnection

                connection.connectTimeout = 3000

                connection.readTimeout = 3000

                val code =
                    connection.responseCode

                connection.disconnect()

                runOnUiThread {

                    if (code in 200..299) {

                        statusText.text =
                            "TIMO ONLINE ✓\nESP32: HTTP $code"

                    } else {

                        statusText.text =
                            "ESP32 HTTP $code"
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    statusText.text =
                        "ارتباط با ESP32 برقرار نشد"
                }
            }
        }
    }

    override fun onDestroy() {

        recognizer?.destroy()
        recognizer = null

        tts?.stop()
        tts?.shutdown()
        tts = null

        super.onDestroy()
    }
}
