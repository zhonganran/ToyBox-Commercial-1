package com.byd.wjx.ui.settings

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.byd.wjx.R
import com.byd.wjx.led.ipixelfa.IpixelFaPreset

@Composable
internal fun IpixelFaPresetPreview(
    preset: IpixelFaPreset,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val drawable = remember(context, preset) {
        ImageDecoder.decodeDrawable(
            ImageDecoder.createSource(
                context.resources,
                IpixelFaPresetPreviewCatalog.resourceFor(preset),
            ),
        )
    }

    AndroidView(
        factory = { viewContext ->
            ImageView(viewContext).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { imageView ->
            if (imageView.drawable !== drawable) imageView.setImageDrawable(drawable)
            (drawable as? AnimatedImageDrawable)?.apply {
                repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                start()
            }
        },
        modifier = modifier
            .aspectRatio(6f)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black),
    )

    DisposableEffect(drawable) {
        onDispose { (drawable as? AnimatedImageDrawable)?.stop() }
    }
}

internal object IpixelFaPresetPreviewCatalog {
    @DrawableRes
    fun resourceFor(preset: IpixelFaPreset): Int = when (preset) {
        IpixelFaPreset.EMERGENCY -> R.drawable.ipixel_fa_preset_01
        IpixelFaPreset.TURN_OFF_HIGH_BEAM -> R.drawable.ipixel_fa_preset_02
        IpixelFaPreset.TURN_LEFT -> R.drawable.ipixel_fa_preset_03
        IpixelFaPreset.TEMPORARY_STOP -> R.drawable.ipixel_fa_preset_04
        IpixelFaPreset.TURN_RIGHT -> R.drawable.ipixel_fa_preset_05
        IpixelFaPreset.SMILE -> R.drawable.ipixel_fa_preset_06
        IpixelFaPreset.SLOW_DOWN -> R.drawable.ipixel_fa_preset_07
        IpixelFaPreset.KEEP_DISTANCE -> R.drawable.ipixel_fa_preset_08
        IpixelFaPreset.BABY_IN_CAR -> R.drawable.ipixel_fa_preset_09
        IpixelFaPreset.YIELD_TO_PEDESTRIANS -> R.drawable.ipixel_fa_preset_10
        IpixelFaPreset.REVERSING -> R.drawable.ipixel_fa_preset_11
        IpixelFaPreset.PREGNANT_IN_CAR -> R.drawable.ipixel_fa_preset_12
        IpixelFaPreset.DANCE_AT_RED_LIGHT -> R.drawable.ipixel_fa_preset_13
        IpixelFaPreset.AUTOPILOT -> R.drawable.ipixel_fa_preset_14
        IpixelFaPreset.REAR_END_WARNING -> R.drawable.ipixel_fa_preset_15
        IpixelFaPreset.NEW_DRIVER -> R.drawable.ipixel_fa_preset_16
        IpixelFaPreset.NO_HONKING -> R.drawable.ipixel_fa_preset_17
        IpixelFaPreset.SLIPPERY_ROAD -> R.drawable.ipixel_fa_preset_18
        IpixelFaPreset.GET_OFF -> R.drawable.ipixel_fa_preset_19
        IpixelFaPreset.HAZARD_WARNING -> R.drawable.ipixel_fa_preset_20
    }
}
