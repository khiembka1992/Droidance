// SPDX-FileCopyrightText: 2026 YBBLab
// SPDX-License-Identifier: Apache-2.0

package com.pixelvisionlab.pvautoagent.ui.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color

/**
 * Bitmap asset for the robot hand pointer (`robot_hand_pointer.png`). It follows the original hand asset's loading approach with a black background,
 * turning the near-pure-black background into transparency,
 * then scales to the target size; decode results are cached per size.
 *
 * The main display composited by the sidecar and the virtual display preview layer drawn directly by the View share this same image here, so they look identical.
 */
internal object HandCursorBitmap {
    const val ASSET = "robot_hand_pointer.png"
    private const val BACKGROUND_KEY_THRESHOLD = 18

    private val cache = HashMap<Int, Bitmap>()

    /** Returns the hand bitmap that is [sizePx] square; each size is decoded only once. */
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
