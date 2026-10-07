package com.timo.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread


class MainActivity : Activity() {

    // =========================================================
    // تنظیمات TIMO
    // =========================================================

    private val ESP32_URL = "http://192.168.1.102"

    /*
     * کلید API خودت را همین‌جا قرار بده.
     * کلید قبلی خودت را دوباره وارد کن.
     */
    private val OPENAI_API_KEY = "YOUR_OPENAI_API_KEY"

    private val OPENAI_MODEL = "gpt-6-luna"


    // =========================================================
    // UI
    // =========================================================

    private lateinit var statusText: TextView
    private lateinit var conversationText: TextView
    private lateinit var inputText: EditText
    private lateinit var sendButton: Button
    private lateinit var micButton: Button


    // =========================================================
    // Speech Recognition
    // =========================================================

    private var speechRecognizer: SpeechRecognizer? = null
    private var listening = false


    // =========================================================
    // Text To Speech
    // =========================================================

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null


    // =========================================================
    // Audio
    // =========================================================

    private lateinit var audioManager: AudioManager


    // =========================================================
    // ON CREATE
    // =========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audioManager =
            getSystemService(AUDIO_SERVICE) as AudioManager

        createInterface()

        initializeTTS()

        initializeSpeechRecognizer()

        checkMicrophonePermission()

        checkESP32()
    }


    // =========================================================
    // ساخت رابط کاربری
    // =========================================================

    private fun createInterface() {

        val root = LinearLayout(this)

        root.orientation = LinearLayout.VERTICAL

        root.setPadding(
            24,
            24,
            24,
            24
        )

        // -----------------------------------------------------
        // وضعیت
        // -----------------------------------------------------

        statusText = TextView(this)

        statusText.text =
            "🔄 TIMO در حال آماده‌سازی..."

        statusText.textSize = 18f

        statusText.setPadding(
            0,
            0,
            0,
            20
        )

        root.addView(
            statusText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )


        // -----------------------------------------------------
        // مکالمه
        // -----------------------------------------------------

        conversationText = TextView(this)

        conversationText.textSize = 18f

        conversationText.setPadding(
            0,
            20,
            0,
            20
        )

        val scroll = ScrollView(this)

        scroll.addView(conversationText)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )


        // -----------------------------------------------------
        // ورودی متن
        // -----------------------------------------------------

        inputText = EditText(this)

        inputText.hint =
            "پیام خود را بنویسید..."

        inputText.textSize = 17f

        root.addView(
            inputText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )


        // -----------------------------------------------------
        // دکمه ارسال
        // -----------------------------------------------------

        sendButton = Button(this)

        sendButton.text = "ارسال"

        sendButton.setOnClickListener {

            val message =
                inputText.text.toString().trim()

            if (message.isNotEmpty()) {

                inputText.setText("")

                sendToOpenAI(message)
            }
        }

        root.addView(
            sendButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )


        // -----------------------------------------------------
        // دکمه میکروفون
        // -----------------------------------------------------

        micButton = Button(this)

        micButton.text =
            "🎤 صحبت با TIMO"

        micButton.setOnClickListener {

            if (listening) {
                stopListening()
            } else {
                startListening()
            }
        }

        root.addView(
            micButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )


        root.gravity = Gravity.CENTER

        setContentView(root)
    }


    // =========================================================
    // TTS
    // =========================================================

    private fun initializeTTS() {

        tts = TextToSpeech(
            this
        ) { result ->

            if (result != TextToSpeech.SUCCESS) {

                runOnUiThread {
                    statusText.text =
                        "❌ خطا در راه‌اندازی صدای TIMO"
                }

                return@TextToSpeech
            }


            val engine = tts ?: return@TextToSpeech


            // -------------------------------------------------
            // زبان
            // -------------------------------------------------

            var language =
                engine.setLanguage(
                    Locale("fa", "IR")
                )

            if (
                language == TextToSpeech.LANG_MISSING_DATA ||
                language == TextToSpeech.LANG_NOT_SUPPORTED
            ) {

                engine.setLanguage(
                    Locale.getDefault()
                )
            }


            // -------------------------------------------------
            // Audio Attributes
            // -------------------------------------------------

            engine.setAudioAttributes(

                AudioAttributes.Builder()

                    .setUsage(
                        AudioAttributes.USAGE_MEDIA
                    )

                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SPEECH
                    )

                    .build()
            )


            // -------------------------------------------------
            // Listener
            // -------------------------------------------------

            engine.setOnUtteranceProgressListener(

                object : UtteranceProgressListener() {

                    override fun onStart(
                        utteranceId: String?
                    ) {

                        runOnUiThread {

                            statusText.text =
                                "🔊 TIMO در حال صحبت است..."
                        }
                    }


                    override fun onDone(
                        utteranceId: String?
                    ) {

                        runOnUiThread {

                            statusText.text =
                                "🟢 TIMO آماده است"
                        }
                    }


                    override fun onError(
                        utteranceId: String?
                    ) {

                        runOnUiThread {

                            statusText.text =
                                "❌ خطا در پخش صدا"
                        }
                    }
                }
            )


            ttsReady = true


            runOnUiThread {

                statusText.text =
                    "🟢 TIMO آماده است"

                val pending =
                    pendingSpeech

                pendingSpeech = null

                if (!pending.isNullOrBlank()) {

                    speak(pending)
                }
            }
        }
    }


    // =========================================================
    // صحبت کردن TIMO
    // =========================================================

    private fun speak(text: String) {

        val cleanText =
            text
                .replace("*", "")
                .replace("#", "")
                .replace("`", "")
                .replace("_", "")
                .trim()


        if (cleanText.isEmpty()) {
            return
        }


        if (!ttsReady || tts == null) {

            pendingSpeech = cleanText

            statusText.text =
                "🔊 آماده‌سازی صدا..."

            return
        }


        val engine = tts ?: return


        /*
         * صدای قبلی را متوقف کن
         */
        engine.stop()


        /*
         * صدای TIMO روی MEDIA گوشی
         */
        val parameters = Bundle()

        parameters.putInt(
            TextToSpeech.Engine.KEY_PARAM_STREAM,
            AudioManager.STREAM_MUSIC
        )

        parameters.putFloat(
            TextToSpeech.Engine.KEY_PARAM_VOLUME,
            1.0f
        )


        statusText.text =
            "🔊 TIMO در حال صحبت است..."


        val result = engine.speak(

            cleanText,

            TextToSpeech.QUEUE_FLUSH,

            parameters,

            "TIMO_SPEECH"
        )


        if (result != TextToSpeech.SUCCESS) {

            statusText.text =
                "❌ TTS نتوانست صدا را پخش کند"
        }
    }


    // =========================================================
    // Speech Recognizer
    // =========================================================

    private fun initializeSpeechRecognizer() {

        if (
            !SpeechRecognizer.isRecognitionAvailable(
                this
            )
        ) {

            statusText.text =
                "❌ تشخیص گفتار در دسترس نیست"

            return
        }


        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(
                this
            )


        speechRecognizer?.setRecognitionListener(

            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {

                    runOnUiThread {

                        statusText.text =
                            "🎤 گوش می‌دهم..."
                    }
                }


                override fun onBeginningOfSpeech() {

                    runOnUiThread {

                        statusText.text =
                            "🎤 در حال شنیدن..."
                    }
                }


                override fun onEndOfSpeech() {

                    runOnUiThread {

                        statusText.text =
                            "🧠 در حال پردازش..."
                    }
                }


                override fun onResults(
                    results: Bundle?
                ) {

                    listening = false


                    val resultsList =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )


                    val spokenText =
                        resultsList
                            ?.firstOrNull()
                            ?.trim()


                    runOnUiThread {

                        micButton.text =
                            "🎤 صحبت با TIMO"


                        if (
                            !spokenText.isNullOrBlank()
                        ) {

                            sendToOpenAI(
                                spokenText
                            )

                        } else {

                            statusText.text =
                                "🟢 TIMO آماده است"
                        }
                    }
                }


                override fun onError(
                    error: Int
                ) {

                    listening = false

                    runOnUiThread {

                        micButton.text =
                            "🎤 صحبت با TIMO"

                        statusText.text =
                            "🟢 TIMO آماده است"
                    }
                }


                override fun onRmsChanged(
                    rmsdB: Float
                ) {
                }


                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {
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
    }


    // =========================================================
    // شروع شنیدن
    // =========================================================

    private fun startListening() {

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

            return
        }


        val recognizer =
            speechRecognizer ?: return


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
            RecognizerIntent.EXTRA_PARTIAL_RESULTS,
            false
        )


        intent.putExtra(
            RecognizerIntent.EXTRA_MAX_RESULTS,
            3
        )


        listening = true

        micButton.text =
            "⏹ توقف"


        recognizer.startListening(intent)
    }


    // =========================================================
    // توقف شنیدن
    // =========================================================

    private fun stopListening() {

        listening = false

        speechRecognizer?.stopListening()

        micButton.text =
            "🎤 صحبت با TIMO"

        statusText.text =
            "🟢 TIMO آماده است"
    }


    // =========================================================
    // ارسال به OpenAI
    // =========================================================

    private fun sendToOpenAI(
        userMessage: String
    ) {

        conversationText.append(
            "\n\n👤 شما:\n$userMessage\n"
        )


        statusText.text =
            "🧠 TIMO در حال فکر کردن..."


        sendButton.isEnabled = false

        micButton.isEnabled = false


        thread {

            var connection:
                    HttpURLConnection? = null


            try {

                val url =
                    URL(
                        "https://api.openai.com/v1/responses"
                    )


                connection =
                    url.openConnection()
                            as HttpURLConnection


                connection.requestMethod =
                    "POST"


                connection.connectTimeout =
                    30000


                connection.readTimeout =
                    60000


                connection.doOutput =
                    true


                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )


                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $OPENAI_API_KEY"
                )


                // ------------------------------------------------
                // درخواست OpenAI
                // ------------------------------------------------

                val request =
                    JSONObject()


                request.put(
                    "model",
                    OPENAI_MODEL
                )


                request.put(
                    "instructions",
                    """
                    تو TIMO هستی.
                    یک دستیار فارسی‌زبان دوستانه و طبیعی هستی.
                    همیشه فارسی جواب بده.
                    پاسخ را مستقیم و قابل فهم بده.
                    برای جواب‌های ساده کوتاه جواب بده.
                    از Markdown و علامت‌های اضافی استفاده نکن.
                    """.trimIndent()
                )


                request.put(
                    "input",
                    userMessage
                )


                connection.outputStream.use {

                    it.write(
                        request
                            .toString()
                            .toByteArray(
                                Charsets.UTF_8
                            )
                    )

                    it.flush()
                }


                val responseCode =
                    connection.responseCode


                val inputStream =

                    if (
                        responseCode in 200..299
                    ) {

                        connection.inputStream

                    } else {

                        connection.errorStream
                    }


                val response =
                    BufferedReader(
                        InputStreamReader(
                            inputStream,
                            Charsets.UTF_8
                        )
                    ).use {

                        buildString {

                            var line: String?

                            while (
                                it.readLine()
                                    .also {
                                        line = it
                                    } != null
                            ) {

                                append(line)
                            }
                        }
                    }


                if (
                    responseCode !in 200..299
                ) {

                    runOnUiThread {

                        statusText.text =
                            "❌ خطای OpenAI: $responseCode"

                        sendButton.isEnabled =
                            true

                        micButton.isEnabled =
                            true
                    }

                    return@thread
                }


                val answer =
                    extractAnswer(response)


                if (answer.isBlank()) {

                    runOnUiThread {

                        statusText.text =
                            "❌ پاسخ TIMO خالی بود"

                        sendButton.isEnabled =
                            true

                        micButton.isEnabled =
                            true
                    }

                    return@thread
                }


                // ------------------------------------------------
                // نمایش جواب و صحبت TIMO
                // ------------------------------------------------

                runOnUiThread {

                    conversationText.append(
                        "\n🤖 TIMO:\n$answer\n"
                    )


                    sendButton.isEnabled =
                        true

                    micButton.isEnabled =
                        true


                    speak(answer)
                }


            } catch (e: Exception) {

                runOnUiThread {

                    statusText.text =
                        "❌ خطا: ${e.message}"


                    sendButton.isEnabled =
                        true

                    micButton.isEnabled =
                        true
                }

            } finally {

                connection?.disconnect()
            }
        }
    }


    // =========================================================
    // استخراج جواب OpenAI
    // =========================================================

    private fun extractAnswer(
        jsonText: String
    ): String {

        return try {

            val json =
                JSONObject(jsonText)


            // حالت output_text
            val direct =
                json.optString(
                    "output_text",
                    ""
                )


            if (direct.isNotBlank()) {

                direct.trim()

            } else {

                // حالت output[]
                val output =
                    json.optJSONArray(
                        "output"
                    )


                if (output == null) {

                    ""

                } else {

                    val result =
                        StringBuilder()


                    for (
                        i in 0 until output.length()
                    ) {

                        val item =
                            output.optJSONObject(i)
                                ?: continue


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
                                part.optString(
                                    "type",
                                    ""
                                ) == "output_text"
                            ) {

                                result.append(
                                    part.optString(
                                        "text",
                                        ""
                                    )
                                )
                            }
                        }
                    }


                    result
                        .toString()
                        .trim()
                }
            }

        } catch (_: Exception) {

            ""
        }
    }


    // =========================================================
    // بررسی دسترسی میکروفون
    // =========================================================

    private fun checkMicrophonePermission() {

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


    // =========================================================
    // بررسی ESP32
    // =========================================================

    private fun checkESP32() {

        thread {

            try {

                val url =
                    URL(
                        "$ESP32_URL/ping"
                    )


                val connection =
                    url.openConnection()
                            as HttpURLConnection


                connection.requestMethod =
                    "GET"


                connection.connectTimeout =
                    3000


                connection.readTimeout =
                    3000


                val result =
                    if (
                        connection.responseCode == 200
                    ) {

                        connection.inputStream
                            .bufferedReader()
                            .use {
                                it.readText()
                            }

                    } else {

                        ""
                    }


                connection.disconnect()


                runOnUiThread {

                    if (
                        result.trim() == "PONG"
                    ) {

                        statusText.text =
                            "🟢 TIMO ONLINE ✓"

                    } else {

                        statusText.text =
                            "🟡 TIMO آماده است"
                    }
                }


            } catch (_: Exception) {

                runOnUiThread {

                    statusText.text =
                        "🟡 TIMO آماده است"
                }
            }
        }
    }


    // =========================================================
    // خروج
    // =========================================================

    override fun onDestroy() {

        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }


        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }


        speechRecognizer = null
        tts = null


        super.onDestroy()
    }
}
