package com.byd.wjx.cluster

import androidx.annotation.StringRes
import com.byd.wjx.R

/**
 * Known steering-wheel keycodes → display name string resource. Display only — it does NOT gate
 * which keys are assignable (that is [isAssignable]). This table mirrors the key profiles seen in
 * validated 23/34-controller vehicle profiles, plus the OpenBYD public keycodes we already used.
 * Unknown codes fall back to R.string.steering_button_unknown with the raw number.
 */
val KNOWN_BUTTON_NAMES: Map<Int, Int> = mapOf(
    80 to R.string.steering_button_left,
    87 to R.string.steering_button_right,
    88 to R.string.steering_button_left,
    289 to R.string.steering_button_mode,
    291 to R.string.steering_button_wheel_up,
    292 to R.string.steering_button_wheel_down,
    294 to R.string.steering_button_surround_view,
    304 to R.string.steering_button_voice,
    305 to R.string.steering_button_left_star,
    307 to R.string.steering_button_wheel_up,
    308 to R.string.steering_button_wheel_down,
    309 to R.string.steering_button_carousel,
    310 to R.string.steering_button_surround_view,
    318 to R.string.steering_button_right_long,
    319 to R.string.steering_button_left_long,
    320 to R.string.steering_button_voice,
    321 to R.string.steering_button_aux_left,
    322 to R.string.steering_button_aux_left_long,
    328 to R.string.steering_button_voice_long,
    351 to R.string.steering_button_right_star,
    352 to R.string.steering_button_right_star_long,
    353 to R.string.steering_button_wheel_center,
    383 to R.string.steering_button_aux_right,
    384 to R.string.steering_button_aux_right_long,
    386 to R.string.steering_button_wheel_center,
)

/** @StringRes name for [keyCode], or 0 if unknown (caller falls back to the "unknown" string). */
@StringRes
fun knownButtonNameRes(keyCode: Int): Int = KNOWN_BUTTON_NAMES[keyCode] ?: 0
