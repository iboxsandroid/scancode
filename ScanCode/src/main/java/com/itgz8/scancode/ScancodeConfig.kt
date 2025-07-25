package com.itgz8.scancode

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.camera.core.ImageProxy
import bitmapToUri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.IOException

/**
 * 插件配置信息
 */
object ScancodeConfig {
    // 扫一扫界面的标题
    var title: String = ""
    // 扫一扫界面的提示文字
    var tip: String = ""
    // 是否弹出扫码完成提示
    var showTip: Boolean = true
    // 扫码成功提示文字
    var successTip: String = ""
    // 扫码失败提示文字
    var failureTip: String = ""
    // 扫一扫界面的关闭按钮文字
    var exitText: String = ""
    // 扫一扫界面的取消按钮文字
    var cancelText: String = ""
    // 是否显示闪光灯按钮
    var showFlash: Boolean = true
    // 打开闪光灯时按钮的文字
    var flashOnText: String = ""
    // 关闭闪光灯时按钮的文字
    var flashOffText: String = ""
    // 打开闪光灯时按钮的背景色
    var flashOnColor: String = ""
    // 关闭闪光灯时按钮的背景色
    var flashOffColor: String = ""
    // 打开闪光灯时的提示文字
    var flashOnTip: String = ""
    // 打开闪光灯时的文字颜色
    var flashOnTextColor: String = ""
    // 关闭闪光灯时的提示文字
    var flashOffTip: String = ""
    // 关闭闪光灯时的文字颜色
    var flashOffTextColor: String = ""
    // 是否显示相册按钮
    var showAlbum: Boolean = true
    // 是否显示返回按钮
    var showBack: Boolean = true
    // 是否需要图片
    var needImage:Boolean = false
    // 是否显示扫描线
    var showLine: Boolean = true
    // 扫描线动画时长
    var lineDuration: Long = 3000
    // 扫描线起始位置
    var lineStartY: Float = 0f
    // 扫描线结束位置·
    var lineEndY: ((floatYFraction: Float) -> Float)? = { 0f }
    // 扫描成功后是否震动
    var vibrate: Boolean = true
    // 初始化调焦大小
    var initZoomRatio: Float = 1.0f
    // 最大调焦大小
    var maxZoomRatio: Float = 1.0f
    // 是否自动全屏
    var autoFullScreen: Boolean = true
    // 是否启用手势缩放
    var touchZoom: Boolean = true
    // 是否启用双击缩放
    var doubleTapZoom: Boolean = true
    // 是否开启连续扫码
    var continuousScanning: Boolean = false
    // 是否开启批量扫码
    var batchScanning: Boolean = true
    // 二维码识别圆圈半径大小
    var markCircleRadius: Float = 50f
    // 二维码识别圆圈颜色
    var markCircleColor: String = "#00BC79"
    // 二维码识别圆圈边框颜色
    var markCircleStrokeColor: String = "#FFFFFF"
    // 二维码识别圆圈边框宽度
    var markCircleStrokeWidth: Float = 3f
    // 二维码识别圆圈是否开启动画
    var markCircleAnimate: Boolean = true
    // 是否开启二维码只在屏幕中心识别
    var centerBarcode: Boolean = true
    // 识别到二维码后执行成功回调前的自定义处理
    var beforeSuccess: ((ResponseStateConfig) -> Unit)? = null
    // 识别到二维码后执行成功回调
    var onSuccess: ((ResponseStateConfig) -> Unit)? = null
    // 识别到二维码后执行失败回调
    var onFailure: ((ResponseStateConfig) -> Unit)? = null
    // 识别到二维码后执行完成回调
    var onComplete: ((ResponseStateConfig) -> Unit)? = null
    // 识别到二维码后执行取消回调前的自定义处理
    var beforeCancel: (() -> Boolean)? = null
    // 识别到二维码后执行取消回调
    var onCancel: (() -> Unit)? = null
    // 识别到二维码后执行返回回调
    var onBack: (() -> Unit)? = null
    // 识别到二维码后执行相册回调
    var onAlbum: (() -> Unit)? = null
    // 识别到二维码后打开闪光灯回调
    var onFlashOn: (() -> Unit)? = null
    // 识别到二维码后关闭闪光灯回调
    var onFlashOff: (() -> Unit)? = null
    // 识别到二维码后执行手势缩放回调
    var onZoom: ((Float) -> Unit)? = null
    // 识别到二维码后执行双击缩放回调
    var onDoubleTap: ((Float) -> Unit)? = null
    // 识别到二维码后执行连续扫码回调
    var onContinuous: ((ResponseStateConfig) -> Unit)? = null
    // 识别到二维码后执行批量扫码回调
    var onBatch: ((Boolean) -> Unit)? = null
    // 点击返回按钮是否关闭扫一扫界面
    var backFinish: Boolean = true
    // 点击取消按钮是否关闭扫一扫界面
    var cancelFinish: Boolean = false
    // 点击单个二维码的回调
    var onBarcode: ((ResponseStateConfig) -> Unit)? = null
    // 识别到二维码时是否显示蒙层
    var showMask: Boolean = true
    // 识别到二维码时蒙层颜色
    var maskColor: String = "#80000000"
    // 识别到二维码时蒙层透明度
    var maskAlpha: Float = 0.5f
    // 识别到二维码时显示蒙层的过渡动画时长
    var maskDuration: Long = 300
    // 用户拒绝相机权限时的回调
    var onCameraPermissionDenied: (() -> Unit)? = null
    // 用户拒绝相册权限时的回调
    var onAlbumPermissionDenied: (() -> Unit)? = null
    var onOpenAlbum:(()->Unit)?=null
}

/**
 * 定义成功状态码常量
 */
object ResponseStateCode {
    const val SUCCESS = 200
    const val FAILURE = 500
    const val CANCEL = 501
    const val BACK = 502
    const val ALBUM = 503
    const val FLASH = 504
    const val ZOOM = 505
    const val DOUBLE_TAP = 506
    const val CONTINUOUS = 507
    const val BATCH = 508
    const val BARCODE = 509
}

/**
 * 二维码数据
 */
object QRCodeImageConfig {
    var barcode: Any = Any()
    var barcodeList: List<Any> = listOf()
    var imageProxy: Any = Any()
    var image: Any = Any()
    var mediaImage: Any = Any()
    var width: Int = 0
    var height: Int = 0
}

/**
 * 扫描结果状态
 */
object ResponseStateConfig {
    var statusCode: Int = 0
    var message: String = ""
    var data: Any = Any()
    lateinit var barcode: QRCodeImageConfig
}

object CodeInfo {
    var bitmap:String? = null
    var rawValue:String? = null
    var proxy:ImageProxy? = null
    var barcode:Barcode?=null
}
