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
import com.byd.wjx.led.a950.A950Preset

@Composable
internal fun A950PresetPreview(
    preset: A950Preset,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val drawable = remember(context, preset) {
        ImageDecoder.decodeDrawable(
            ImageDecoder.createSource(context.resources, preset.previewResource),
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

private val A950Preset.previewResource: Int
    @DrawableRes get() = when (this) {
        A950Preset.LET_ME_GO_FIRST -> R.drawable.a950_preset_01
        A950Preset.TURN_OFF_HIGH_BEAM -> R.drawable.a950_preset_02
        A950Preset.LEFT_TURN -> R.drawable.a950_preset_03
        A950Preset.RIGHT_TURN -> R.drawable.a950_preset_04
        A950Preset.TEMPORARY_STOP -> R.drawable.a950_preset_05
        A950Preset.SMILE -> R.drawable.a950_preset_06
        A950Preset.SLOW_DOWN -> R.drawable.a950_preset_07
        A950Preset.KEEP_DISTANCE -> R.drawable.a950_preset_08
        A950Preset.BABY_IN_CAR -> R.drawable.a950_preset_09
        A950Preset.YIELD_TO_PEDESTRIANS -> R.drawable.a950_preset_10
        A950Preset.REVERSING -> R.drawable.a950_preset_11
        A950Preset.PREGNANT_IN_CAR -> R.drawable.a950_preset_12
        A950Preset.RELAX_AT_RED_LIGHT -> R.drawable.a950_preset_13
        A950Preset.AUTOPILOT -> R.drawable.a950_preset_14
        A950Preset.REAR_END_WARNING -> R.drawable.a950_preset_15
        A950Preset.NEW_DRIVER -> R.drawable.a950_preset_16
        A950Preset.NO_HONKING -> R.drawable.a950_preset_17
        A950Preset.SLIPPERY_ROAD -> R.drawable.a950_preset_18
        A950Preset.OPEN_DOOR_CAREFULLY -> R.drawable.a950_preset_19
        A950Preset.HAZARD_WARNING -> R.drawable.a950_preset_20
    }
