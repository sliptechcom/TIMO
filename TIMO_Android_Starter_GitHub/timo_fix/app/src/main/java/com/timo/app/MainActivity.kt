package com.timo.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.File
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
    private var mediaPlayer: MediaPlayer? = null

    private val esp32Ip = "192.168.1.102"

    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()
        initializeTts()
        requestMicrophonePermission()
    }

    // =========================================================
    // USER INTERFACE
    // =========================================================

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

        statusText.text = "TIMO در حال آماده شدن..."
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

    // =========================================================
    // TTS INITIALIZATION
    // =========================================================

    private fun initializeTts() {

        tts = TextToSpeech(
            this,
            { result ->

                if (result == TextToSpeech.SUCCESS) {

                    try {

                        tts?.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(
                                    AudioAttributes.USAGE_MEDIA
                                )
                                .setContentType(
                                    AudioAttributes.CONTENT_TYPE_SPEECH
                                )
                                .build()
                        )

                        var languageResult =
                            tts?.setLanguage(
                                Locale("fa", "IR")
                            )

                        if (
                            languageResult ==
                            TextToSpeech.LANG_MISSING_DATA ||
                            languageResult ==
                            TextToSpeech.LANG_NOT_SUPPORTED
                        ) {

                            languageResult =
                                tts?.setLanguage(
                                    Locale("fa")
                                )
                        }

                        tts?.setSpeechRate(0.95f)
                        tts?.setPitch(1.0f)

                        tts?.setOnUtteranceProgressListener(
                            object : UtteranceProgressListener() {

                                override fun onStart(
                                    utteranceId: String?
                                ) {
                                }

                                override fun onDone(
                                    utteranceId: String?
                                ) {
                                }

                                override fun onError(
                                    utteranceId: String?
                                ) {
                                }
                            }
                        )

                        ttsReady = true

                        runOnUiThread {

                            statusText.text =
                                "TIMO آماده است ✓"
                        }

                    } catch (e: Exception) {

                        ttsReady = false

                        runOnUiThread {

                            statusText.text =
                                "خطای آماده‌سازی صدا"
                        }
                    }

                } else {

                    ttsReady = false

                    runOnUiThread {

                        statusText.text =
                            "موتور صدای TIMO آماده نشد"
                    }
                }
            },
            null
        )
    }

    // =========================================================
    // FINAL AUDIO SYSTEM
    //
    // TIMO:
    // Persian text
    //      ↓
    // Samsung TTS
    //      ↓
    // WAV file
    //      ↓
    // MediaPlayer
    //      ↓
    // Phone speaker / audio output
    // =========================================================

    private fun speak(text: String) {

        if (text.isBlank()) {
            return
        }

        if (!ttsReady || tts == null) {

            runOnUiThread {

                statusText.text =
                    "موتور صدای TIMO آماده نیست"
            }

            return
        }

        val cleanText =
            text
                .replace("*", "")
                .replace("#", "")
                .replace("`", "")
                .trim()

        val audioFile =
            File(
                cacheDir,
                "timo_reply.wav"
            )

        try {

            if (audioFile.exists()) {
                audioFile.delete()
            }

        } catch (_: Exception) {
        }

        val utteranceId =
            "TIMO_AUDIO_${System.currentTimeMillis()}"

        tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(
                    id: String?
                ) {
                }

                override fun onDone(
                    id: String?
                ) {

                    if (id != utteranceId) {
                        return
                    }

                    runOnUiThread {

                        playTimoAudio(
                            audioFile
                        )
                    }
                }

                override fun onError(
                    id: String?
                ) {

                    if (id != utteranceId) {
                        return
                    }

                    runOnUiThread {

                        statusText.text =
                            "خطا در ساخت صدای TIMO"
                    }
                }
            }
        )

        try {

            val params =
                Bundle()

            params.putInt(
                TextToSpeech.Engine.KEY_PARAM_STREAM,
                AudioManager.STREAM_MUSIC
            )

            val result =
                tts?.synthesizeToFile(
                    cleanText,
                    params,
                    audioFile,
                    utteranceId
                )

            if (result != TextToSpeech.SUCCESS) {

                statusText.text =
                    "TTS نتوانست صدای TIMO را بسازد"
            } else {

                statusText.text =
                    "🔊 در حال آماده‌سازی صدای TIMO..."
            }

        } catch (e: Exception) {

            runOnUiThread {

                statusText.text =
                    "خطای ساخت صدا: ${e.message}"
            }
        }
    }

    // =========================================================
    // PLAY GENERATED AUDIO
    // =========================================================

    private fun playTimoAudio(
        file: File
    ) {

        if (!file.exists()) {

            statusText.text =
                "فایل صدای TIMO ساخته نشد"

            return
        }

        try {

            mediaPlayer?.stop()

        } catch (_: Exception) {
        }

        try {

            mediaPlayer?.release()

        } catch (_: Exception) {
        }

        mediaPlayer = null

        try {

            val audioManager =
                getSystemService(
                    AUDIO_SERVICE
                ) as AudioManager

            val volume =
                audioManager.getStreamVolume(
                    AudioManager.STREAM_MUSIC
                )

            if (volume == 0) {

                statusText.text =
                    "🔊 صدای Media گوشی صفر است"

                return
            }

            val player =
                MediaPlayer()

            mediaPlayer = player

            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(
                        AudioAttributes.USAGE_MEDIA
                    )
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SPEECH
                    )
                    .build()
            )

            player.setDataSource(
                file.absolutePath
            )

            player.setOnPreparedListener {

                statusText.text =
                    "🔊 TIMO در حال صحبت است..."

                it.start()
            }

            player.setOnCompletionListener {

                statusText.text =
                    "TIMO ONLINE ✓"

                it.release()

                if (mediaPlayer == it) {
                    mediaPlayer = null
                }
            }

            player.setOnErrorListener { mp, _, _ ->

                statusText.text =
                    "خطا در پخش صدای TIMO"

                mp.release()

                if (mediaPlayer == mp) {
                    mediaPlayer = null
                }

                true
            }

            player.prepareAsync()

        } catch (e: Exception) {

            statusText.text =
                "خطای پخش صدا: ${e.message}"
        }
    }

    // =========================================================
    // MICROPHONE PERMISSION
    // =========================================================

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

    // =========================================================
    // SPEECH RECOGNITION
    // =========================================================

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

                    if (
                        !text.isNullOrBlank()
                    ) {

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

    // =========================================================
    // OPENAI
    // =========================================================

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
                    if (
                        responseCode in 200..299
                    ) {

                        connection.inputStream

                    } else {

                        connection.errorStream
                    }

                val response =
                    responseStream
                        ?.bufferedReader()
                        ?.use {
                            it.readText()
                        }
                        ?: ""

                if (
                    responseCode !in 200..299
                ) {

                    runOnUiThread {

                        talkButton.isEnabled =
                            true

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

                    talkButton.isEnabled =
                        true

                    statusText.text =
                        "TIMO ONLINE ✓"

                    conversationText.text =
                        "شما:\n$question\n\nTIMO:\n$reply"

                    speak(reply)
                }

            } catch (e: Exception) {

                runOnUiThread {

                    talkButton.isEnabled =
                        true

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

    // =========================================================
    // OPENAI RESPONSE PARSER
    // =========================================================

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

            if (
                direct.isNotBlank()
            ) {

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

    // =========================================================
    // ESP32
    // =========================================================

    private fun checkEsp32() {

        statusText.text =
            "📡 بررسی TIMO..."

        thread {

            var connection:
                HttpURLConnection? = null

            try {

                val url =
                    URL(
                        "http://$esp32Ip/ping"
                    )

                connection =
                    url.openConnection()
                        as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.useCaches = false

                val code =
                    connection.responseCode

                val response =
                    if (
                        code in 200..299
                    ) {

                        connection.inputStream
                            .bufferedReader()
                            .use {
                                it.readText()
                            }

                    } else {

                        connection.errorStream
                            ?.bufferedReader()
                            ?.use {
                                it.readText()
                            }
                            ?: ""
                    }

                runOnUiThread {

                    if (
                        code == 200 &&
                        response.trim() == "PONG"
                    ) {

                        statusText.text =
                            "TIMO ONLINE ✓\nESP32: PONG"

                    } else {

                        statusText.text =
                            "ESP32: HTTP $code\n$response"
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    statusText.text =
                        "ESP32 ERROR:\n${e.message}"
                }

            } finally {

                connection?.disconnect()
            }
        }
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    override fun onDestroy() {

        recognizer?.destroy()
        recognizer = null

        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }

        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }

        mediaPlayer = null

        tts?.stop()
        tts?.shutdown()
        tts = null

        super.onDestroy()
    }
}
