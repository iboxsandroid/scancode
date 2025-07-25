package com.itgz8.scancode

import android.annotation.SuppressLint
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions.ZoomCallback
import com.google.mlkit.vision.common.InputImage
import java.lang.Exception

class QRCodeAnalyser(
    private val success: (List<Barcode>,ImageProxy) -> Unit,
    private val failure: (Exception) -> Unit,
    private val complete: (List<Barcode>,ImageProxy) -> Unit,
    zoomCallback: ZoomCallback
) : ImageAnalysis.Analyzer {

    companion object {
        const val TAG = "BarcodeScanningActivity"
    }

    //配置当前扫码格式
    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_AZTEC,
            Barcode.FORMAT_PDF417,
            Barcode.FORMAT_CODABAR,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_DATA_MATRIX,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_ITF,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_UNKNOWN,
            Barcode.FORMAT_ALL_FORMATS
        )
//        .enableAllPotentialBarcodes()
        .setZoomSuggestionOptions(
            ZoomSuggestionOptions.Builder(zoomCallback)
                .setMaxSupportedZoomRatio(ScancodeConfig.maxZoomRatio)
                .build()
        )
        .build()
    //获取解析器
    private val detector = BarcodeScanning.getClient(options)

    private var barcodes: List<Barcode>? = null

    @OptIn(ExperimentalGetImage::class) @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: kotlin.run {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { barCodes ->
                barcodes = barCodes
                if (barCodes.size > 0){
                    // 判断是否开启连续扫码，如果开启则继续扫码，否则只返回第一个结果
                    if (ScancodeConfig.continuousScanning) {
                        success.invoke(barCodes,imageProxy)
                    } else {
                        // 判断是否开启批量扫码，如果开启则返回所有结果，否则只返回第一个结果
                        if (ScancodeConfig.batchScanning) {
                            success.invoke(barCodes,imageProxy)
                            //接收到结果后，就关闭解析
                            detector.close()
                        } else {
                            success.invoke(listOf(barCodes[0]),imageProxy)
                            //接收到结果后，就关闭解析
                            detector.close()
                        }
                    }
                }
            }
            .addOnFailureListener {
//                Log.d(TAG, "Error: ${it.message}")
                failure.invoke(it)
            }
            .addOnCompleteListener {
                complete.invoke(barcodes ?: emptyList(),imageProxy)
                imageProxy.close()
            }

        // 实现自动对焦和拉近拉远
        val focusMeteringAction = FocusMeteringAction.Builder(
            SurfaceOrientedMeteringPointFactory(imageProxy.width.toFloat(), imageProxy.height.toFloat())
                .createPoint(imageProxy.width / 2f, imageProxy.height / 2f)
        )
            .setAutoCancelDuration(5, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}