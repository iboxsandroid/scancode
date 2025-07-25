package com.itgz8.scancode

import com.google.mlkit.vision.barcode.common.Barcode

/**
 * 二维码/条形码解析数据类
 */
sealed class QRCodeData {
    // 文本
    data class Text(val text: String?,val codeInfo:CodeInfo) : QRCodeData()
    // URL
    data class Url(val url: String?,val codeInfo:CodeInfo) : QRCodeData()
    // WIFI
    data class Wifi(val ssid: String?, val password: String?, val type: String?,val codeInfo:CodeInfo) : QRCodeData()
    // 电话
    data class Phone(val number: String?,val codeInfo:CodeInfo) : QRCodeData()
    // 短信
    data class Sms(val number: String?, val message: String?,val codeInfo:CodeInfo) : QRCodeData()
    // 邮件
    data class Email(val address: String?, val subject: String?, val body: String?,val codeInfo:CodeInfo) : QRCodeData()
    // 联系人
    data class Contact(val name: Barcode.PersonName?, val address: String?, val phone: String?, val email: String?,val codeInfo:CodeInfo) : QRCodeData()
    // 地理位置
    data class Geo(val latitude: Double?, val longitude: Double?,val codeInfo:CodeInfo) : QRCodeData()
    // 日历
    data class Calendar(val summary: String?, val location: String?, val description: String?, val start: Barcode.CalendarDateTime?, val end: Barcode.CalendarDateTime?,val codeInfo:CodeInfo) : QRCodeData()
    // 驾驶证
    data class DriverLicense(
        val firstName: String?,
        val lastName: String?,
        val middleName: String?,
        val addressState: String?,
        val addressCity: String?,
        val addressStreet: String?,
        val addressZip: String?,
        val birthDate: String?,
        val documentType: String?,
        val expiryDate: String?,
        val gender: String?,
        val issueDate: String?,
        val licenseNumber: String?,
        val issuingCountry: String?,
        val codeInfo:CodeInfo
    ) : QRCodeData()
}