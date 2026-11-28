// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.droidance.ui.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color

/**
 * Bitmap cho con trỏ bàn tay robot (`robot_hand_pointer.png`). Kế thừa cách nạp tài nguyên nền đen của bàn tay nhỏ cũ,
 * chuyển nền gần như đen tuyền thành trong suốt,
 * rồi thu phóng theo kích thước đích, kết quả giải mã được đệm theo kích thước.
 *
 * Màn hình chính do sidecar ghép bitmap, lớp xem trước màn hình ảo do View vẽ trực tiếp, hai nơi dùng chung một ảnh ở đây nên trông giống nhau.
 */
internal object HandCursorBitmap {
    const val ASSET = "robot_hand_pointer.png"
    private const val BACKGROUND_KEY_THRESHOLD = 18

    private val cache = HashMap<Int, Bitmap>()

    /** Trả về bitmap bàn tay nhỏ [sizePx] vuông; cùng kích thước chỉ giải mã một lần. */
    @Synchronized
    fun of(context: Context, sizePx: Int): Bitmap {
        val size = sizePx.coerceAtLeast(1)
        cache[size]?.takeIf { !it.isRecycled }?.let { return it }
        val rendered = render(context, size)
        cache[size] = rendered
        return rendered
    }

    private fun render(context: Context, size: Int): Bitmap {
        val original = context.assets.open(ASSET).use(BitmapFactory::decodeStream)
            ?: error("$ASSET decode failed")
        val pixels = IntArray(original.width * original.height)
        original.getPixels(pixels, 0, original.width, 0, 0, original.width, original.height)
        pixels.indices.forEach { index ->
            val color = pixels[index]
            if (Color.red(color) < BACKGROUND_KEY_THRESHOLD &&
                Color.green(color) < BACKGROUND_KEY_THRESHOLD &&
                Color.blue(color) < BACKGROUND_KEY_THRESHOLD
            ) {
                pixels[index] = Color.TRANSPARENT
            }
        }
        val transparent = Bitmap.createBitmap(
            original.width,
            original.height,
            Bitmap.Config.ARGB_8888
        ).apply {
            setPixels(pixels, 0, original.width, 0, 0, original.width, original.height)
        }
        original.recycle()
        val scaled = Bitmap.createScaledBitmap(transparent, size, size, true)
        if (scaled != transparent) transparent.recycle()
        return scaled
    }
}
