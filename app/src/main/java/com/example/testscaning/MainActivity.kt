package com.example.testscaning

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import com.itgz8.scancode.BarcodeScanningActivity
import com.itgz8.scancode.QRCodeData
import com.itgz8.scancode.ResponseStateConfig
import com.itgz8.scancode.ScancodeConfig

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // 打开扫码界面
        val intent = Intent(this, BarcodeScanningActivity::class.java)
        // 获取扫码界面实例
        val activityInstance = BarcodeScanningActivity()
        // 点击单个二维码回调测试
        val onBarcodeCallback: (ResponseStateConfig) -> Unit = {
            Log.d("DEBUG-Activity-点击回调", it.data.toString())
            Toast.makeText(this.applicationContext, it.data.toString(), Toast.LENGTH_SHORT).show()
        }
        // 扫码成功回调测试
        val onSuccessCallback: (ResponseStateConfig) -> Unit = {
            Log.d("DEBUG-Activity-扫码成功回调", it.data.toString())
        }
        // 设置扫码界面配置
        activityInstance.setConfig(ScancodeConfig.apply {
            showLine = true
            showAlbum = true
            onOpenAlbum = fun(){
                Log.d("ImageAlbum", "点击相册")
            }
//            showTip = true
//            onSuccess = onSuccessCallback
//            onBarcode = onBarcodeCallback
//            markCircleAnimate = false
//            markCircleColor = "#000000"
//            doubleTapZoom = false
//            flashOffColor = "#03DAC5"
//            autoFullScreen = true
//            title = "扫描"
//            tip = "请将二维码放入框内"
//            showTip = false
//            cancelText = "关闭"
//            showFlash = true
//            lineDuration = 3000
//            vibrate = false
//            initZoomRatio = 1.0f
//            markCircleRadius = 50f
//            markCircleStrokeColor = "#FF7D00"
//            markCircleStrokeWidth = 3f
//            backFinish = true
//            showMask = true
//            maskColor = "#5000BC79"
        })
        val textInstance = findViewById<TextView>(R.id.tv_open_scan)
        textInstance.setOnClickListener {
            startActivity(intent)
        }
//        startActivity(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // 接受扫码结果
        if (requestCode == -1 && resultCode == RESULT_OK) {
            val result = data?.getStringExtra("SCAN_RESULT")
            // 调试时，可以在这里打断点，查看扫码结果
            Log.d("MainActivity", "扫码结果：$result")
            // do something
        }
    }
}