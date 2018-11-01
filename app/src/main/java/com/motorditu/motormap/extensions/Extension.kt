package com.motorditu.motormap.extensions

import android.content.Context
import android.graphics.Point
import android.view.WindowManager


fun Context.screenWidth(): Int {
    val manager = this.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val outSize = Point()
    manager.defaultDisplay?.getSize(outSize)
    return outSize.x
}

fun Context.screenHeight(): Int {
    val manager = this.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val outSize = Point()
    manager.defaultDisplay?.getSize(outSize)
    return outSize.y
}

