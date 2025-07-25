package com.itgz8.scancode

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.mlkit.vision.barcode.common.Barcode


class ScanOverlay(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    // 原点画笔
    private var paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 原点边框画笔
    private var paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = if (isSetStrokeColor()) Color.parseColor(ScancodeConfig.markCircleStrokeColor) else Color.WHITE
        strokeWidth = if (isSetStrokeWidth()) ScancodeConfig.markCircleStrokeWidth.toPx().toFloat() else 3f
    }

    private fun isSetStrokeWidth(): Boolean {
        return ScancodeConfig.markCircleStrokeWidth.toInt() != 0
    }

    private fun isSetStrokeColor(): Boolean {
        return ScancodeConfig.markCircleStrokeColor.isNotEmpty()
    }

    // 原点中间的指示箭头画笔
    private var paintArrow = Paint(Paint.ANTI_ALIAS_FLAG)

    private var animator : ObjectAnimator? = null

    private var bitmap: Bitmap

    private var resultRect : RectF? = null

    private var rectList : ArrayList<RectF>? = null // 二维码数组

    private var analyzerList : ArrayList<QRCodeData> = ArrayList()

    private var barcodeResultList : ArrayList<Barcode>? = null // 二维码识别结果数组

    private var showLine = ScancodeConfig.showLine

    private var floatYFraction = 0f
    set(value) {field = value
    invalidate()}

    // 用于缓存每个二维码的缩放比例
    private var scaleFactors = mutableMapOf<RectF, Float>()

    init {
        paint.style = Paint.Style.FILL
        val color = if (isSetMarkCircleColor()) Color.parseColor(ScancodeConfig.markCircleColor) else ContextCompat.getColor(context ,R.color.colorPrimary)
        paint.color = color
        paint.strokeWidth = ScancodeConfig.markCircleStrokeWidth.toPx().toFloat()
        bitmap = BitmapFactory.decodeResource(resources,R.drawable.icon_scan_line)
        getAnimator().start()
    }

    private fun isSetMarkCircleColor() : Boolean {
        return ScancodeConfig.markCircleColor.isNotEmpty()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        parseResult(canvas)
        if (showLine){
            val yStart = 400f
            val yEnd = height * floatYFraction - yStart
//            val yStart = if (ScancodeConfig.lineStartY == 0f) 400f else ScancodeConfig.lineStartY.toPx().toFloat()
//            val yEnd = ScancodeConfig.lineEndY?.invoke(floatYFraction) ?: (height * floatYFraction - yStart)
            val yCurrent = yStart + (yEnd - yStart) * floatYFraction
            canvas.drawBitmap(bitmap,(width - bitmap.width)/2f,yCurrent,paint)
        }
    }

    /**
     * 接收扫码结果，在二维码中心绘制一个圆点
     */
    private fun parseResult(canvas: Canvas?) {
        rectList?.let { list ->
            if (list.isEmpty()) {
                return
            }
            list.forEach {
                val scaleFactor = scaleFactors[it] ?: 1f
                val centerX = it.left + (it.right - it.left) / 2f
                val centerY = it.top + (it.bottom - it.top) / 2f
                val radius = ScancodeConfig.markCircleRadius * scaleFactor
                // 绘制原点
                canvas?.drawCircle(centerX, centerY, radius, paint)
                // 绘制原点边框
                canvas?.drawCircle(centerX, centerY, radius, paintBorder)
                // 加载指示箭头图片，并缩放到合适大小
                val arrowBitmap = BitmapFactory.decodeResource(resources, R.drawable.scan_default_result_point_arrow)
                val matrix = Matrix()
                matrix.postScale(0.2f * scaleFactor, 0.2f * scaleFactor)
                val arrowBitmapScale = Bitmap.createBitmap(
                    arrowBitmap,
                    0,
                    0,
                    arrowBitmap.width,
                    arrowBitmap.height,
                    matrix,
                    true
                )
                // 绘制指示箭头
                canvas?.drawBitmap(
                    arrowBitmapScale,
                    it.left + (it.right - it.left) / 2f - arrowBitmapScale.width / 2f,
                    it.top + (it.bottom - it.top) / 2f - arrowBitmapScale.height / 2f,
                    paintArrow
                )

            }
        }
    }

    private fun getAnimator(): ObjectAnimator {
        if (animator == null) {
            animator = ObjectAnimator.ofFloat(
                this,
                "floatYFraction",
                0f,
                1f
            )
            animator?.duration = ScancodeConfig.lineDuration
            animator?.repeatCount = -1 //-1代表无限循环
            animator?.addUpdateListener {
                val animatedValue = it.animatedValue as Float
                floatYFraction = animatedValue
                invalidate()
            }
        }
        return animator!!
    }

    fun setRectList(list: ArrayList<RectF>?) {
        rectList = list
        rectList?.let {
            if (it.isNotEmpty()) {
                showLine = false
                getAnimator().cancel()
                // 初始化缩放比例
                it.forEach { rect ->
                    scaleFactors[rect] = 0f
                }
                startHeartBeatAnimation()
                invalidate()
            }
        }
    }

    fun setAnalyzerList(list: ArrayList<QRCodeData>) {
        analyzerList = list
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        getAnimator().cancel()
    }

    fun setBarcodeResultList(list: ArrayList<Barcode>?){
        barcodeResultList = list
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            when (it.action) {
                MotionEvent.ACTION_DOWN -> {
                    val x = it.x
                    val y = it.y
                    rectList?.let { list ->
                        list.forEach { rect ->
                            if (rect.contains(x, y)) {
                                // 识别结果
                                val index = list.indexOf(rect)
                                val qrCodeData = analyzerList[index]
                                val content = barcodeResultList?.get(list.indexOf(rect))?.rawValue
                                if (ScancodeConfig.showTip) {
                                    Toast.makeText(context, "识别结果：${content}66", Toast.LENGTH_SHORT).show()
                                }
                                // 点击回调
                                ResponseStateConfig.statusCode = ResponseStateCode.SUCCESS
                                ResponseStateConfig.message = "识别成功"
                                ResponseStateConfig.data = qrCodeData
                                Log.d("BARCODE", ResponseStateConfig.toString())
                                if (ScancodeConfig.onBarcode != null) {
                                    ScancodeConfig.onBarcode?.invoke(ResponseStateConfig)
                                }
                                return true
                            }
                        }
                    }
                }

                else -> {}
            }
        }
        return super.onTouchEvent(event)
    }
    /**
     * 开启心跳动画
     */
    private fun startHeartBeatAnimation() {
        val startScaleValue = if (
            ScancodeConfig.markCircleAnimate
        ) 0.9f else 1f
        rectList?.forEach { rect ->
            val valueAnimator = ValueAnimator.ofFloat(startScaleValue, 1f)
            valueAnimator.duration = 1000
            valueAnimator.repeatCount = ValueAnimator.INFINITE
            valueAnimator.repeatMode = ValueAnimator.REVERSE
            valueAnimator.interpolator = FastOutSlowInInterpolator()
            valueAnimator.addUpdateListener { animation ->
                val animatedValue = animation.animatedValue as Float
                scaleFactors[rect] = animatedValue
                invalidate()
            }
            valueAnimator.start()
        }
    }

    private fun Float.toPx(): Int {
        return (this * Resources.getSystem().displayMetrics.density).toInt()
    }

    /**
     * 重新开始扫码
     */
    fun restartScan() {
        showLine = true
        rectList?.clear()
        getAnimator().start()
        invalidate()
    }
}
