package com.youyu.domain

import kotlin.math.roundToInt

data class CropBounds(val left: Int, val top: Int, val width: Int, val height: Int)

object ImageCrop {
    // x/y are the relative positions within the available travel, from 0 (start) to 1 (end).
    fun bounds(width: Int, height: Int, aspect: Float, zoom: Float, x: Float, y: Float): CropBounds {
        require(width > 0 && height > 0 && aspect > 0)
        val w = (minOf(width.toFloat(), height * aspect) / zoom.coerceIn(1f, 5f)).roundToInt().coerceIn(1, width)
        val h = (w / aspect).roundToInt().coerceIn(1, height)
        return CropBounds(((width - w) * x.coerceIn(0f, 1f)).roundToInt(),
            ((height - h) * y.coerceIn(0f, 1f)).roundToInt(), w, h)
    }
}
