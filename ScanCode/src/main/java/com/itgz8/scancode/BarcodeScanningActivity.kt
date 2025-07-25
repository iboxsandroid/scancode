package com.itgz8.scancode

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.itgz8.scancode.databinding.ActivityBarcodeScanningBinding
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import android.graphics.ImageFormat;
import bitmapToUri
import isPortraitMode
import toUri
import java.nio.ByteBuffer;

class BarcodeScanningActivity : AppCompatActivity() {

    private  val TAG = "BarcodeScanningActivity"

    private lateinit var cameraProviderFuture : ListenableFuture<ProcessCameraProvider>

    private var listener : OverlayListener? = null

    private var camera : Camera? = null

    private var scaleX = 0f

    private var scaleY = 0f

    private lateinit var binding: ActivityBarcodeScanningBinding

    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private lateinit var gestureDetector: GestureDetector

    private var handler: Handler = Handler(Looper.getMainLooper())

    private lateinit var delayRunnable: Runnable

    private var gConfig : ScancodeConfig? = null

    private var maskOverlay: View? = null

    companion object{
        const val SCAN_RESULT = "BarcodeScanningActivity.scan_result"
        const val REQUEST_PERMISSION = 12345
        const val PICK_IMAGE_REQUEST = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        binding = DataBindingUtil.setContentView(this,R.layout.activity_barcode_scanning)
        binding = ActivityBarcodeScanningBinding.inflate(layoutInflater)
        setContentView(binding.root)
//        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA,Manifest.permission.WRITE_EXTERNAL_STORAGE),
//            REQUEST_PERMISSION
//        )
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        listener = OverlayListener()
        binding.overlay.viewTreeObserver.addOnGlobalLayoutListener(listener)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 根据配置信息，设置扫描界面
        setScanView()

        // 设置透明状态栏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setTransparentStatusBar()
        // 设置全屏显示
        if (ScancodeConfig.autoFullScreen) setFullScreen()
    }

    /**
     * 设置透明状态栏
     */
    private fun setTransparentStatusBar() {
        val window = window
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        val uiOptions = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        getWindow().decorView.systemUiVisibility = uiOptions
    }

    /**
     * 设置全屏显示
     */
    private fun setFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
            val decorView = window.decorView
            decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    /**
     * 设置扫描界面
     */
    private fun setScanView() {
        // 设置扫描界面标题
        binding.tvTitle.text = if (isSetConfigTitle()) ScancodeConfig.title else getString(R.string.scan_title)
        // 设置扫码时的提示文字
        binding.tvTips.text = if (isSetTip()) ScancodeConfig.tip else getString(R.string.scanning_text)
        // 设置相册按钮是否显示
        binding.ivPhoto.visibility = if (ScancodeConfig.showAlbum) View.VISIBLE else View.INVISIBLE
        // 设置闪光灯按钮是否显示
        binding.llFlashlight.visibility = if (ScancodeConfig.showFlash) View.VISIBLE else View.GONE
        // 设置取消按钮文字
        binding.tvCancel.text = if (isSetConfigCancelText()) ScancodeConfig.cancelText else getString(R.string.cancel_scan_result)
        // 设置打开闪光灯时按钮文字显示
        val flashOnColor = if (isSetFlashOnColor()) Color.parseColor(ScancodeConfig.flashOnColor) else ContextCompat.getColor(this, R.color.colorWhite)
        val flashOffColor = if (isSetFlashOffColor()) Color.parseColor(ScancodeConfig.flashOffColor) else ContextCompat.getColor(this, R.color.colorPrimary)
        binding.ivFlashlightOn.imageTintList = ColorStateList.valueOf(flashOnColor)
        binding.ivFlashlightOff.imageTintList = ColorStateList.valueOf(flashOffColor)
        // 设置闪光灯按钮文字默认颜色
        val flashTextColor = if (isSetFlashOffTextColor()) Color.parseColor(ScancodeConfig.flashOffTextColor) else ContextCompat.getColor(this, R.color.colorWhite)
        binding.tvFlashlight.setTextColor(flashTextColor)
        // 判断是否设置了关闭按钮文字，如果设置了，则隐藏返回图标，显示关闭文字
        if (isSetExitText()) {
            binding.ivExit.visibility = View.GONE
            binding.tvExit.visibility = View.VISIBLE
            binding.tvExit.text = ScancodeConfig.exitText
        }
        // 判断是否设置了隐藏关闭按钮，如果设置了，则隐藏关闭按钮和关闭文字
        if (!ScancodeConfig.showBack) {
            binding.ivExit.visibility = View.GONE
            binding.tvExit.visibility = View.GONE
        }
    }

    /**
     * 打开闪光灯
     */
    fun turnOnFlashlight() {
        camera?.cameraControl?.enableTorch(true)
        // 更新提示文字
        val flashOnTip = if (isSetConfigFlashOnTip()) ScancodeConfig.flashOnTip else getString(R.string.flash_on_tip)
        if (ScancodeConfig.showTip) {
            Toast.makeText(this@BarcodeScanningActivity, flashOnTip, Toast.LENGTH_SHORT).show()
        }
        // 更新打开闪光灯时按钮的文字颜色
        val flashOnTextColor = if (isSetFlashOnTextColor()) Color.parseColor(ScancodeConfig.flashOnTextColor) else ContextCompat.getColor(this, R.color.colorPrimary)
        binding.tvFlashlight.setTextColor(flashOnTextColor)
        // 更新打开闪光灯时按钮的文字
        val flashOnText = if (isSetConfigFlashOnText()) ScancodeConfig.flashOnText else getString(R.string.close_flashlight)
        binding.tvFlashlight.text = flashOnText
        // 切换闪光灯状态图标
        binding.ivFlashlightOn.visibility = View.GONE
        binding.ivFlashlightOff.visibility = View.VISIBLE
    }

    /**
     * 关闭闪光灯
     */
    fun turnOffFlashlight() {
        camera?.cameraControl?.enableTorch(false)
        // 更新提示文字
        val flashOffTip = if (isSetConfigFlashOffTip()) ScancodeConfig.flashOffTip else getString(R.string.flash_off_tip)
        if (ScancodeConfig.showTip) {
            Toast.makeText(this@BarcodeScanningActivity, flashOffTip, Toast.LENGTH_SHORT).show()
        }
        // 更新关闭闪光灯时按钮的文字颜色
        val flashOffTextColor = if (isSetFlashOffTextColor()) Color.parseColor(ScancodeConfig.flashOffTextColor) else ContextCompat.getColor(this, R.color.colorWhite)
        binding.tvFlashlight.setTextColor(flashOffTextColor)
        // 更新关闭闪光灯时按钮的文字
        val flashOffText = if (isSetConfigFlashOffText()) ScancodeConfig.flashOffText else getString(R.string.open_flashlight)
        binding.tvFlashlight.text = flashOffText
        // 切换闪光灯状态图标
        binding.ivFlashlightOn.visibility = View.VISIBLE
        binding.ivFlashlightOff.visibility = View.GONE
    }

    /**
     * 判断相机权限是否已经授权
     */
    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_PERMISSION)
        } else {
            cameraProviderFuture.addListener(Runnable {
                val cameraProvider = cameraProviderFuture.get()
                bindScan(cameraProvider, binding.overlay.width, binding.overlay.height)
            }, ContextCompat.getMainExecutor(this))
        }
    }

    /**
     * 封装手动发起相机权限请求
     */
    fun selfRequestCameraPermission() {
        requestCameraPermission()
    }

    /**
     * 判断读取相册权限是否已经授权
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestReadMediaImagesPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_IMAGES), PICK_IMAGE_REQUEST)
        } else {
            openAlbum()
        }
    }

    /**
     * 封装手动发起读取相册权限请求
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun selfRequestReadMediaImagesPermission() {
        requestReadMediaImagesPermission()
    }

    /**
     * 打开相册
     */
    fun openAlbum() {
        // 打开相册选择图片
        if (ScancodeConfig.onOpenAlbum != null) {
            ScancodeConfig.onOpenAlbum!!.invoke()
        } else{
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }
    }

    /**
     * 接收相册选取的图片
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // 接收相册选取的图片
        if (data != null) {
            if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data.data != null) {
                val uri: Uri? = data.data
                try {
                    // 将选中的图片转换为Bitmap
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    // 使用ML Kit的BarcodeScanner进行扫描
                    // 关闭相机预览
                    cameraProviderFuture.get().unbindAll()
                    // 隐藏闪光灯按钮
                    hideFlashlight()
                    // 显示取消文本，隐藏关闭按钮
                    binding.tvCancel.visibility = View.VISIBLE
                    binding.ivExit.visibility = View.GONE
                    // 关闭overlay
                    binding.overlay.visibility = View.GONE
                    // 显示预览界面
                    binding.ivPhotoPreview.visibility = View.VISIBLE
                    binding.ivPhotoPreview.setImageBitmap(bitmap)
                    // 解析图片中的二维码
                    scanBarcodes(bitmap)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * 接收权限请求结果
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予
                cameraProviderFuture.addListener(Runnable {
                    val cameraProvider = cameraProviderFuture.get()
                    bindScan(cameraProvider, binding.overlay.width, binding.overlay.height)
                }, ContextCompat.getMainExecutor(this))
            } else {
                // 权限未授予
                Toast.makeText(this, "请授予相机权限", Toast.LENGTH_SHORT).show()
                // 判断是否设置了拒绝相机权限的回调
                if (ScancodeConfig.onCameraPermissionDenied != null) {
                    ScancodeConfig.onCameraPermissionDenied!!.invoke()
                }
                finish()
            }
        }

        if (requestCode == PICK_IMAGE_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openAlbum()
            } else {
                Toast.makeText(this, "请授予读取相册权限", Toast.LENGTH_SHORT).show()
                // 判断是否设置了拒绝相册权限的回调
                if (ScancodeConfig.onAlbumPermissionDenied != null) {
                    ScancodeConfig.onAlbumPermissionDenied!!.invoke()
                }
            }
        }
    }

    /**
     * 隐藏闪光灯按钮
     */
    private fun hideFlashlight() {
        binding.llFlashlight.visibility = View.GONE
    }

    private fun isSetFlashOffTextColor() : Boolean {
        return ScancodeConfig.flashOffTextColor.isNotEmpty()
    }

    private fun isSetFlashOnTextColor() : Boolean {
        return ScancodeConfig.flashOnTextColor.isNotEmpty()
    }

    private fun isSetExitText() : Boolean {
        return ScancodeConfig.exitText.isNotEmpty()
    }

    private fun isSetTip() : Boolean {
        return ScancodeConfig.tip.isNotEmpty()
    }

    private fun isSetFailureTip() : Boolean {
        return ScancodeConfig.failureTip.isNotEmpty()
    }

    private fun isSetSuccessTip() : Boolean {
        return ScancodeConfig.successTip.isNotEmpty()
    }

    private fun isSetFlashOnColor() : Boolean {
        return gConfig != null && gConfig?.flashOnColor?.isNotEmpty() == true
    }

    private fun isSetFlashOffColor() : Boolean {
        return ScancodeConfig.flashOffColor.isNotEmpty()
    }

    private fun isSetConfigCancelText() : Boolean {
        return ScancodeConfig.cancelText.isNotEmpty()
    }

    private fun isSetConfigFlashOffText() : Boolean {
        return gConfig != null && gConfig?.flashOffText?.isNotEmpty() == true
    }

    private fun isSetConfigFlashOffTip() : Boolean {
        return gConfig != null && gConfig?.flashOffTip?.isNotEmpty() == true
    }

    private fun isSetConfigFlashOnText() : Boolean {
        return gConfig != null && gConfig?.flashOnText?.isNotEmpty() == true
    }

    private fun isSetConfigFlashOnTip() : Boolean {
        return gConfig != null && gConfig?.flashOnTip?.isNotEmpty() == true
    }

    private fun isSetConfigTitle() : Boolean {
        return ScancodeConfig.title.isNotEmpty()
    }

    inner class OverlayListener : ViewTreeObserver.OnGlobalLayoutListener {
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun onGlobalLayout() {
//            cameraProviderFuture.addListener(Runnable {
//                val cameraProvider = cameraProviderFuture.get()
//                bindScan(cameraProvider, binding.overlay.width, binding.overlay.height)
//            }, ContextCompat.getMainExecutor(this@BarcodeScanningActivity))
            requestCameraPermission()
            // 监听点击关闭按钮事件
            binding.ivExit.setOnClickListener {
                if (ScancodeConfig.backFinish) {
                    close()
                }

                if (ScancodeConfig.onBack != null) {
                    ScancodeConfig.onBack!!.invoke()
                }
            }
            // 监听点击取消按钮事件
            binding.tvCancel.setOnClickListener {
                restartScan()
                if (ScancodeConfig.cancelFinish) {
                    close()
                }

                if (ScancodeConfig.onCancel != null) {
                    ScancodeConfig.onCancel!!.invoke()
                }
                // 隐藏取消文本，显示关闭按钮
                binding.tvCancel.visibility = View.GONE
                binding.ivExit.visibility = View.VISIBLE
            }
            // 监听右上角相册按钮事件
            binding.ivPhoto.setOnClickListener {
                if (ScancodeConfig.onAlbum != null) {
                    ScancodeConfig.onAlbum!!.invoke()
                } else {
                    // 打开相册选取图片
                    requestReadMediaImagesPermission()
//                    Toast.makeText(
//                        this@BarcodeScanningActivity,
//                        "相册选取待开发...",
//                        Toast.LENGTH_SHORT
//                    ).show()
                }
            }
            // 监听闪光灯按钮事件
            binding.llFlashlight.setOnClickListener {
                // 切换闪光灯状态
                val enableTorch = camera!!.cameraInfo.torchState.value!! > 0 // 闪光灯是否开启
                // 判断闪光灯状态，弹出相应的提示
                if (!enableTorch) {
                    // 打开闪光灯
                    turnOnFlashlight()
                    if (ScancodeConfig.onFlashOn != null) {
                        ScancodeConfig.onFlashOn!!.invoke()
                    }
                } else {
                    // 关闭闪光灯
                    turnOffFlashlight()
                    if (ScancodeConfig.onFlashOff != null) {
                        ScancodeConfig.onFlashOff!!.invoke()
                    }
                }
            }
            binding.previewView.setOnTouchListener { _, event ->
                // 将触摸事件传递给手势监听器
                scaleGestureDetector.onTouchEvent(event)
                // 将双击事件传递给手势监听器
                gestureDetector.onTouchEvent(event)
                return@setOnTouchListener true
            }
            binding.overlay.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    /**
     * 解析图片中的二维码
     */
    fun scanBarcodes(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val scanner = BarcodeScanning.getClient()
        val barcodesList = ArrayList<Barcode>()
        val analyzerList: ArrayList<QRCodeData> = ArrayList()
        // 初始化扫描结果矩形
        val list = ArrayList<Rect>()
        var responseData = Any()
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    // 将扫描结果添加到集合中
                    barcodesList.addAll(barcodes)
                    val successTip = if (isSetSuccessTip()) ScancodeConfig.successTip else getString(R.string.scanning_succeeded_single)
                    val failureTip = if (isSetFailureTip()) ScancodeConfig.failureTip else getString(R.string.scanning_failed)
                    // 弹出成功提示
                    if (ScancodeConfig.showTip) {
                        // 设置成功提示
                        Toast.makeText(this, successTip, Toast.LENGTH_SHORT).show()
                    }
                    if (barcodes.isNotEmpty()) {
                        successUpdate()
                    }
                    // 根据二维码数量，更新扫描成功提示文本
                    if (barcodes.isEmpty()) {
                        binding.tvTips.text = failureTip
                    }
                    if (barcodes.size > 1) {
                        binding.tvTips.text = getString(R.string.scanning_succeeded_multi)
                    } else {
                        binding.tvTips.text = successTip
                    }
                    //解绑当前所有相机操作
                    cameraProviderFuture.get().unbindAll()
                    //初始化缩放比例
                    initScale(bitmap.width, bitmap.height)
                    // 遍历所有扫描到的二维码
                    for (barcode in barcodes) {
                        // 获取扫描结果值
                        var rawValue = barcode.rawValue
                        CodeInfo.rawValue=rawValue
                        if(ScancodeConfig.needImage){
                            CodeInfo.bitmap=bitmapToUri(bitmap,this, Bitmap.CompressFormat. JPEG,false).toString()
                        }
                        CodeInfo.proxy=null
                        CodeInfo.barcode=barcode
                        // 根据扫描结果类型，进行相应的操作
                        when (barcode.valueType) {
                            // 扫描结果为 URL
                            Barcode.TYPE_URL -> {
                                // 打印扫码结果
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                // 构建二维码数据
                                val qrCodeData = QRCodeData.Url(rawValue,CodeInfo)
                                analyzerList.add(qrCodeData)
                            }
                            // 扫描结果为 WIFI 信息
                            Barcode.TYPE_WIFI -> {
                                // 打印扫码结果
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                // 获取 WIFI 名称
                                val ssid = barcode.wifi?.ssid
                                // 获取 WIFI 密码
                                val password = barcode.wifi?.password
                                // 获取 WIFI 加密类型
                                val type = barcode.wifi?.encryptionType
                                // 构建二维码数据
                                val qrCodeData = QRCodeData.Wifi(ssid, password, type.toString(),CodeInfo)
                                analyzerList.add(qrCodeData)
                            }
                            // 扫描结果为联系人信息
                            Barcode.TYPE_CONTACT_INFO -> {
                                // 打印扫码结果
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                // 获取联系人姓名
                                val name = barcode.contactInfo?.name
                                // 获取联系人地址
                                val address = barcode.contactInfo?.addresses?.get(0)?.addressLines?.get(0)
                                // 获取联系人邮箱
                                val email = barcode.contactInfo?.emails?.get(0)?.address
                                // 获取联系人电话
                                val tel = barcode.contactInfo?.phones?.get(0)?.number
                                // 构建二维码数据
                                val qrCodeData = QRCodeData.Contact(name, address, tel, email,CodeInfo)
                                analyzerList.add(qrCodeData)
                            }
                            // 扫描结果为短信
                            Barcode.TYPE_SMS -> {
                                // 打印
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                // 获取短信号码
                                val number = barcode.sms?.phoneNumber
                                // 获取短信内容
                                val content = barcode.sms?.message
                                // 构建二维码数据
                                val qrCodeData = QRCodeData.Sms(number, content,CodeInfo)
                                analyzerList.add(qrCodeData)
                            }
                            // 扫描结果为电话号码
                            Barcode.TYPE_PHONE -> {
                                // 打印扫码结果
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                // 获取电话号码
                                val number = barcode.phone?.number
                                // 构建二维码数据
                                val qrCodeData = QRCodeData.Phone(number,CodeInfo)
                                analyzerList.add(qrCodeData)
                            }
                            // 扫描结果为电子邮件
                            Barcode.TYPE_EMAIL -> {
                                // 打印扫码结果
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                // 获取邮件地址
                                val address = barcode.email?.address
                                // 获取邮件主题
                                val subject = barcode.email?.subject
                                // 获取邮件内容
                                val body = barcode.email?.body
                                // 构建二维码数据
                                val qrCodeData = QRCodeData.Email(address, subject, body,CodeInfo)
                                analyzerList.add(qrCodeData)
                            }
                            // 扫描结果为日历事件
                            Barcode.TYPE_CALENDAR_EVENT -> {
                                // 打印扫码结果
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                // 获取事件名称
                                val name = barcode.calendarEvent?.summary
                                // 获取事件开始时间
                                val start = barcode.calendarEvent?.start
                                // 获取事件结束时间
                                val end = barcode.calendarEvent?.end
                                // 获取事件地点
                                val location = barcode.calendarEvent?.location
                                // 获取事件描述
                                val description = barcode.calendarEvent?.description
                                // 构建二维码数据
                                val qrCodeData = QRCodeData.Calendar(name, location, description, start, end,CodeInfo)
                                analyzerList.add(qrCodeData)
                            }
                            // 扫描结果为地理位置
                            Barcode.TYPE_GEO -> {
                                // 打印扫码结果
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                // 获取纬度
                                val latitude = barcode.geoPoint?.lat
                                // 获取经度
                                val longitude = barcode.geoPoint?.lng
                                // 构建二维码数据
                                val qrCodeData = QRCodeData.Geo(latitude, longitude,CodeInfo)
                                analyzerList.add(qrCodeData)
                            }
                            // 扫描结果为文本
                            Barcode.TYPE_TEXT -> {
                                // 打印扫码结果
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                // 构建二维码数据
                                val qrCodeData = QRCodeData.Text(rawValue!!,CodeInfo)
                                analyzerList.add(qrCodeData)
                            }
                            // 扫描结果为驾驶证信息
                            Barcode.TYPE_DRIVER_LICENSE -> {
                                // 打印扫码结果
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                // 获取驾驶证姓
                                val firstName = barcode.driverLicense?.firstName
                                // 获取驾驶证名
                                val lastName = barcode.driverLicense?.lastName
                                // 获取驾驶证中间名
                                val middleName = barcode.driverLicense?.middleName
                                // 获取驾驶证地址州
                                val addressState = barcode.driverLicense?.addressState
                                // 获取驾驶证地址城市
                                val addressCity = barcode.driverLicense?.addressCity
                                // 获取驾驶证地址街道
                                val addressStreet = barcode.driverLicense?.addressStreet
                                // 获取驾驶证地址邮编
                                val addressZip = barcode.driverLicense?.addressZip
                                // 获取驾驶证性别
                                val gender = barcode.driverLicense?.gender
                                // 获取驾驶证号码
                                val licenseNumber = barcode.driverLicense?.licenseNumber
                                // 获取驾驶证发行国家
                                val issuingCountry = barcode.driverLicense?.issuingCountry
                                // 获取驾驶证出生日期
                                val birthDate = barcode.driverLicense?.birthDate
                                // 获取驾驶证发行日期
                                val issueDate = barcode.driverLicense?.issueDate
                                // 获取驾驶证到期日期
                                val expiryDate = barcode.driverLicense?.expiryDate
                                // 获取驾驶证文件类型
                                val documentType = barcode.driverLicense?.documentType
                                // 构建二维码数据
                                val qrCodeData = QRCodeData.DriverLicense(
                                    firstName,
                                    lastName,
                                    middleName,
                                    addressState,
                                    addressCity,
                                    addressStreet,
                                    addressZip,
                                    birthDate,
                                    documentType,
                                    expiryDate,
                                    gender,
                                    issueDate,
                                    licenseNumber,
                                    issuingCountry,
                                    CodeInfo
                                )
                                analyzerList.add(qrCodeData)
                            }
                            // 扫描结果为未知类型
                            Barcode.TYPE_UNKNOWN -> {
                                // 打印扫码结果
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                // 构建二维码数据
                                val qrCodeData = QRCodeData.Text(rawValue!!,CodeInfo)
                                analyzerList.add(qrCodeData)
                            }
                            // 否则为默认的文本类型数据构建
                            else -> {
                                val qrCodeData = QRCodeData.Text(rawValue!!,CodeInfo)
                                analyzerList.add(qrCodeData)
                            }
                        }
                        // 获取扫描结果类型
                        barcode.boundingBox?.let {//扫描二维码的外边框矩形
                            // 相册选取图片的预览界面不需要转换坐标
//                            val rect = translateRect(it)
                            list.add(it)
//                                Log.i(
//                                    TAG,
//                                    "scanBarcodes: left:${it.left} right:${it.right} top:${it.top} bottom:${it.bottom}"
//                                )
                        }
                    }
                    // 显示覆盖层
                    showViewMask()
                    binding.ivPhotoPreview.setRectList(list)
                    binding.ivPhotoPreview.setAnalyzerList(analyzerList)
                    binding.ivPhotoPreview.setBarcodeResultList(barcodesList)
                    // 关闭解析器
                    scanner.close()
                    // 初始化回调结果
                    ResponseStateConfig.statusCode = ResponseStateCode.SUCCESS
                    ResponseStateConfig.message = "扫码成功"
                    responseData = analyzerList
                    ResponseStateConfig.data = responseData
//                    ResponseStateConfig.barcode=
//                    Log.d("BARCODE",responseData.)
                    if (ScancodeConfig.beforeSuccess != null) {
                        ScancodeConfig.beforeSuccess!!.invoke(ResponseStateConfig)
                    }
                    // 成功回调
                    if (ScancodeConfig.onSuccess != null) {
                        ScancodeConfig.onSuccess!!.invoke(ResponseStateConfig)
                    }
                }
            }
            .addOnFailureListener {
//                Log.e(TAG, "扫描失败：${it.message}")
                // 扫描失败时，弹出失败提示
                if (ScancodeConfig.showTip) {
                    // 设置失败提示
                    val failureTip = ScancodeConfig.failureTip.ifEmpty { getString(R.string.scanning_failed) }
                    Toast.makeText(this, failureTip, Toast.LENGTH_SHORT).show()
                }
                // 设置失败回调
                // 初始化回调结果
                ResponseStateConfig.statusCode = ResponseStateCode.FAILURE
                ResponseStateConfig.message = "扫码失败"
                responseData = it
                ResponseStateConfig.data = responseData
                if (ScancodeConfig.onFailure != null) {
                    ScancodeConfig.onFailure!!.invoke(ResponseStateConfig)
                }
            }
            .addOnCompleteListener {
                // 初始化回调结果
                ResponseStateConfig.statusCode = ResponseStateCode.SUCCESS
                ResponseStateConfig.message = "扫码完成"
                ResponseStateConfig.data = responseData
                if (ScancodeConfig.onComplete != null) {
                    ScancodeConfig.onComplete!!.invoke(ResponseStateConfig)
                }
            }
    }

    /**
     * 识别成功后界面的更新调整
     */
    private fun successUpdate() {
        binding.previewView.animate().alpha(0.5f).setDuration(300).start()
        // 隐藏关闭按钮，显示取消文本
        binding.ivExit.visibility = View.GONE
        binding.tvCancel.visibility = View.VISIBLE
        // 隐藏闪光灯按钮
        hideFlashlight()
        // 隐藏相册按钮
        binding.ivPhoto.visibility = View.GONE
        // 隐藏标题
        binding.tvTitle.visibility = View.GONE
        // 扫描成功后，震动一下
        vibrate()
    }

    fun isUriValid(context: Context, uri: Uri): Boolean {
        return try {
            Log.d("ImageUtils", "检查 URI 是否有效: $uri")

            // 尝试打开输入流
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use {
                Log.d("ImageUtils", "URI 有效，输入流可打开")
                return true
            }

            // 如果无法打开输入流，尝试查询内容提供者
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media.SIZE, MediaStore.Images.Media.MIME_TYPE),
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(MediaStore.Images.Media.SIZE)
                    val mimeTypeIndex = it.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)

                    if (sizeIndex != -1) {
                        val size = it.getLong(sizeIndex)
                        Log.d("ImageUtils", "URI 有效，文件大小: ${size} 字节")
                        return size > 0
                    }

                    if (mimeTypeIndex != -1) {
                        val mimeType = it.getString(mimeTypeIndex)
                        Log.d("ImageUtils", "URI 有效，MIME 类型: ${mimeType}")
                        return true
                    }
                }
            }

            Log.e("ImageUtils", "URI 无效: 无法打开输入流或查询内容提供者")
            return false
        } catch (e: Exception) {
            Log.e("ImageUtils", "URI 验证失败: ${e.message}")
            return false
        }
    }

    /**
     * 绑定相机扫描
     */
    @SuppressLint("UnsafeExperimentalUsageError")
    private fun bindScan(cameraProvider: ProcessCameraProvider,width : Int, height : Int, delay : Long = 0) {
        Log.i(TAG, "bindScan: width:$width height:$height")

        // 预览
        val preview : Preview = Preview.Builder()
            .build()

        //绑定预览
        preview.setSurfaceProvider(binding.previewView.surfaceProvider)

        //使用后置相机
        val cameraSelector : CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        var b:Bitmap?=null
        //配置图片扫描
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(width, height))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        //将相机绑定到当前控件的生命周期
        camera = cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, imageAnalysis, preview)
        // 相机控制器
        val cameraControl = camera!!.cameraControl

        // 调整相机焦距
        val zoomRatio = if (ScancodeConfig.initZoomRatio != 1.0f) ScancodeConfig.initZoomRatio else 1.6f
        setZoom(zoomRatio)

        // 设置开始扫描延迟
        delayRunnable = Runnable {
            val barcodesList = ArrayList<Barcode>()
            val analyzerList: ArrayList<QRCodeData> = ArrayList()
            // 初始化扫描结果矩形
            val list = ArrayList<RectF>()
            //绑定图片扫描解析
            imageAnalysis.setAnalyzer(
                Executors.newSingleThreadExecutor(),
                QRCodeAnalyser(
                    success = { barcodes, imageProxy->
                        var image="";
                        if(ScancodeConfig.needImage){
                            var ur=toUri(imageProxy,this,Bitmap.CompressFormat. JPEG,false)
                            if(ur!=null){
                                image=ur.toString()
                                Log.d("ImageUtils",isUriValid(this,Uri.parse(image)).toString())
                            }
                            Log.d("ImageUtils",image.toString())
                        }
                        val imageWidth=imageProxy.width.toFloat()
                        val imageHeight=imageProxy.height.toFloat()
                        // 将扫描结果添加到集合中
                        barcodesList.addAll(barcodes)
                        val successTip = if (isSetSuccessTip()) ScancodeConfig.successTip else getString(R.string.scanning_succeeded_single)
                        val failureTip = if (isSetFailureTip()) ScancodeConfig.failureTip else getString(R.string.scanning_failed)
                        // 弹出成功提示
                        if (ScancodeConfig.showTip) {
                            // 设置成功提示
                            Toast.makeText(this, successTip, Toast.LENGTH_SHORT).show()
                        }
                        if (barcodes.isNotEmpty()) {
                            successUpdate()
                        }
                        // 根据二维码数量，更新扫描成功提示文本
                        if (barcodes.isEmpty()) {
                            binding.tvTips.text = failureTip
                        }
                        if (barcodes.size > 1) {
                            binding.tvTips.text = getString(R.string.scanning_succeeded_multi)
                        } else {
                            binding.tvTips.text = successTip
                        }
                        //解绑当前所有相机操作
                        cameraProvider.unbindAll()
                        //初始化缩放比例
                        initScale(imageWidth.toInt(), imageHeight.toInt())

                        // 遍历所有扫描到的二维码
                        for (barcode in barcodes) {
                            // 获取扫描结果值
                            val rawValue = barcode.rawValue
                            CodeInfo.bitmap=image
                            CodeInfo.rawValue=rawValue
                            CodeInfo.proxy = imageProxy
                            CodeInfo.barcode=barcode
                            // 根据扫描结果类型，进行相应的操作
                            when (barcode.valueType) {
                                // 扫描结果为 URL
                                Barcode.TYPE_URL -> {
                                    // 打印扫码结果
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                    // 构建二维码数据
                                    val qrCodeData = QRCodeData.Url(rawValue,CodeInfo)
                                    analyzerList.add(qrCodeData)
                                }
                                // 扫描结果为 WIFI 信息
                                Barcode.TYPE_WIFI -> {
                                    // 打印扫码结果
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                    // 获取 WIFI 名称
                                    val ssid = barcode.wifi?.ssid
                                    // 获取 WIFI 密码
                                    val password = barcode.wifi?.password
                                    // 获取 WIFI 加密类型
                                    val type = barcode.wifi?.encryptionType
                                    // 构建二维码数据
                                    val qrCodeData = QRCodeData.Wifi(ssid, password, type.toString(),CodeInfo)
                                    analyzerList.add(qrCodeData)
                                }
                                // 扫描结果为联系人信息
                                Barcode.TYPE_CONTACT_INFO -> {
                                    // 打印扫码结果
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                    // 获取联系人姓名
                                    val name = barcode.contactInfo?.name
                                    // 获取联系人地址
                                    val address = barcode.contactInfo?.addresses?.get(0)?.addressLines?.get(0)
                                    // 获取联系人邮箱
                                    val email = barcode.contactInfo?.emails?.get(0)?.address
                                    // 获取联系人电话
                                    val tel = barcode.contactInfo?.phones?.get(0)?.number
                                    // 构建二维码数据
                                    val qrCodeData = QRCodeData.Contact(name, address, tel, email,CodeInfo)
                                    analyzerList.add(qrCodeData)
                                }
                                // 扫描结果为短信
                                Barcode.TYPE_SMS -> {
                                    // 打印
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                    // 获取短信号码
                                    val number = barcode.sms?.phoneNumber
                                    // 获取短信内容
                                    val content = barcode.sms?.message
                                    // 构建二维码数据
                                    val qrCodeData = QRCodeData.Sms(number, content,CodeInfo)
                                    analyzerList.add(qrCodeData)
                                }
                                // 扫描结果为电话号码
                                Barcode.TYPE_PHONE -> {
                                    // 打印扫码结果
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                    // 获取电话号码
                                    val number = barcode.phone?.number
                                    // 构建二维码数据
                                    val qrCodeData = QRCodeData.Phone(number,CodeInfo)
                                    analyzerList.add(qrCodeData)
                                }
                                // 扫描结果为电子邮件
                                Barcode.TYPE_EMAIL -> {
                                    // 打印扫码结果
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                    // 获取邮件地址
                                    val address = barcode.email?.address
                                    // 获取邮件主题
                                    val subject = barcode.email?.subject
                                    // 获取邮件内容
                                    val body = barcode.email?.body
                                    // 构建二维码数据
                                    val qrCodeData = QRCodeData.Email(address, subject, body,CodeInfo)
                                    analyzerList.add(qrCodeData)
                                }
                                // 扫描结果为日历事件
                                Barcode.TYPE_CALENDAR_EVENT -> {
                                    // 打印扫码结果
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                    // 获取事件名称
                                    val name = barcode.calendarEvent?.summary
                                    // 获取事件开始时间
                                    val start = barcode.calendarEvent?.start
                                    // 获取事件结束时间
                                    val end = barcode.calendarEvent?.end
                                    // 获取事件地点
                                    val location = barcode.calendarEvent?.location
                                    // 获取事件描述
                                    val description = barcode.calendarEvent?.description
                                    // 构建二维码数据
                                    val qrCodeData = QRCodeData.Calendar(name, location, description, start, end,CodeInfo)
                                    analyzerList.add(qrCodeData)
                                }
                                // 扫描结果为地理位置
                                Barcode.TYPE_GEO -> {
                                    // 打印扫码结果
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                    // 获取纬度
                                    val latitude = barcode.geoPoint?.lat
                                    // 获取经度
                                    val longitude = barcode.geoPoint?.lng
                                    // 构建二维码数据
                                    val qrCodeData = QRCodeData.Geo(latitude, longitude,CodeInfo)
                                    analyzerList.add(qrCodeData)
                                }
                                // 扫描结果为文本
                                Barcode.TYPE_TEXT -> {
                                    // 打印扫码结果
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                    // 构建二维码数据
                                    val qrCodeData = QRCodeData.Text(rawValue!!,CodeInfo)
                                    analyzerList.add(qrCodeData)
                                }
                                // 扫描结果为驾驶证信息
                                Barcode.TYPE_DRIVER_LICENSE -> {
                                    // 打印扫码结果
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                    // 获取驾驶证姓
                                    val firstName = barcode.driverLicense?.firstName
                                    // 获取驾驶证名
                                    val lastName = barcode.driverLicense?.lastName
                                    // 获取驾驶证中间名
                                    val middleName = barcode.driverLicense?.middleName
                                    // 获取驾驶证地址州
                                    val addressState = barcode.driverLicense?.addressState
                                    // 获取驾驶证地址城市
                                    val addressCity = barcode.driverLicense?.addressCity
                                    // 获取驾驶证地址街道
                                    val addressStreet = barcode.driverLicense?.addressStreet
                                    // 获取驾驶证地址邮编
                                    val addressZip = barcode.driverLicense?.addressZip
                                    // 获取驾驶证性别
                                    val gender = barcode.driverLicense?.gender
                                    // 获取驾驶证号码
                                    val licenseNumber = barcode.driverLicense?.licenseNumber
                                    // 获取驾驶证发行国家
                                    val issuingCountry = barcode.driverLicense?.issuingCountry
                                    // 获取驾驶证出生日期
                                    val birthDate = barcode.driverLicense?.birthDate
                                    // 获取驾驶证发行日期
                                    val issueDate = barcode.driverLicense?.issueDate
                                    // 获取驾驶证到期日期
                                    val expiryDate = barcode.driverLicense?.expiryDate
                                    // 获取驾驶证文件类型
                                    val documentType = barcode.driverLicense?.documentType
                                    // 构建二维码数据
                                    val qrCodeData = QRCodeData.DriverLicense(
                                        firstName,
                                        lastName,
                                        middleName,
                                        addressState,
                                        addressCity,
                                        addressStreet,
                                        addressZip,
                                        birthDate,
                                        documentType,
                                        expiryDate,
                                        gender,
                                        issueDate,
                                        licenseNumber,
                                        issuingCountry,
                                        CodeInfo
                                    )
                                    analyzerList.add(qrCodeData)
                                }
                                // 扫描结果为未知类型
                                Barcode.TYPE_UNKNOWN -> {
                                    // 打印扫码结果
//                                    Log.d(TAG, "扫码结果：$rawValue")
                                    // 构建二维码数据
                                    val qrCodeData = QRCodeData.Text(rawValue!!,CodeInfo)
                                    analyzerList.add(qrCodeData)
                                }
                                // 否则为默认的文本类型数据构建
                                else -> {
                                    val qrCodeData = QRCodeData.Text(rawValue!!,CodeInfo)
                                    analyzerList.add(qrCodeData)
                                }
                            }

                            barcode.boundingBox?.let {//扫描二维码的外边框矩形
                                val rect = translateRect(it)
                                list.add(rect)
//                                Log.i(
//                                    TAG,
//                                    "bindScan: left:${it.left} right:${it.right} top:${it.top} bottom:${it.bottom}"
//                                )
                            }
                        }
                        // 显示覆盖层
                        showViewMask()
                        binding.overlay.setRectList(list)
                        binding.overlay.setAnalyzerList(analyzerList)
                        binding.overlay.setBarcodeResultList(barcodesList)
                        // 成功回调前的自定义结果处理
                        // 初始化回调结果
                        ResponseStateConfig.statusCode = ResponseStateCode.SUCCESS
                        ResponseStateConfig.message = "扫码成功"
                        ResponseStateConfig.data = analyzerList
                        if (ScancodeConfig.beforeSuccess != null) {
                            ScancodeConfig.beforeSuccess!!.invoke(ResponseStateConfig)
                        }
                        // 成功回调
                        if (ScancodeConfig.onSuccess != null) {
                            ScancodeConfig.onSuccess!!.invoke(ResponseStateConfig)
                        }
                        // 判断是否开启连续扫码，如果开启则继续扫码
                        if (ScancodeConfig.continuousScanning) {
                            restartScan()
                            // 连续扫码回调处理
                            // 初始化回调结果
                            ResponseStateConfig.statusCode = ResponseStateCode.SUCCESS
                            ResponseStateConfig.message = "连续扫码"
                            ResponseStateConfig.data = analyzerList
                            if (ScancodeConfig.onContinuous != null) {
                                ScancodeConfig.onContinuous!!.invoke(ResponseStateConfig)
                            }
                        }
                    },
                    failure = {
                        // 隐藏闪光灯按钮
                        hideFlashlight()
                        // 更新扫描失败提示文本
                        // 扫描失败时，弹出失败提示
                        if (ScancodeConfig.showTip) {
                            // 设置失败提示
                            val failureTip = ScancodeConfig.failureTip.ifEmpty { getString(R.string.scanning_failed) }
                            Toast.makeText(this, failureTip, Toast.LENGTH_SHORT).show()
                        }
                        // 设置失败回调
                        // 初始化回调结果
                        ResponseStateConfig.statusCode = ResponseStateCode.FAILURE
                        ResponseStateConfig.message = "扫码失败"
                        ResponseStateConfig.data = it
                        if (ScancodeConfig.onFailure != null) {
                            ScancodeConfig.onFailure!!.invoke(ResponseStateConfig)
                        }
                        // 解绑当前所有相机操作
                        cameraProvider.unbindAll()
                    },
                    complete = { barcodes, _ ->
                        // 隐藏闪光灯按钮
//                        hideFlashlight()
                        // 初始化回调结果
                        ResponseStateConfig.statusCode = ResponseStateCode.SUCCESS
                        ResponseStateConfig.message = "扫码完成"
                        ResponseStateConfig.data = barcodes
                        if (ScancodeConfig.onComplete != null) {
                            ScancodeConfig.onComplete!!.invoke(ResponseStateConfig)
                        }
                    },
                    zoomCallback = { zoomRatio ->
//                        Toast.makeText(this, "zoomRatio:$zoomRatio", Toast.LENGTH_SHORT).show()
                        // 设置缩放比例
                        setZoom(zoomRatio)
                        true
                    }
                )
            )


            // 初始化缩放手势监听器
            scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    if (!ScancodeConfig.touchZoom) return true
                    // 根据用户的手势调整焦距
                    val currentZoom = camera!!.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                    val deltaZoom = detector.scaleFactor
                    val newZoom = currentZoom * deltaZoom
                    // 设置缩放比例
                    cameraControl.setZoomRatio(newZoom)
                    if (ScancodeConfig.onZoom != null) {
                        ScancodeConfig.onZoom!!.invoke(newZoom)
                    }
                    return true
                }
            })
            // 初始化双击手势监听器
            gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (!ScancodeConfig.doubleTapZoom) return true
                    // 双击时，将焦点设置为触摸点
                    val factory = SurfaceOrientedMeteringPointFactory(
                        binding.previewView.width.toFloat(),
                        binding.previewView.height.toFloat()
                    )
                    val point = factory.createPoint(e.x, e.y)
                    val action = FocusMeteringAction.Builder(point).build()
                    cameraControl.startFocusAndMetering(action)
                    // 获取当前焦距
                    val currentZoom = camera!!.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                    // 如果焦距不为 1，则将焦距设置为 1
                    cameraControl.setZoomRatio(if (currentZoom != 1f) 1f else 2f)
                    if (ScancodeConfig.onDoubleTap != null) {
                        ScancodeConfig.onDoubleTap!!.invoke(currentZoom)
                    }
                    return true
                }
            })
        }
        delay(delay, delayRunnable)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun setZoom(zoomRatio: Float): Boolean {
        if (camera != null) {
//            Toast.makeText(this, "zoomRatio:$zoomRatio", Toast.LENGTH_SHORT).show()
            camera!!.cameraControl.setZoomRatio(zoomRatio)
            return true
        }

        return false
    }

    /**
     * 扫描成功后，检测设备是否支持震动，如果支持，则震动一下
     */
    private fun vibrate() {
        if (ScancodeConfig.vibrate) {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(100)
        }
    }

    /**
     * 显示闪光灯按钮
     */
    private fun showFlashlight() {
        if (ScancodeConfig.showFlash) {
            binding.llFlashlight.visibility = View.VISIBLE
        }
    }

    /**
     * 显示相册按钮
     */
    private fun showAlbum() {
        if (ScancodeConfig.showAlbum) {
            binding.ivPhoto.visibility = View.VISIBLE
        }
    }

    /**
     * 监听是否在进行缩放操作，缩放操作时，调用 zoomTo 方法
     */
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_UP) {
            zoomTo(1f)
        }
        return true
    }

    /**
     * 实现实例的释放，避免内存泄漏
     */
    override fun onDestroy() {
        super.onDestroy()
        try {
            cameraProviderFuture.cancel(true)
            // 取消延迟执行
            cancelDelay(delayRunnable)
        } catch (e: UninitializedPropertyAccessException) {
            // 处理未初始化的属性异常
            Log.e("BarcodeScanningActivity", "Error releasing resources", e)
        }
    }

    /**
     * 实现相机缩放
     */
    fun zoomTo(ratio: Float) {
        if (camera == null) {
            return
        }

        // 设置缩放比例
        camera!!.cameraControl.setZoomRatio(ratio)
    }

    private fun translateX(x: Float): Float = x * scaleX
    private fun translateY(y: Float): Float = y * scaleY

    //将扫描的矩形换算为当前屏幕大小
    private fun translateRect(rect: Rect) = RectF(
        translateX(rect.left.toFloat()),
        translateY(rect.top.toFloat()),
        translateX(rect.right.toFloat()),
        translateY(rect.bottom.toFloat())
    )

    private fun initScale(imageWidth : Int, imageHeight : Int){
        if(isPortraitMode(this)){
            scaleY = binding.overlay.height.toFloat() / imageWidth.toFloat()
            scaleX = binding.overlay.width.toFloat() / imageHeight.toFloat()
        }else{
            scaleY = binding.overlay.height.toFloat() / imageHeight.toFloat()
            scaleX = binding.overlay.width.toFloat() / imageWidth.toFloat()
        }
    }

    private fun isSetMaskColor() : Boolean {
        return ScancodeConfig.maskColor.isNotEmpty()
    }

    private fun isSetMaskAlpha() : Boolean {
        return ScancodeConfig.maskAlpha.toInt() != 0
    }

    private fun isSetMaskDuration() : Boolean {
        return ScancodeConfig.maskDuration.toInt() != 0
    }

    /**
     * 显示覆盖层
     */
    private fun showViewMask(){
        if (ScancodeConfig.showMask) {
            binding.viewMask.visibility = View.VISIBLE
            binding.viewMask.setBackgroundColor(if (isSetMaskColor()) Color.parseColor(ScancodeConfig.maskColor) else resources.getColor(R.color.colorViewMask))
            val alpha = if (isSetMaskAlpha()) ScancodeConfig.maskAlpha else 0f
            binding.viewMask.alpha = alpha
            val duration = if (isSetMaskDuration()) ScancodeConfig.maskDuration else 300
            binding.viewMask.animate().alpha(1f).setDuration(duration).start()
        }
    }

    /**
     * 隐藏覆盖层
     */
    private fun hideViewMask(){
        if (ScancodeConfig.showMask) {
            binding.viewMask.visibility = View.GONE
            binding.viewMask.setBackgroundColor(if (isSetMaskColor()) Color.parseColor(ScancodeConfig.maskColor) else resources.getColor(R.color.colorViewMask))
            val alpha = if (isSetMaskAlpha()) ScancodeConfig.maskAlpha else 1f
            binding.viewMask.alpha = alpha
            val duration = if (isSetMaskDuration()) ScancodeConfig.maskDuration else 300
            binding.viewMask.animate().alpha(0f).setDuration(duration).start()
        }
    }

    /**
     * 封装重新开始扫描的方法
     */
    fun restartScan() {
        // 显示标题
        binding.tvTitle.visibility = View.VISIBLE
        // 显示闪光灯按钮
        showFlashlight()
        // 显示相册按钮
        showAlbum()
        // 重置预览界面的亮度
        binding.previewView.animate().alpha(1f).setDuration(300).start()
        // 隐藏覆盖层
        hideViewMask()
        // 重置扫描结果提示文本
        binding.tvTips.text = getString(R.string.scanning_text)
        // 重置扫描结果矩形
        binding.overlay.setRectList(null)
        // 重置缩放比例
        scaleX = 0f
        scaleY = 0f
        // 刷新相机预览界面
        binding.overlay.restartScan()
        // 重新开始扫描
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = cameraProviderFuture.get()
            bindScan(cameraProvider, binding.overlay.width, binding.overlay.height)
        }, ContextCompat.getMainExecutor(this@BarcodeScanningActivity))
    }

    /**
     * 封装延迟执行的方法
     */
    private fun delay(delayMillis: Long, delayRunnable: Runnable) {
        handler.postDelayed(delayRunnable, delayMillis)
    }

    /**
     * 封装取消延迟执行的方法
     */
    private fun cancelDelay(delayRunnable: Runnable) {
        handler.removeCallbacks(delayRunnable)
    }

    /**
     * 关闭扫一扫界面
     */
    fun close() {
        finish()
    }

    /**
     * 设置启动扫描界面的配置信息
     */
    fun setConfig(config: ScancodeConfig) {
        gConfig = config
        ScancodeConfig.showLine = gConfig!!.showLine
        ScancodeConfig.title = gConfig!!.title
        ScancodeConfig.showAlbum = gConfig!!.showAlbum
        ScancodeConfig.cancelText = gConfig!!.cancelText
        ScancodeConfig.lineDuration = gConfig!!.lineDuration
        ScancodeConfig.markCircleAnimate = gConfig!!.markCircleAnimate
        ScancodeConfig.markCircleColor = gConfig!!.markCircleColor
        ScancodeConfig.markCircleRadius = gConfig!!.markCircleRadius
        ScancodeConfig.initZoomRatio= gConfig!!.initZoomRatio
        ScancodeConfig.doubleTapZoom = gConfig!!.doubleTapZoom
        ScancodeConfig.touchZoom = gConfig!!.touchZoom
        ScancodeConfig.showFlash = gConfig!!.showFlash
        ScancodeConfig.flashOnText = gConfig!!.flashOnText
        ScancodeConfig.flashOnTip = gConfig!!.flashOnTip
        ScancodeConfig.flashOnColor = gConfig!!.flashOnColor
        ScancodeConfig.flashOnTextColor = gConfig!!.flashOnTextColor
        ScancodeConfig.flashOffText = gConfig!!.flashOffText
        ScancodeConfig.flashOffTip = gConfig!!.flashOffTip
        ScancodeConfig.flashOffColor = gConfig!!.flashOffColor
        ScancodeConfig.flashOffTextColor = gConfig!!.flashOffTextColor
        ScancodeConfig.autoFullScreen = gConfig!!.autoFullScreen
        ScancodeConfig.showTip = gConfig!!.showTip
        ScancodeConfig.successTip = gConfig!!.successTip
        ScancodeConfig.failureTip = gConfig!!.failureTip
        ScancodeConfig.beforeSuccess = gConfig!!.beforeSuccess
        ScancodeConfig.onSuccess = gConfig!!.onSuccess
        ScancodeConfig.onFailure = gConfig!!.onFailure
        ScancodeConfig.onComplete = gConfig!!.onComplete
        ScancodeConfig.onBarcode = gConfig!!.onBarcode
        ScancodeConfig.tip = gConfig!!.tip
        ScancodeConfig.exitText = gConfig!!.exitText
        ScancodeConfig.showBack = gConfig!!.showBack
        ScancodeConfig.vibrate = gConfig!!.vibrate
        ScancodeConfig.lineStartY = gConfig!!.lineStartY
        ScancodeConfig.lineEndY = gConfig!!.lineEndY
        ScancodeConfig.batchScanning = gConfig!!.batchScanning
        ScancodeConfig.maxZoomRatio = gConfig!!.maxZoomRatio
        ScancodeConfig.centerBarcode = gConfig!!.centerBarcode
        ScancodeConfig.backFinish = gConfig!!.backFinish
        ScancodeConfig.onBack = gConfig!!.onBack
        ScancodeConfig.cancelFinish = gConfig!!.cancelFinish
        ScancodeConfig.onCancel = gConfig!!.onCancel
        ScancodeConfig.onAlbum = gConfig!!.onAlbum
        ScancodeConfig.onFlashOn = gConfig!!.onFlashOn
        ScancodeConfig.onFlashOff = gConfig!!.onFlashOff
        ScancodeConfig.onZoom = gConfig!!.onZoom
        ScancodeConfig.onDoubleTap = gConfig!!.onDoubleTap
        ScancodeConfig.markCircleStrokeColor = gConfig!!.markCircleStrokeColor
        ScancodeConfig.markCircleStrokeWidth = gConfig!!.markCircleStrokeWidth
        ScancodeConfig.continuousScanning = gConfig!!.continuousScanning
        ScancodeConfig.onContinuous = gConfig!!.onContinuous
        ScancodeConfig.showMask = gConfig!!.showMask
        ScancodeConfig.maskColor = gConfig!!.maskColor
        ScancodeConfig.maskAlpha = gConfig!!.maskAlpha
        ScancodeConfig.maskDuration = gConfig!!.maskDuration
        ScancodeConfig.onCameraPermissionDenied = gConfig!!.onCameraPermissionDenied
        ScancodeConfig.onAlbumPermissionDenied = gConfig!!.onAlbumPermissionDenied
    }
}