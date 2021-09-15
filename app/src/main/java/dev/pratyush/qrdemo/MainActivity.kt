package dev.pratyush.qrdemo

import android.Manifest
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.get
import dev.pratyush.qrdemo.analyser.QRAnalyser
import dev.pratyush.qrdemo.overlay.ScannerOverlay
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val analyserExecutor = Executors.newSingleThreadExecutor()

    private val analyser : QRAnalyser by lazy {
        initImageAnalyser()
    }
    private fun initImageAnalyser(): QRAnalyser {
        return QRAnalyser(scannerOverlay)
    }
    private val scannerOverlay : ScannerOverlay by lazy{
        findViewById(R.id.scannerOverlay)
    }
    private val previewView : PreviewView by lazy {
        findViewById(R.id.previewView)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        analyser.bitmapLiveData().observe(this, {
            findViewById<ImageView>(R.id.ivActScannerCroppedPreview).setImageBitmap(it)
        })

        analyser.errorLiveData().observe(this, { e ->
            Toast.makeText(this, "Scanner failed, reason: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        })

        analyser.liveData().observe(this, { result ->
            val tvActScannerScannedResult = findViewById<TextView>(R.id.tvActScannerScannedResult)
            tvActScannerScannedResult.text = ""
            result?.let {
                tvActScannerScannedResult.text = it
            }
        })

        analyser.debugInfoLiveData().observe(this, {
            val surfaceView = previewView[0]
            val tvActScannerDebugInfo = findViewById<TextView>(R.id.tvActScannerDebugInfo)
            val info = "$it\nPreview Size (${surfaceView.width}, ${surfaceView.height}) " +
                    "Translation (${surfaceView.translationX}, ${surfaceView.translationY}) " +
                    "Scale (${surfaceView.scaleX}, ${surfaceView.scaleY}) " +
                    "Pivot (${surfaceView.pivotX}, ${surfaceView.pivotY}) " +
                    "Rotation (${surfaceView.rotation}) " +
                    "Container Size (${previewView.width}, ${previewView.height})"
            tvActScannerDebugInfo.text = info
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 0)
        }

        startCamera()

    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)


        val cameraSelector = CameraSelector
            .Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        val preview = Preview.Builder()
            .build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

        val imageAnalyzer = ImageAnalysis.Builder()
            .build()
            .also {
                it.setAnalyzer(analyserExecutor, analyser)
            }

        val useCase = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(imageAnalyzer)
            .build()

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, useCase)
            } catch (exc: Exception) {
            }

        }, ContextCompat.getMainExecutor(this))
    }


}