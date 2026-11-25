package com.youyu.android.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.Canvas as PreviewCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.youyu.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.roundToInt

private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
private fun CropBounds.rect() = Rect(left, top, left + width, top + height)

@Composable
internal fun CropPhoto(uri: Uri, kind: ArtworkKind, model: LedgerViewModel, op: OperationState, close: () -> Unit, saved: (Artwork) -> Unit) {
    val context = LocalContext.current
    val icon = kind == ArtworkKind.ICON
    val aspect = if (icon) 1f else 1.586f
    val noun = if (icon) "图标" else "卡面"
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var failure by remember(uri) { mutableStateOf<String?>(null) }
    var x by remember(uri) { mutableFloatStateOf(.5f) }
    var y by remember(uri) { mutableFloatStateOf(.5f) }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var name by remember(uri) { mutableStateOf("自定义$noun") }
    LaunchedEffect(uri) {
        try {
            bitmap = withContext(Dispatchers.IO) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val scale = minOf(1f, 2048f / maxOf(info.size.width, info.size.height))
                    decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure = "无法读取这张图片，请重新选择。" }
    }
    val source = bitmap
    val save: (() -> Unit)? = if (source == null) null else ({
        val art = Artwork(newId(), kind, name.trim())
        val crop = ImageCrop.bounds(source.width, source.height, aspect, zoom, x, y)
        model.perform({ saved(art) }) {
            requireRule(art.name.isNotBlank() && art.name.length <= 40, "名称需为 1 至 40 字")
            val bytes = withContext(Dispatchers.Default) {
                val w = if (icon) 256 else 1080
                val h = (w / aspect).roundToInt()
                val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(output)
                    if (!icon) canvas.drawColor(android.graphics.Color.WHITE)
                    canvas.drawBitmap(source, crop.rect(), Rect(0, 0, w, h), imagePaint)
                    ByteArrayOutputStream().use { stream ->
                        requireRule(output.compress(if (icon) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 88, stream), "图片保存失败")
                        stream.toByteArray()
                    }
                } finally { output.recycle() }
            }
            model.repository.saveArtwork(art, bytes)
        }
    })
    FormPage("裁剪$noun", op.busy, failure ?: op.error, close, save) {
        if (source == null && failure == null) CircularProgressIndicator()
        if (source != null) {
            PreviewCanvas(Modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(12.dp)).background(Color.DarkGray)
                .pointerInput(source, aspect) {
                    detectTransformGestures { _, pan, scale, _ ->
                        val crop = ImageCrop.bounds(source.width, source.height, aspect, zoom, x, y)
                        zoom = (zoom * scale).coerceIn(1f, 5f)
                        if (source.width > crop.width) x = (x - pan.x * crop.width / size.width / (source.width - crop.width)).coerceIn(0f, 1f)
                        if (source.height > crop.height) y = (y - pan.y * crop.height / size.height / (source.height - crop.height)).coerceIn(0f, 1f)
                    }
                }) {
                val region = ImageCrop.bounds(source.width, source.height, aspect, zoom, x, y)
                drawContext.canvas.nativeCanvas.drawBitmap(source, region.rect(), RectF(0f, 0f, size.width, size.height), imagePaint)
            }
            Text("拖动调整位置，双指或滑块缩放。", style = MaterialTheme.typography.bodySmall)
            Slider(zoom, { zoom = it }, valueRange = 1f..5f)
            TextButton({ x = .5f; y = .5f; zoom = 1f }) { Text("重置裁剪") }
            Field("名称", name, { name = it })
        }
    }
}
