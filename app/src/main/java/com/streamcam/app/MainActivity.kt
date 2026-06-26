package com.streamcam.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.srt.SrtCamera2
import com.streamcam.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), ConnectChecker, SurfaceHolder.Callback {

    private val host = "167.233.203.172"
    private val port = 8890
    private val prefChannel = "pref_channel"

    private lateinit var binding: ActivityMainBinding
    private lateinit var srtCamera2: SrtCamera2

    private val resolutions = listOf(
        Triple("1080p", 1920, 1080),
        Triple("720p", 1280, 720),
        Triple("480p", 854, 480)
    )

    private val bitrates = listOf(
        "12 Mbps" to 12000,
        "8 Mbps" to 8000,
        "6 Mbps" to 6000,
        "4 Mbps" to 4000,
        "2.5 Mbps" to 2500
    )

    private val channels = (1..8).map { "Canal $it" to "canal$it" }
    private val fps = 30

    // Reintentos
    private var retryCount = 0
    private val maxRetries = 3
    private val retryDelayMs = 5000L

    // Bitrate adaptativo
    private var lowBitrateStart = 0L
    private var currentResolutionIndex = 1 // 720p por defecto
    private var targetBitrateKbps = 6000

    // Watchdog
    private val handler = Handler(Looper.getMainLooper())
    private var lastFrameCount = 0L
    private var lastFrameTime = 0L
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (srtCamera2.isStreaming) {
                val frames = srtCamera2.streamClient.getSentVideoFrames().toLong()
                if (frames == lastFrameCount && lastFrameCount > 0) {
                    val stuckSecs = (System.currentTimeMillis() - lastFrameTime) / 1000
                    if (stuckSecs > 10) {
                        log("Watchdog: sin frames ${stuckSecs}s, reiniciando...")
                        restartStream()
                        return
                    }
                } else {
                    lastFrameCount = frames
                    lastFrameTime = System.currentTimeMillis()
                }
                handler.postDelayed(this, 2000)
            }
        }
    }

    private val logLines = ArrayDeque<String>(12)

    private fun log(msg: String) {
        runOnUiThread {
            if (logLines.size >= 10) logLines.removeFirst()
            logLines.addLast(msg)
            binding.statusText.text = logLines.joinToString("\n")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.resolutionSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            resolutions.map { it.first }
        )
        binding.resolutionSpinner.setSelection(currentResolutionIndex)

        binding.bitrateSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            bitrates.map { it.first }
        )
        binding.bitrateSpinner.setSelection(2)

        binding.channelSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            channels.map { it.first }
        )

        val prefs = getSharedPreferences("streamcam", MODE_PRIVATE)
        binding.channelSpinner.setSelection(prefs.getInt(prefChannel, 0))

        srtCamera2 = SrtCamera2(binding.openGlView, this)
        binding.openGlView.holder.addCallback(this)

        binding.startStopButton.setOnClickListener { toggleStream() }
        binding.switchCameraButton.setOnClickListener {
            try { srtCamera2.switchCamera() } catch (_: Exception) {}
        }

        requestPermissions()
    }

    private fun buildSrtUrl(): String {
        val channelId = channels[binding.channelSpinner.selectedItemPosition].second
        return "srt://$host:$port/publish:$channelId"
    }

    private fun toggleStream() {
        if (!srtCamera2.isStreaming) {
            retryCount = 0
            currentResolutionIndex = binding.resolutionSpinner.selectedItemPosition
            startStream()
        } else {
            stopStreamClean()
        }
    }

    private fun startStream() {
        val url = buildSrtUrl()
        targetBitrateKbps = bitrates[binding.bitrateSpinner.selectedItemPosition].second
        val res = resolutions[currentResolutionIndex]
        val rotation = CameraHelper.getCameraOrientation(this)

        log("URL: $url")
        log("Res: ${res.first} | Bitrate: ${targetBitrateKbps} kbps")

        if (srtCamera2.isOnPreview) srtCamera2.stopPreview()

        val preparedAudio = srtCamera2.prepareAudio(128 * 1024, 44100, true)
        val preparedVideo = srtCamera2.prepareVideo(
            res.second, res.third, fps, targetBitrateKbps * 1024, 2, rotation
        )

        log("Audio: $preparedAudio | Video: $preparedVideo")

        if (preparedAudio && preparedVideo) {
            srtCamera2.startPreview()
            srtCamera2.startStream(url)
            binding.startStopButton.text = getString(R.string.stop)
            log("Conectando...")
            lastFrameCount = 0
            lastFrameTime = System.currentTimeMillis()
            handler.postDelayed(watchdogRunnable, 2000)

            val prefs = getSharedPreferences("streamcam", MODE_PRIVATE)
            prefs.edit().putInt(prefChannel, binding.channelSpinner.selectedItemPosition).apply()
        } else {
            srtCamera2.startPreview()
            log("ERROR encoder: audio=$preparedAudio video=$preparedVideo")
        }
    }

    private fun stopStreamClean() {
        handler.removeCallbacks(watchdogRunnable)
        srtCamera2.stopStream()
        binding.startStopButton.text = getString(R.string.start)
        retryCount = 0
        log("Detenido")
    }

    private fun restartStream() {
        srtCamera2.stopStream()
        handler.postDelayed({ startStream() }, 1000)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (!srtCamera2.isOnPreview) srtCamera2.startPreview()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        handler.removeCallbacks(watchdogRunnable)
        if (srtCamera2.isStreaming) srtCamera2.stopStream()
        if (srtCamera2.isOnPreview) srtCamera2.stopPreview()
    }

    override fun onConnectionStarted(url: String) {
        log("Iniciando: $url")
    }

    override fun onConnectionSuccess() = runOnUiThread {
        retryCount = 0
        lowBitrateStart = 0
        log("CONECTADO OK")
    }

    override fun onConnectionFailed(reason: String) = runOnUiThread {
        log("Fallo: $reason")
        if (retryCount < maxRetries) {
            retryCount++
            log("Reintento $retryCount/$maxRetries en 5s...")
            handler.postDelayed({ restartStream() }, retryDelayMs)
        } else {
            stopStreamClean()
            log("Sin mas reintentos")
        }
    }

    override fun onNewBitrate(bitrate: Long) = runOnUiThread {
        val kbps = bitrate / 1024
        log("Bitrate: ${kbps} kbps")

        // Bitrate adaptativo
        val threshold = targetBitrateKbps * 0.5
        if (kbps < threshold) {
            if (lowBitrateStart == 0L) lowBitrateStart = System.currentTimeMillis()
            val lowSecs = (System.currentTimeMillis() - lowBitrateStart) / 1000
            if (lowSecs > 5 && currentResolutionIndex < resolutions.size - 1) {
                currentResolutionIndex++
                log("Bajando a ${resolutions[currentResolutionIndex].first}...")
                lowBitrateStart = 0
                restartStream()
            }
        } else {
            lowBitrateStart = 0
        }
    }

    override fun onDisconnect() = runOnUiThread {
        log("Desconectado")
    }

    override fun onAuthError() = runOnUiThread {
        log("Error de autenticacion")
    }

    override fun onAuthSuccess() {}

    private fun requestPermissions() {
        val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val missing = needed.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) ActivityCompat.requestPermissions(this, needed, 1)
    }
}