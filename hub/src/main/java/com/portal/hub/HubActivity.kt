package com.portal.hub

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.Toast
import java.util.Locale
import org.json.JSONObject

/**
 * Full-screen WebView shell for the family hub PWA.
 *
 * Loads the live hub, keeps the screen on, and adds a native mic button:
 * Android's SpeechRecognizer hears the question and injects it into the
 * hub's Talk to Muse page (the WebView has no speech recognition of its
 * own on this device, so the hub's orb cannot hear here). The hub's orb
 * can still use its MediaRecorder fallback when the page asks for the mic.
 */
class HubActivity : Activity() {

    companion object {
        private const val HUB_URL = "https://family-learning-dashboard-portal.vercel.app"
        private const val MUSE_PATH = "/app/muse"
        private const val REQUEST_RECORD_AUDIO = 1
    }

    private lateinit var webView: WebView
    private lateinit var micButton: ImageButton
    private var pendingTranscript: String? = null
    private var speechRecognizer: SpeechRecognizer? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_hub)

        webView = findViewById(R.id.webview)
        micButton = findViewById(R.id.mic_button)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.builtInZoomControls = false
        webView.settings.displayZoomControls = false

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Keep all navigation inside the WebView.
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                pendingTranscript?.let { transcript ->
                    pendingTranscript = null
                    injectTranscript(transcript)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                // Let the hub's own Talk to Muse orb use the mic (its
                // MediaRecorder fallback) when it asks. Only audio is granted.
                val audio = request.resources
                    .filter { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
                    .toTypedArray()
                if (audio.isNotEmpty()) {
                    request.grant(audio)
                } else {
                    request.deny()
                }
            }
        }

        micButton.setOnClickListener { onMicTapped() }

        if (savedInstanceState == null) {
            webView.loadUrl(HUB_URL)
        }
    }

    private fun onMicTapped() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            return
        }
        startListening()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Voice input isn't available on this device.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                recognizer.destroy()
                speechRecognizer = null
                if (heard.isNotBlank()) {
                    sendToMuse(heard)
                } else {
                    Toast.makeText(this@HubActivity, "Didn't catch anything — try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: Int) {
                recognizer.destroy()
                speechRecognizer = null
                Toast.makeText(this@HubActivity, "Voice input failed — try again.", Toast.LENGTH_SHORT).show()
            }

            override fun onReadyForSpeech(params: Bundle) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle) {}
            override fun onEvent(eventType: Int, params: Bundle) {}
        })
        recognizer.startListening(intent)
        Toast.makeText(this, "Listening…", Toast.LENGTH_SHORT).show()
    }

    /** Routes a heard question to the Talk to Muse page, opening it first if needed. */
    private fun sendToMuse(text: String) {
        val url = webView.url.orEmpty()
        if (MUSE_PATH in url) {
            injectTranscript(text)
        } else {
            pendingTranscript = text
            webView.loadUrl(HUB_URL + MUSE_PATH)
        }
    }

    /** Types the question into the hub's text box and sends it. */
    private fun injectTranscript(text: String) {
        val escaped = JSONObject.quote(text)
        val js = "(function(){var i=document.getElementById('text-input');if(!i)return 'no-input';" +
            "i.value=$escaped;" +
            "i.dispatchEvent(new Event('input',{bubbles:true}));" +
            "var b=document.getElementById('send-text');if(!b)return 'no-send';b.click();return 'sent';})()"
        webView.evaluateJavascript(js) { result ->
            if (result != "\"sent\"") {
                Toast.makeText(this, "Couldn't send that to Muse — try typing it.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening()
            } else {
                Toast.makeText(this, "Microphone permission is needed for voice input.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }
}
