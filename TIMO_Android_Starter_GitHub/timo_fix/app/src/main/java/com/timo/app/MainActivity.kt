package com.timo.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
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
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    // ============================================================
    // TIMO
    // ============================================================

    private val esp32Url = "http://192.168.1.102"

    // کلید API خودت را همین‌جا نگه دار
    private val openAiApiKey = "YOUR_OPENAI_API_KEY"

    private val openAiModel = "gpt-6-luna"

    // ============================================================
    // UI
    // ============================================================

    private lateinit var statusText: TextView
    private lateinit var conversationText: TextView
    private lateinit var inputText: EditText
    private lateinit var sendButton: Button
    private lateinit var micButton: Button

    // ============================================================
    // Speech Recognition
    // ============================================================

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    // ============================================================
    // TTS
    // ============================================================

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

    // ============================================================
    // Audio
    // ============================================================

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    // ============================================================
    // ON CREATE
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        createUI()
        setupTTS()
        setupSpeechRecognizer()

        checkEsp32()

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1001
            )
        }
    }

    // ============================================================
    // UI
    // ============================================================

    private fun createUI() {

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(24, 24, 24, 24)

        statusText = TextView(this)
        statusText.text = "🔄 TIMO در حال آماده‌سازی..."
        statusText.textSize = 18f
        statusText.setPadding(0, 0, 0, 20)

        root.addView(
            statusText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        conversationText = TextView(this)
        conversationText.textSize = 18f
        conversationText.setPadding(0, 20, 0, 20)

        val scrollView = ScrollView(this)

        scrollView.addView(conversationText)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        inputText = EditText(this)
        inputText.hint = "پیام خود را بنویسید..."
        inputText.textSize = 17f

        root.addView(
            inputText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        sendButton = Button(this)
        sendButton.text = "ارسال"

        sendButton.setOnClickListener {

            val text = inputText.text.toString().trim()

            if (text.isNotEmpty()) {
                inputText.setText("")
                sendToTimo(text)
            }
        }

        root.addView(
            sendButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        micButton = Button(this)
        micButton.text = "🎤 صحبت با TIMO"

        micButton.setOnClickListener {

            if (isListening) {
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

    // ============================================================
    // TTS SETUP
    // ============================================================

    private fun setupTTS() {

        tts = TextToSpeech(this) { result ->

            if (result != TextToSpeech.SUCCESS) {

                runOnUiThread {
                    statusText.text = "❌ خطا در راه‌اندازی صدای TIMO"
                }

                return@TextToSpeech
            }

            val engine = tts ?: return@TextToSpeech

            // اول زبان پیش‌فرض گوشی
            var languageResult =
                engine.setLanguage(Locale.getDefault())

            // اگر مناسب نبود، فارسی
            if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                languageResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                languageResult = engine.setLanguage(
                    Locale("fa", "IR")
                )
            }

            /*
             * تنظیم AudioAttributes
             */
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SPEECH
                    )
                    .build()
            )

            /*
             * Listener قبل از هر speak نصب می‌شود.
             */
            engine.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {

                    override fun onStart(utteranceId: String?) {

                        runOnUiThread {
                            statusText.text =
                                "🔊 TIMO در حال صحبت است..."
                        }
                    }

                    override fun onDone(utteranceId: String?) {

                        releaseAudioFocus()

                        runOnUiThread {
                            statusText.text =
                                "🟢 TIMO آماده است"
                        }
                    }

                    override fun onError(utteranceId: String?) {

                        releaseAudioFocus()

                        runOnUiThread {
                            statusText.text =
                                "❌ خطا در پخش صدای TIMO"
                        }
                    }
                }
            )

            ttsReady = true

            runOnUiThread {

                statusText.text =
                    "🟢 TIMO آماده است"

                pendingSpeech?.let {
                    pendingSpeech = null
                    speak(it)
                }
            }
        }
    }

    // ============================================================
    // AUDIO FOCUS
    // ============================================================

    private fun requestAudioFocus(): Boolean {

        return try {

            val attributes =
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SPEECH
                    )
                    .build()

            audioFocusRequest =
                AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
                    .setAudioAttributes(attributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { }
                    .build()

            audioManager.requestAudioFocus(
                audioFocusRequest!!
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        } catch (e: Exception) {

            false
        }
    }

    private fun releaseAudioFocus() {

        try {

            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }

        } catch (_: Exception) {
        }
    }

    // ============================================================
    // SPEAK
    // ============================================================

    private fun speak(text: String) {

        val cleanText = text
            .replace("*", "")
            .replace("#", "")
            .replace("`", "")
            .replace("_", "")
            .trim()

        if (cleanText.isBlank()) return

        if (!ttsReady || tts == null) {

            pendingSpeech = cleanText

            runOnUiThread {
                statusText.text =
                    "🔊 TIMO در حال آماده‌سازی صدا..."
            }

            return
        }

        val engine = tts ?: return

        runOnUiThread {
            statusText.text =
                "🔊 TIMO در حال صحبت است..."
        }

        /*
         * گرفتن Audio Focus
         */
        requestAudioFocus()

        /*
         * قطع گفتار قبلی
         */
        engine.stop()

        /*
         * بسیار مهم:
         * خروجی صدا روی STREAM_MUSIC
         */
        val params = Bundle()

        params.putInt(
            TextToSpeech.Engine.KEY_PARAM_STREAM,
            AudioManager.STREAM_MUSIC
        )

        params.putFloat(
            TextToSpeech.Engine.KEY_PARAM_VOLUME,
            1.0f
        )

        val result = engine.speak(
            cleanText,
            TextToSpeech.QUEUE_FLUSH,
            params,
            "TIMO_REPLY"
        )

        if (result != TextToSpeech.SUCCESS) {

            releaseAudioFocus()

            runOnUiThread {
                statusText.text =
                    "❌ ارسال صدا به خروجی گوشی انجام نشد"
            }
        }
    }

    // ============================================================
    // SPEECH RECOGNIZER
    // ============================================================

    private fun setupSpeechRecognizer() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {

            runOnUiThread {
                statusText.text =
                    "❌ تشخیص گفتار روی گوشی در دسترس نیست"
            }

            return
        }

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {

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

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {

                    runOnUiThread {
                        statusText.text =
                            "⏳ در حال پردازش..."
                    }
                }

                override fun onError(error: Int) {

                    isListening = false

                    runOnUiThread {
                        micButton.text =
                            "🎤 صحبت با TIMO"

                        statusText.text =
                            "🟢 TIMO آماده است"
                    }
                }

                override fun onResults(results: Bundle?) {

                    isListening = false

                    val matches =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    val text =
                        matches?.firstOrNull()?.trim()

                    runOnUiThread {

                        micButton.text =
                            "🎤 صحبت با TIMO"

                        if (!text.isNullOrBlank()) {

                            sendToTimo(text)
                        } else {

                            statusText.text =
                                "🟢 TIMO آماده است"
                        }
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
    }

    // ============================================================
    // START LISTENING
    // ============================================================

    private fun startListening() {

        if (checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1001
            )

            return
        }

        val recognizer = speechRecognizer ?: return

        val intent =
            android.content.Intent(
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

        isListening = true

        micButton.text = "⏹ توقف"

        recognizer.startListening(intent)
    }

    // ============================================================
    // STOP LISTENING
    // ============================================================

    private fun stopListening() {

        isListening = false

        speechRecognizer?.stopListening()

        micButton.text =
            "🎤 صحبت با TIMO"

        statusText.text =
            "🟢 TIMO آماده است"
    }

    // ============================================================
    // SEND TO OPENAI
    // ============================================================

    private fun sendToTimo(userText: String) {

        conversationText.append(
            "\n\n👤 شما:\n$userText\n"
        )

        statusText.text =
            "🧠 TIMO در حال فکر کردن..."

        sendButton.isEnabled = false
        micButton.isEnabled = false

        thread {

            try {

                val url =
                    URL(
                        "https://api.openai.com/v1/responses"
                    )

                val connection =
                    url.openConnection()
                            as HttpURLConnection

                connection.requestMethod = "POST"

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )

                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $openAiApiKey"
                )

                connection.doOutput = true

                connection.connectTimeout = 30000
                connection.readTimeout = 60000

                /*
                 * دستور اصلی TIMO
                 */
                val instructions = """
                    تو TIMO هستی؛ یک دستیار فارسی‌زبان دوستانه و کاربردی.
                    همیشه به فارسی پاسخ بده.
                    پاسخ‌ها را طبیعی، کوتاه و قابل فهم بده.
                    اگر سؤال ساده است، مستقیم جواب بده.
                    از Markdown سنگین و علامت‌های اضافی استفاده نکن.
                """.trimIndent()

                val body =
                    JSONObject().apply {

                        put(
                            "model",
                            openAiModel
                        )

                        put(
                            "instructions",
                            instructions
                        )

                        put(
                            "input",
                            userText
                        )
                    }

                connection.outputStream.use { output ->

                    output.write(
                        body.toString()
                            .toByteArray(Charsets.UTF_8)
                    )

                    output.flush()
                }

                val responseCode =
                    connection.responseCode

                val stream =
                    if (responseCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                val reader =
                    BufferedReader(
                        InputStreamReader(
                            stream,
                            Charsets.UTF_8
                        )
                    )

                val responseText =
                    buildString {

                        var line: String?

                        while (
                            reader.readLine()
                                .also { line = it } != null
                        ) {
                            append(line)
                        }
                    }

                reader.close()
                connection.disconnect()

                if (responseCode !in 200..299) {

                    runOnUiThread {

                        statusText.text =
                            "❌ خطای OpenAI: $responseCode"

                        sendButton.isEnabled = true
                        micButton.isEnabled = true
                    }

                    return@thread
                }

                val answer =
                    extractOpenAIText(responseText)

                if (answer.isBlank()) {

                    runOnUiThread {

                        statusText.text =
                            "❌ پاسخ TIMO خالی بود"

                        sendButton.isEnabled = true
                        micButton.isEnabled = true
                    }

                    return@thread
                }

                runOnUiThread {

                    conversationText.append(
                        "\n🤖 TIMO:\n$answer\n"
                    )

                    sendButton.isEnabled = true
                    micButton.isEnabled = true

                    /*
                     * بعد از دریافت جواب،
                     * TIMO همان جواب را می‌خواند.
                     */
                    speak(answer)
                }

            } catch (e: Exception) {

                runOnUiThread {

                    statusText.text =
                        "❌ خطای ارتباط: ${e.message}"

                    sendButton.isEnabled = true
                    micButton.isEnabled = true
                }
            }
        }
    }

    // ============================================================
    // PARSE OPENAI RESPONSE
    // ============================================================

    private fun extractOpenAIText(
        jsonString: String
    ): String {

        try {

            val json =
                JSONObject(jsonString)

            /*
             * حالت ساده:
             * output_text
             */
            val directText =
                json.optString("output_text", "")

            if (directText.isNotBlank()) {
                return directText.trim()
            }

            /*
             * حالت Responses API:
             * output -> message -> content -> output_text
             */
            val output =
                json.optJSONArray("output")
                    ?: return ""

            val result =
                StringBuilder()

            for (i in 0 until output.length()) {

                val item =
                    output.optJSONObject(i)
                        ?: continue

                val content =
                    item.optJSONArray("content")
                        ?: continue

                for (j in 0 until content.length()) {

                    val part =
                        content.optJSONObject(j)
                            ?: continue

                    val type =
                        part.optString("type", "")

                    if (type == "output_text") {

                        val text =
                            part.optString("text", "")

                        if (text.isNotBlank()) {
                            result.append(text)
                        }
                    }
                }
            }

            return result.toString().trim()

        } catch (_: Exception) {

            return ""
        }
    }

    // ============================================================
    // ESP32 CHECK
    // ============================================================

    private fun checkEsp32() {

        thread {

            try {

                val url =
                    URL("$esp32Url/ping")

                val connection =
                    url.openConnection()
                            as HttpURLConnection

                connection.requestMethod = "GET"

                connection.connectTimeout = 3000
                connection.readTimeout = 3000

                val code =
                    connection.responseCode

                val result =
                    if (code == 200) {

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

                    if (result.trim() == "PONG") {

                        statusText.text =
                            "🟢 TIMO ONLINE ✓"

                    } else {

                        statusText.text =
                            "🟡 TIMO آنلاین نیست"
                    }
                }

            } catch (_: Exception) {

                runOnUiThread {

                    statusText.text =
                        "🟡 TIMO آنلاین نیست"
                }
            }
        }
    }

    // ============================================================
    // DESTROY
    // ============================================================

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

        releaseAudioFocus()

        speechRecognizer = null
        tts = null

        super.onDestroy()
    }
}
