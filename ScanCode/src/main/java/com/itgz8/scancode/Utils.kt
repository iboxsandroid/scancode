import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.WindowManager
import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.itgz8.scancode.ScancodeConfig
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*


/**
 * dp转px
 */
fun Float.toPx(): Int {
    val resources = Resources.getSystem()
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        resources.displayMetrics
    ).toInt()
}


fun isPortraitMode(context: Context) : Boolean{
    val mConfiguration: Configuration = context.resources.configuration //获取设置的配置信息
    return mConfiguration.orientation == Configuration.ORIENTATION_PORTRAIT
}
fun toUri(imageProxy: ImageProxy, context: Context,
          format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
          useContentUri: Boolean = true): Uri? { // 默认使用 file:// 方式
    try {
        if(ScancodeConfig.needImage==false){
            return null;
        }
        val image = imageProxy.image
        if (image == null) {
            Log.e("ImageUtils", "保存图片失败: Image 为空")
            return null
        }

        // 生成唯一文件名
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "IMG_$timeStamp.${format.extension}"
        var u: Uri? = null

        if (useContentUri) {
            u = saveToMediaStore(context, image, format, fileName)
        } else {
            u = saveToExternalStorage(context, image, format, fileName)
        }

        // 检查 URI 是否有效
        if (u != null && isUriValid(context, u)) {
            return u
        } else {
            Log.e("ImageUtils", "保存图片失败: URI 无效")
            return null
        }
    } catch (e: Exception) {
        Log.e("ImageUtils", "保存图片失败", e)
        return null
    } finally {
        imageProxy.close() // 确保释放 ImageProxy 资源
    }
}

// 保存到外部存储 (file:// URI)
private fun saveToExternalStorage(
    context: Context,
    image: android.media.Image,
    format: Bitmap.CompressFormat,
    fileName: String
): Uri? {
    try {
        // 检查外部存储权限
        if (!isExternalStorageWritable()) {
            Log.e("ImageUtils", "外部存储不可写")
            return null
        }

        // 获取应用私有目录中的图片文件夹
        val storageDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用应用私有目录，不需要额外权限
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        } else {
            // Android 9 及以下可以使用公共存储目录
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                context.packageName)
                .apply { mkdirs() }
        }

        if (storageDir == null || !storageDir.exists()) {
            Log.e("ImageUtils", "存储目录不存在或无法创建")
            return null
        }

        val file = File(storageDir, fileName)

        // 将 Image 写入文件
        FileOutputStream(file).use { outputStream ->
            if (image.format == ImageFormat.JPEG) {
                // JPEG 格式直接写入
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                outputStream.write(bytes)
            } else {
                // 其他格式转换为 Bitmap 再写入
                val bitmap = image.toBitmapAlternative() ?: return null
                bitmap.compress(format, 90, outputStream)
            }
        }

        Log.d("ImageUtils", "图片已保存到: ${file.absolutePath}")
        return Uri.fromFile(file) // 返回 file:// 开头的 URI
    } catch (e: Exception) {
        Log.e("ImageUtils", "保存到外部存储失败: ${e.message}")
        return null
    }
}

// 检查外部存储是否可写
private fun isExternalStorageWritable(): Boolean {
    return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
}

// 替代的保存方法，使用不同的转换逻辑
private fun saveToMediaStoreAlternative(
    context: Context,
    image: android.media.Image,
    format: Bitmap.CompressFormat,
    fileName: String
): Uri? {
    try {
        val contentResolver = context.contentResolver

        // 设置 ContentValues
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        // 获取内容 URI
        val contentUri = when (format) {
            Bitmap.CompressFormat.JPEG, Bitmap.CompressFormat.PNG, Bitmap.CompressFormat.WEBP ->
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Files.getContentUri("external")
        }

        // 插入新条目
        val uri = contentResolver.insert(contentUri, contentValues) ?: return null

        // 将 Image 转换为 Bitmap 并保存
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            if (image.format == ImageFormat.JPEG) {
                // JPEG 格式直接写入
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                outputStream.write(bytes)
            } else {
                // 使用更健壮的方法将 YUV 转换为 Bitmap
                val bitmap = image.toBitmapAlternative() ?: return null
                bitmap.compress(format, 90, outputStream)
            }
        }

        // 标记为已完成
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            contentResolver.update(uri, contentValues, null, null)
        }

        return uri
    } catch (e: Exception) {
        Log.e("ImageUtils", "替代保存方法失败: ${e.message}")
        return null
    }
}

// 替代的 Image 转 Bitmap 方法，使用更健壮的转换逻辑
private fun android.media.Image.toBitmapAlternative(): Bitmap? {
    try {
        if (format == ImageFormat.JPEG) {
            val buffer = planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } else if (format == ImageFormat.YUV_420_888) {
            // 使用 YUV_420_888 格式的转换逻辑
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            // 复制 Y 数据
            yBuffer.get(nv21, 0, ySize)

            // 处理 UV 数据
            var vuOffset = ySize
            val vPixelStride = planes[2].pixelStride
            val vRowStride = planes[2].rowStride

            // 确保有足够的空间存储 UV 数据
            if (nv21.size < ySize + uSize + vSize) {
                Log.e("ImageUtils", "数组大小不足")
                return null
            }

            // 复制 V 和 U 数据（交错存储）
            for (row in 0 until height / 2) {
                for (col in 0 until width / 2) {
                    val vIndex = row * vRowStride + col * vPixelStride
                    val uIndex = row * vRowStride + col * vPixelStride + 1

                    if (vIndex < vSize && uIndex < uSize) {
                        nv21[vuOffset++] = vBuffer[vIndex]
                        nv21[vuOffset++] = uBuffer[uIndex]
                    }
                }
            }

            // 将 NV21 数据转换为 JPEG
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
            val jpegBytes = out.toByteArray()

            return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        }

        Log.e("ImageUtils", "不支持的图片格式: ${format}")
        return null
    } catch (e: Exception) {
        Log.e("ImageUtils", "Image 转 Bitmap 失败: ${e.message}")
        return null
    }
}

// 改进的 isUriValid 函数，提供更详细的日志
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


fun bitmapToUri(bitmap: Bitmap,context: Context,
                format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
                useContentUri: Boolean = true): Uri?
{
    try {
        // 生成唯一文件名
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "IMG_$timeStamp.${format.extension}"
        var u:Uri?=null
        if (useContentUri) {
            u=saveToMediaStoreBitmap(context, bitmap, format, fileName)
        } else {
            u= saveToExternalStorageBitmap(context, bitmap, format, fileName)
        }

        // 检查 URI 是否有效
        if (u != null && isUriValid(context, u)) {
            return u
        } else {
            Log.e("ImageUtils", "保存图片失败: URI 无效")
            return null
        }
    } catch (e: Exception) {
        Log.e("ImageUtils", "保存图片失败", e)
        return null
    }
}

private fun imageToBmp(image: Image):Bitmap?
{
    val width = image.width
    val height = image.height

    // 获取 Y、U、V 三个通道的像素数据
    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]

    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer

    // 计算每个通道的步长（像素行之间的间隔，可能有填充）
    val yRowStride = yPlane.rowStride
    val uRowStride = uPlane.rowStride
    val vRowStride = vPlane.rowStride

    // 计算每个像素的像素间隔（通常为 1，但可能有填充）
    val yPixelStride = yPlane.pixelStride
    val uPixelStride = uPlane.pixelStride
    val vPixelStride = vPlane.pixelStride

    // 创建一个字节数组存储 YUV 数据（转换为 NV21 格式，Bitmap 可识别）
    val nv21 = ByteArray(width * height * 3 / 2)
    var nv21Index = 0

    // 复制 Y 通道数据
    for (y in 0 until height) {
        for (x in 0 until width) {
            nv21[nv21Index++] = yBuffer[y * yRowStride + x * yPixelStride]
        }
    }

    // 复制 UV 通道数据（YUV_420_888 转 NV21 需要交错 UV）
    var uvRowStride = uRowStride
    var uvPixelStride = uPixelStride * 2 // 因为 U 和 V 交替存储
    for (y in 0 until height / 2) {
        for (x in 0 until width / 2) {
            val uIndex = y * uRowStride + x * uPixelStride
            val vIndex = y * vRowStride + x * vPixelStride
            // NV21 格式是 U、V 交替存储（注意顺序）
            nv21[nv21Index++] = vBuffer[vIndex] // V
            nv21[nv21Index++] = uBuffer[uIndex] // U
        }
    }

    // 将 NV21 格式的字节数组转换为 Bitmap
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val outputStream = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, outputStream)
    val jpegData = outputStream.toByteArray()
    return BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)

}

private fun saveToMediaStoreBitmap(
    context: Context,
    bitmap: Bitmap,
    format: Bitmap.CompressFormat,
    fileName: String
):Uri?
{
    val contentResolver = context.contentResolver

    // 设置 ContentValues
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 指定保存到 Pictures 目录
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.MediaColumns.IS_PENDING, 1) // 标记为临时文件
        }
    }

    // 获取内容 URI
    val contentUri = when (format) {
        Bitmap.CompressFormat.JPEG, Bitmap.CompressFormat.PNG, Bitmap.CompressFormat.WEBP ->
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        else -> MediaStore.Files.getContentUri("external")
    }
    Log.i("ImageUtils","一步"+contentUri.toString())
    // 插入新条目
    val uri = contentResolver.insert(contentUri, contentValues) ?: return null
    Log.i("ImageUtils","2步"+uri.toString())

    uri.let {
        context.contentResolver.openOutputStream(it)?.use { outputStream ->
            bitmap.compress(format, 90, outputStream)
        }
        // 标记为已完成
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)
        }
        it
    }

    Log.i("ImageUtils","3步"+uri.toString())
    // 标记为已完成
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        contentValues.clear()
        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
        contentResolver.update(uri, contentValues, null, null)
    }
    Log.i("ImageUtils","7步"+uri.toString())
    return uri
}

// 保存到 MediaStore (content:// URI)
private fun saveToMediaStore(
    context: Context,
    image: android.media.Image,
    format: Bitmap.CompressFormat,
    fileName: String
): Uri? {
    val contentResolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 使用应用私有目录 + 隐藏文件夹
            put(MediaStore.MediaColumns.RELATIVE_PATH, 
                "${context.packageName}/.private/Pictures")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    // 获取内容 URI
    val contentUri = when (format) {
        Bitmap.CompressFormat.JPEG, Bitmap.CompressFormat.PNG, Bitmap.CompressFormat.WEBP ->
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        else -> MediaStore.Files.getContentUri("external")
    }

    // 插入新条目
    val uri = contentResolver.insert(contentUri, contentValues) ?: return null

    uri.let {
        context.contentResolver.openOutputStream(it)?.use { outputStream ->
            image.toBitmap()?.compress(format, 90, outputStream)
        }
        // 标记为已完成
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)
        }
        it
    }

    Log.i("ImageUtils","3步"+uri.toString())
    // 标记为已完成
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        contentValues.clear()
        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
        contentResolver.update(uri, contentValues, null, null)
    }
    Log.i("ImageUtils","7步"+uri.toString())
    return uri
}

private fun saveToExternalStorageBitmap(
    context: Context,
    bitmap: Bitmap,
    format: Bitmap.CompressFormat,
    fileName: String
):Uri?
{
    if (!isExternalStorageWritable()) {
        Log.e("ImageUtils", "外部存储不可写")
        return null
    }

    // 获取应用私有目录中的图片文件夹
    val storageDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Android 10+ 使用应用私有目录，不需要额外权限
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    } else {
        // Android 9 及以下可以使用公共存储目录
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            context.packageName)
            .apply { mkdirs() }
    }

    if (storageDir == null || !storageDir.exists()) {
        Log.e("ImageUtils", "存储目录不存在或无法创建")
        return null
    }

    val file = File(storageDir, fileName)

    // 将 Image 写入文件
    FileOutputStream(file).use { outputStream ->
        bitmap.compress(format, 90, outputStream)
    }

    return Uri.fromFile(file)
}

// 扩展函数：Image 转 Bitmap (前面已实现)
private fun android.media.Image.toBitmap(): Bitmap? {
    // 这里可以使用前面提供的 Image 转 Bitmap 函数
    // 为简化示例，此处使用简化版
    if (format == ImageFormat.JPEG) {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    // 其他格式处理...
    return null
}

// 扩展属性：获取格式的文件扩展名
private val Bitmap.CompressFormat.extension: String
    get() = when (this) {
        Bitmap.CompressFormat.JPEG -> "jpg"
        Bitmap.CompressFormat.PNG -> "png"
        Bitmap.CompressFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "webp" else "jpg"
        Bitmap.CompressFormat.WEBP_LOSSY -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "webp" else "jpg"
        Bitmap.CompressFormat.WEBP_LOSSLESS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "webp" else "jpg"
    }

// 扩展属性：获取格式的 MIME 类型
private val Bitmap.CompressFormat.mimeType: String
    get() = when (this) {
        Bitmap.CompressFormat.JPEG -> "image/jpeg"
        Bitmap.CompressFormat.PNG -> "image/png"
        Bitmap.CompressFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "image/webp" else "image/jpeg"
        Bitmap.CompressFormat.WEBP_LOSSY -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "image/webp" else "image/jpeg"
        Bitmap.CompressFormat.WEBP_LOSSLESS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "image/webp" else "image/jpeg"
    }