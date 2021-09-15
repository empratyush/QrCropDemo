package dev.pratyush.qrdemo.analyser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.Image
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.lifecycle.*
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import dev.pratyush.qrdemo.overlay.ScannerOverlay
import dev.pratyush.qrdemo.util.BitmapUtil
import dev.pratyush.qrdemo.util.FrameMetadata
import dev.pratyush.qrdemo.util.YuvNV21Util
import java.lang.NullPointerException
import java.util.*

class QRAnalyser(private val scannerOverlay: ScannerOverlay) : ImageAnalysis.Analyzer, LifecycleObserver {

    private val qrRecognizedData = MutableLiveData<Boolean>()
    fun qrRecognizedLiveData(): LiveData<Boolean> = qrRecognizedData

    private val imageMutableData = MutableLiveData<Bitmap>()
    private val mutableLiveData = MutableLiveData<String>()
    private val errorData = MutableLiveData<Exception>()
    private val debugInfoData = MutableLiveData<String>()

    fun liveData() : LiveData<String> = mutableLiveData
    fun errorLiveData() : LiveData<Exception> = errorData
    fun bitmapLiveData() : LiveData<Bitmap> = imageMutableData
    fun debugInfoLiveData() : LiveData<String> = debugInfoData

    var emitDebugInfo : Boolean = true

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        try {
            val imageProxyReadyEpoch = System.currentTimeMillis()
            val rotation = imageProxy.imageInfo.rotationDegrees
            val scannerRect = getScannerRectToPreviewViewRelation(Size(imageProxy.width, imageProxy.height), rotation)

            val image = imageProxy.image!!
            val cropRect = image.getCropRectAccordingToRotation(scannerRect, rotation)
            image.cropRect = cropRect

            val byteArray = YuvNV21Util.yuv420toNV21(image)
            val bitmap = BitmapUtil.getBitmap(byteArray, FrameMetadata(cropRect.width(), cropRect.height(), rotation))
            val imagePreparedReadyEpoch = System.currentTimeMillis()

            imageMutableData.postValue(bitmap)

            onBitmapPrepared(bitmap)

            val imageProcessedEpoch = System.currentTimeMillis()

            if(emitDebugInfo) {
                debugInfoData.postValue("""
                   Image proxy (${imageProxy.width},${imageProxy.height}) format : ${imageProxy.format} rotation: $rotation 
                   Cropped Image (${bitmap.width},${bitmap.height}) Preparing took: ${imagePreparedReadyEpoch - imageProxyReadyEpoch}ms
                   OCR Processing took : ${imageProcessedEpoch - imagePreparedReadyEpoch}ms Using Service: 
                """.trimIndent())
            }

            imageProxy.close()
        } catch (e : Exception) {
            errorData.postValue(e)
        }
    }

    private fun postResult(value : String?) {
        mutableLiveData.postValue(value)
    }

    private fun getScannerRectToPreviewViewRelation(proxySize : Size, rotation : Int): ScannerRectToPreviewViewRelation {
        return when(rotation) {
            0, 180 -> {
                val size = scannerOverlay.size
                val width = size.width
                val height = size.height
                val previewHeight = width / (proxySize.width.toFloat() / proxySize.height)
                val heightDeltaTop = (previewHeight - height) / 2

                val scannerRect = scannerOverlay.scanRect
                val rectStartX = scannerRect.left
                val rectStartY = heightDeltaTop + scannerRect.top

                ScannerRectToPreviewViewRelation(
                    rectStartX / width,
                    rectStartY / previewHeight,
                    scannerRect.width() / width,
                    scannerRect.height() / previewHeight
                )
            }
            90, 270 -> {
                val size = scannerOverlay.size
                val width = size.width
                val height = size.height
                val previewWidth = height / (proxySize.width.toFloat() / proxySize.height)
                val widthDeltaLeft = (previewWidth - width) / 2

                val scannerRect = scannerOverlay.scanRect
                val rectStartX = widthDeltaLeft + scannerRect.left
                val rectStartY = scannerRect.top

                ScannerRectToPreviewViewRelation(
                    rectStartX / previewWidth,
                    rectStartY / height,
                    scannerRect.width() / previewWidth,
                    scannerRect.height() / height
                )
            }
            else -> throw IllegalArgumentException("Rotation degree ($rotation) not supported!")
        }
    }

    private val TAG = "SepaQRAnalyser"
    private fun onBitmapPrepared(bitmap: Bitmap) {

        try {
            val intArray = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source: LuminanceSource = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)

            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val supportedHints: MutableMap<DecodeHintType, Any> = EnumMap(
                DecodeHintType::class.java
            )
            //supportedHints[DecodeHintType.PURE_BARCODE] = true
            //supportedHints[DecodeHintType.TRY_HARDER] = true
            // supportedHints[DecodeHintType.CHARACTER_SET] = StandardCharsets.ISO_8859_1
            supportedHints[DecodeHintType.POSSIBLE_FORMATS] = listOf(BarcodeFormat.QR_CODE)

            val result = QRCodeReader()
                .decode(
                    binaryBitmap,
                    //supportedHints
                )
            val plainResult = result?.text
            if(plainResult == null || plainResult.isBlank() || plainResult.isEmpty()){
                throw NullPointerException("no Qr Code Founds")
            }
            qrRecognizedData.postValue(true)
            println("$TAG $plainResult")
            postResult(plainResult)
        }catch (e : NotFoundException){
            qrRecognizedData.postValue(false)
            postResult(null)
        }catch (e : NullPointerException){
            qrRecognizedData.postValue(false)
            postResult(null)
        }catch (e : FormatException){
            qrRecognizedData.postValue(false)
            postResult(null)
        }catch (e : ChecksumException){
            qrRecognizedData.postValue(false)
            postResult(null)
        }
    }

    data class ScannerRectToPreviewViewRelation(val relativePosX: Float,
                                                val relativePosY: Float,
                                                val relativeWidth: Float,
                                                val relativeHeight: Float)

    private fun Image.getCropRectAccordingToRotation(scannerRect: ScannerRectToPreviewViewRelation, rotation: Int) : Rect {
        return when(rotation) {
            0 -> {
                val startX = (scannerRect.relativePosX * this.width).toInt()
                val numberPixelW = (scannerRect.relativeWidth * this.width).toInt()
                val startY = (scannerRect.relativePosY * this.height).toInt()
                val numberPixelH = (scannerRect.relativeHeight * this.height).toInt()
                Rect(startX, startY, startX + numberPixelW, startY + numberPixelH)
            }
            90 -> {
                val startX = (scannerRect.relativePosY * this.width).toInt()
                val numberPixelW = (scannerRect.relativeHeight * this.width).toInt()
                val numberPixelH = (scannerRect.relativeWidth * this.height).toInt()
                val startY = height - (scannerRect.relativePosX * this.height).toInt() - numberPixelH
                Rect(startX, startY, startX + numberPixelW, startY + numberPixelH)
            }
            180 -> {
                val numberPixelW = (scannerRect.relativeWidth * this.width).toInt()
                val startX = (this.width - scannerRect.relativePosX * this.width - numberPixelW).toInt()
                val numberPixelH = (scannerRect.relativeHeight * this.height).toInt()
                val startY = (height - scannerRect.relativePosY * this.height - numberPixelH).toInt()
                Rect(startX, startY, startX + numberPixelW, startY + numberPixelH)
            }
            270 -> {
                val numberPixelW = (scannerRect.relativeHeight * this.width).toInt()
                val numberPixelH = (scannerRect.relativeWidth * this.height).toInt()
                val startX = (this.width - scannerRect.relativePosY * this.width - numberPixelW).toInt()
                val startY = (scannerRect.relativePosX * this.height).toInt()
                Rect(startX, startY, startX + numberPixelW, startY + numberPixelH)
            }
            else -> throw IllegalArgumentException("Rotation degree ($rotation) not supported!")
        }
    }
}