package dev.pratyush.qrdemo.util

import android.content.Context
import android.util.DisplayMetrics
import android.content.res.Configuration

fun Context.px(densityPixel : Float) = calc(this, densityPixel)

private fun calc(context : Context, dp : Float) : Float{
    return dp * (context.resources.displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT)
}

fun Context.isPortrait() : Boolean {
    val orientation = this.resources.configuration.orientation
    return orientation == Configuration.ORIENTATION_PORTRAIT
}
