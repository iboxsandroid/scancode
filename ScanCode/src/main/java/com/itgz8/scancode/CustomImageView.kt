package com.itgz8.scancode

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.itgz8.scancode.R
import com.google.mlkit.vision.barcode.common.Barcode

class CustomImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : androidx.appcompat.widget.AppCompatImageView(context, attrs, defStyleAttr) {
    // 原点画笔
    private var paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 原点边框画笔
    private var paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = if (isSetStrokeColor()) Color.parseColor(ScancodeConfig.markCircleStrokeColor) else Color.WHITE
        strokeWidth = if (isSetStrokeWidth()) ScancodeConfig.markCircleStrokeWidth.toPx().toFloat() else 3f
    }

    // 原点中间的指示箭头画笔
    private var paintArrow = Paint(Paint.ANTI_ALIAS_FLAG)

    private var animator : ObjectAnimator? = null

    private var resultRect : RectF? = null

    private var rectList : ArrayList<Rect>? = null // 二维码数组

    private var analyzerList: ArrayList<QRCodeData> = ArrayList()

    private var barcodeResultList : ArrayList<Barcode>? = null // 二维码识别结果数组

    // 用于缓存每个二维码的缩放比例
    private var scaleFactors = mutableMapOf<Rect, Float>()

    set(value) {
        field = value
        invalidate()
    }

    init {
        paint.style = Paint.Style.FILL
        val color = if (isSetMarkCircleColor()) Color.parseColor(ScancodeConfig.markCircleColor) else ContextCompat.getColor(context ,
            R.color.colorPrimary)
        paint.color = color
        paint.strokeWidth = ScancodeConfig.markCircleStrokeWidth.toPx().toFloat()
    }

    private fun isSetMarkCircleColor() : Boolean {
        return ScancodeConfig.markCircleColor.isNotEmpty()
    }

    private fun isSetStrokeWidth(): Boolean {
        return ScancodeConfig.markCircleStrokeWidth.toInt() != 0
    }

    private fun isSetStrokeColor(): Boolean {
        return ScancodeConfig.markCircleStrokeColor.isNotEmpty()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 确保rectList和barcodeResultList不为空
        if (rectList != null && barcodeResultList != null && rectList!!.isNotEmpty() && barcodeResultList!!.isNotEmpty()) {
            parseResult(canvas)
        }
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    /**
     * 接收扫码结果，在二维码中心绘制一个圆点
     */
    private fun parseResult(canvas: Canvas) {
        rectList?.let { list ->
            if (list.isEmpty()) {
                return
            }

            list.forEach {
                val scaleFactor = scaleFactors[it] ?: 1f
                // 计算中心点
                val centerX = it.centerX().toFloat()
                val centerY = it.centerY().toFloat()
//                Log.d("CustomImageView", "centerX: $centerX, centerY: $centerY")
                val radius = ScancodeConfig.markCircleRadius * scaleFactor
                // 绘制原点
                canvas.drawCircle(centerX, centerY, radius, paint)
                // 绘制原点边框
                canvas.drawCircle(centerX, centerY, radius, paintBorder)
            }
        }
    }

    fun setRectList(list: ArrayList<Rect>?) {
        rectList = list
        rectList?.let {
            if (it.isNotEmpty()) {
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
                            if (rect.contains(x.toInt(), y.toInt())) {
                                // 识别结果
                                val index = list.indexOf(rect)
                                val qrCodeData = analyzerList[index]
                                val content = barcodeResultList?.get(list.indexOf(rect))?.rawValue
                                if (ScancodeConfig.showTip) {
                                    Toast.makeText(context, "识别结果：${content}555", Toast.LENGTH_SHORT).show()
                                }

                                // 点击回调
                                ResponseStateConfig.statusCode = ResponseStateCode.SUCCESS
                                ResponseStateConfig.message = "识别成功"
                                ResponseStateConfig.data = qrCodeData
                                Log.d("BARCODE",ResponseStateConfig.toString())
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
        rectList?.clear()
        invalidate()
    }
}