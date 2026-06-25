package com.streamcam.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.resolutionSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            resolutions.map { it.first }
        )
        // Seleccionar 720p por defecto (Ã­ndice 1)
        binding.resolutionSpinner.setSelection(1)

        binding.bitrateSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            bitrates.map { it.first }
        )
        // Seleccionar 6 Mbps por defecto (Ã­ndice 2)
        binding.bitrateSpinner.setSelection(2)

        binding.channelSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            channels.map { it.first }
        )

        val prefs = getSharedPreferences("streamcam", MODE_PRIVATE)
        val savedChannel = prefs.getInt(prefChannel, 0)
        binding.channelSpinner.setSelection(savedChannel)

        srtCamera2 = SrtCamera2(binding.openGlView, this)
        binding.openGlView.holder.addCallback(this)

        binding.startStopButton.setOnClickListener { toggleStream() }
        binding.switchCameraButton.setOnClickListener {
            try { srtCamera2.switchCamera() } catch (_: Exception) {}
        }

        requestPermissions()
    }

    private fun buildSrtUrl(): String {
        val channelIndex = binding.channelSpinner.selectedItemPosition
        val channelId = channels[channelIndex].second
        return "srt://$host:$port/publish:$channelId"
    }

    private fun toggleStream() {
        if (!srtCamera2.isStreaming) {
            val url = buildSrtUrl()

            val prefs = getSharedPreferences("streamcam", MODE_PRIVATE)
            prefs.edit().putInt(prefChannel, binding.channelSpinner.selectedItemPosition).apply()

            val res = resolutions[binding.resolutionSpinner.selectedItemPosition]
            val bitrateKbps = bitrates[binding.bitrateSpinner.selectedItemPosition].second
            val rotation = CameraHelper.getCameraOrientation(this)

            binding.statusText.text = "Preparando encoder..."

            // Detener preview antes de preparar para liberar recursos
            if (srtCamera2.isOnPreview) srtCamera2.stopPreview()

            val preparedAudio = srtCamera2.prepareAudio(128 * 1024, 44100, true)
            val preparedVideo = srtCamera2.prepareVideo(
                res.second, res.third, fps, bitrateKbps * 1024, 2, rotation
            )

            if (preparedAudio && preparedVideo) {
                srtCamera2.startPreview()
                srtCamera2.startStream(url)
                binding.startStopButton.text = getString(R.string.stop)
                binding.statusText.text = getString(R.string.status_connecting)
            } else {
                // Reiniciar preview aunque falle
                srtCamera2.startPreview()
                binding.statusText.text = "Encoder fallÃ³ â€” audio:$preparedAudio video:$preparedVideo"
            }
        } else {
            srtCamera2.stopStream()
            binding.startStopButton.text = getString(R.string.start)
            binding.statusText.text = getString(R.string.status_idle)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (!srtCamera2.isOnPreview) srtCamera2.startPreview()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (srtCamera2.isStreaming) srtCamera2.stopStream()
        if (srtCamera2.isOnPreview) srtCamera2.stopPreview()
    }

    override fun onConnectionStarted(url: String) {}

    override fun onConnectionSuccess() = runOnUiThread {
        binding.statusText.text = getString(R.string.status_streaming)
    }

    override fun onConnectionFailed(reason: String) = runOnUiThread {
        srtCamera2.stopStream()
        binding.startStopButton.text = getString(R.string.start)
        binding.statusText.text = getString(R.string.status_failed) + ": " + reason
    }

    override fun onNewBitrate(bitrate: Long) = runOnUiThread {
        val kbps = bitrate / 1024
        binding.statusText.text = getString(R.string.status_streaming) + " Â· ${kbps} kbps"
    }

    override fun onDisconnect() = runOnUiThread {
        binding.statusText.text = getString(R.string.status_idle)
    }

    override fun onAuthError() = runOnUiThread {
        binding.statusText.text = "Error de autenticacion"
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
