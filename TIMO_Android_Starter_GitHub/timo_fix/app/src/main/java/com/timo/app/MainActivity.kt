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
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread


class MainActivity : Activity() {

    // =========================================================
    // SETTINGS
    // =========================================================

    private val ESP32_URL = "http://192.168.1.102"

    private val OPENAI_API_KEY =
        "YOUR_OPENAI_API_KEY"

    private val OPENAI_MODEL =
        "gpt-6-luna"


    // =========================================================
    // UI
    // =========================================================

    private lateinit var statusText: TextView
    private lateinit var conversationText: TextView
    private lateinit var inputText: EditText
    private lateinit var sendButton: Button
    private lateinit var micButton: Button


    // =========================================================
    // SPEECH RECOGNITION
    // =========================================================

    private var speechRecognizer: SpeechRecognizer? = null
    private var listening = false


    // =========================================================
    // TTS
    // =========================================================

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null


    // =========================================================
    // AUDIO PLAYER
    // =========================================================

    private var mediaPlayer: MediaPlayer? = null
    private var audioFile: File? = null


    // =========================================================
    // ON CREATE
    // =========================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createInterface()

        initializeTTS()

        initializeSpeechRecognizer()

        checkMicrophonePermission()

        checkESP32()
    }


    // =========================================================
    // UI
    // =========================================================

    private fun createInterface() {

        val root = LinearLayout(this)

        root.orientation =
            LinearLayout.VERTICAL

        root.setPadding(
            24,
            24,
            24,
            24
        )


        // STATUS

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


        // CONVERSATION

        conversationText = TextView(this)

        conversationText.textSize = 18f

        conversationText.setPadding(
            0,
            20,
            0,
            20
        )

        val scroll =
            ScrollView(this)

        scroll.addView(
            conversationText
        )

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )


        // INPUT

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


        // SEND

        sendButton = Button(this)

        sendButton.text =
            "ارسال"

        sendButton.setOnClickListener {

            val message =
                inputText.text
                    .toString()
                    .trim()

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


        // MICROPHONE

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


        root.gravity =
            Gravity.CENTER


        setContentView(root)
    }


    // =========================================================
    // TTS INITIALIZATION
    // =========================================================

    private fun initializeTTS() {

        tts =
            TextToSpeech(this) { result ->

                if (
                    result !=
                    TextToSpeech.SUCCESS
                ) {

                    runOnUiThread {

                        statusText.text =
                            "❌ خطا در TTS"
                    }

                    return@TextToSpeech
                }


                val engine =
                    tts ?: return@TextToSpeech


                // فارسی

                val resultFa =
                    engine.setLanguage(
                        Locale("fa", "IR")
                    )


                if (
                    resultFa ==
                    TextToSpeech.LANG_MISSING_DATA ||
                    resultFa ==
                    TextToSpeech.LANG_NOT_SUPPORTED
                ) {

                    engine.setLanguage(
                        Locale.getDefault()
                    )
                }


                /*
                 * صدای گفتار را با کیفیت مناسب تنظیم می‌کنیم.
                 */

                engine.setSpeechRate(
                    0.95f
                )

                engine.setPitch(
                    1.0f
                )


                ttsReady = true


                runOnUiThread {

                    statusText.text =
                        "🟢 TIMO آماده است"


                    val pending =
                        pendingSpeech

                    pendingSpeech =
                        null


                    if (
                        !pending.isNullOrBlank()
                    ) {

                        speak(pending)
                    }
                }
            }
    }


    // =========================================================
    // SPEAK
    //
    // مهم:
    // اینجا دیگر TTS مستقیماً صدا را پخش نمی‌کند.
    //
    // TTS -> WAV -> MediaPlayer -> Speaker
    // =========================================================

    private fun speak(text: String) {

        val cleanText =
            text
                .replace("*", "")
                .replace("#", "")
                .replace("`", "")
                .replace("_", "")
                .trim()


        if (cleanText.isBlank()) {
            return
        }


        if (
            !ttsReady ||
            tts == null
        ) {

            pendingSpeech =
                cleanText

            statusText.text =
                "🔊 آماده‌سازی صدا..."

            return
        }


        /*
         * صدای قبلی را قطع کن.
         */

        stopAudio()


        statusText.text =
            "🔊 TIMO در حال ساخت صدا..."


        /*
         * فایل صوتی موقت.
         */

        val file =
            File(
                cacheDir,
                "timo_voice.wav"
            )


        if (file.exists()) {
            file.delete()
        }


        audioFile = file


        /*
         * وقتی TTS ساخت فایل را تمام کرد،
         * MediaPlayer آن را پخش می‌کند.
         */

        tts?.setOnUtteranceProgressListener(

            object :
                android.speech.tts.UtteranceProgressListener() {

                override fun onStart(
                    utteranceId: String?
                ) {
                }


                override fun onDone(
                    utteranceId: String?
                ) {

                    runOnUiThread {

                        playAudioFile(file)
                    }
                }


                override fun onError(
                    utteranceId: String?
                ) {

                    runOnUiThread {

                        statusText.text =
                            "❌ ساخت صدای TIMO ناموفق بود"
                    }
                }
            }
        )


        val params =
            Bundle()


        /*
         * ساخت فایل WAV
         */

        val result =
            tts?.synthesizeToFile(
                cleanText,
                params,
                file,
                "TIMO_WAV"
            )


        if (
            result !=
            TextToSpeech.SUCCESS
        ) {

            statusText.text =
                "❌ TTS نتوانست فایل صدا بسازد"
        }
    }


    // =========================================================
    // PLAY AUDIO FILE
    // =========================================================

    private fun playAudioFile(
        file: File
    ) {

        if (!file.exists()) {

            statusText.text =
                "❌ فایل صدای TIMO ساخته نشد"

            return
        }


        stopAudio()


        try {

            val player =
                MediaPlayer()


            mediaPlayer =
                player


            /*
             * خروجی صدا = MEDIA
             */

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

                /*
                 * صدای Media را روی حداکثر سطح
                 * نرم‌افزاری خود Player قرار بده.
                 */

                it.setVolume(
                    1.0f,
                    1.0f
                )


                statusText.text =
                    "🔊 TIMO در حال صحبت است..."


                it.start()
            }


            player.setOnCompletionListener {

                statusText.text =
                    "🟢 TIMO آماده است"

                it.release()

                if (
                    mediaPlayer === it
                ) {

                    mediaPlayer =
                        null
                }
            }


            player.setOnErrorListener {
                    mp,
                    _,
                    _ ->

                statusText.text =
                    "❌ خطا در پخش صدای TIMO"

                mp.release()

                mediaPlayer =
                    null

                true
            }


            player.prepareAsync()

        } catch (e: Exception) {

            statusText.text =
                "❌ خطای پخش: ${e.message}"
        }
    }


    // =========================================================
    // STOP AUDIO
    // =========================================================

    private fun stopAudio() {

        try {

            mediaPlayer?.stop()

        } catch (_: Exception) {
        }


        try {

            mediaPlayer?.release()

        } catch (_: Exception) {
        }


        mediaPlayer =
            null
    }


    // =========================================================
    // SPEECH RECOGNIZER
    // =========================================================

    private fun initializeSpeechRecognizer() {

        if (
            !SpeechRecognizer
                .isRecognitionAvailable(this)
        ) {

            statusText.text =
                "❌ تشخیص گفتار در دسترس نیست"

            return
        }


        speechRecognizer =
            SpeechRecognizer
                .createSpeechRecognizer(this)


        speechRecognizer
            ?.setRecognitionListener(

                object :
                    RecognitionListener {

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

                        listening =
                            false


                        val list =
                            results
                                ?.getStringArrayList(
                                    SpeechRecognizer
                                        .RESULTS_RECOGNITION
                                )


                        val text =
                            list
                                ?.firstOrNull()
                                ?.trim()


                        runOnUiThread {

                            micButton.text =
                                "🎤 صحبت با TIMO"


                            if (
                                !text.isNullOrBlank()
                            ) {

                                sendToOpenAI(
                                    text
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

                        listening =
                            false


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
    // START LISTENING
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
            speechRecognizer
                ?: return


        val intent =
            Intent(
                RecognizerIntent
                    .ACTION_RECOGNIZE_SPEECH
            )


        intent.putExtra(
            RecognizerIntent
                .EXTRA_LANGUAGE_MODEL,
            RecognizerIntent
                .LANGUAGE_MODEL_FREE_FORM
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


        listening =
            true


        micButton.text =
            "⏹ توقف"


        recognizer.startListening(
            intent
        )
    }


    // =========================================================
    // STOP LISTENING
    // =========================================================

    private fun stopListening() {

        listening =
            false

        speechRecognizer
            ?.stopListening()


        micButton.text =
            "🎤 صحبت با TIMO"


        statusText.text =
            "🟢 TIMO آماده است"
    }


    // =========================================================
    // OPENAI
    // =========================================================

    private fun sendToOpenAI(
        message: String
    ) {

        conversationText.append(
            "\n\n👤 شما:\n$message\n"
        )


        statusText.text =
            "🧠 TIMO در حال فکر کردن..."


        sendButton.isEnabled =
            false

        micButton.isEnabled =
            false


        thread {

            var connection:
                HttpURLConnection? =
                null


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
                    همیشه فارسی صحبت کن.
                    طبیعی، دوستانه و کوتاه جواب بده.
                    برای سؤال ساده مستقیم جواب بده.
                    از Markdown و علامت‌های اضافی استفاده نکن.
                    """.trimIndent()
                )


                request.put(
                    "input",
                    message
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


                val code =
                    connection.responseCode


                val stream =
                    if (
                        code in 200..299
                    ) {

                        connection.inputStream

                    } else {

                        connection.errorStream
                    }


                val response =
                    BufferedReader(
                        InputStreamReader(
                            stream,
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
                    code !in 200..299
                ) {

                    runOnUiThread {

                        statusText.text =
                            "❌ خطای OpenAI: $code"

                        sendButton.isEnabled =
                            true

                        micButton.isEnabled =
                            true
                    }

                    return@thread
                }


                val answer =
                    extractAnswer(
                        response
                    )


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
    // PARSE OPENAI
    // =========================================================

    private fun extractAnswer(
        jsonText: String
    ): String {

        return try {

            val json =
                JSONObject(jsonText)


            val direct =
                json.optString(
                    "output_text",
                    ""
                )


            if (
                direct.isNotBlank()
            ) {

                direct.trim()

            } else {

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
                                ) ==
                                "output_text"
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
    // MICROPHONE PERMISSION
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
    // ESP32
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
                        result.trim() ==
                        "PONG"
                    ) {

                        statusText.text =
                            "🟢 TIMO ONLINE ✓"

                    } else {

                        statusText.text =
                            "🟢 TIMO آماده است"
                    }
                }


            } catch (_: Exception) {

                runOnUiThread {

                    statusText.text =
                        "🟢 TIMO آماده است"
                }
            }
        }
    }


    // =========================================================
    // DESTROY
    // =========================================================

    override fun onDestroy() {

        stopAudio()


        try {

            speechRecognizer
                ?.destroy()

        } catch (_: Exception) {
        }


        try {

            tts?.stop()
            tts?.shutdown()

        } catch (_: Exception) {
        }


        speechRecognizer =
            null

        tts =
            null


        super.onDestroy()
    }
}
