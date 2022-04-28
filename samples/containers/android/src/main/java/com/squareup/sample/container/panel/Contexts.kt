package com.squareup.sample.container.panel

import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.view.WindowManager
import com.squareup.sample.container.R

val Context.isTablet: Boolean get() = resources.getBoolean(R.bool.is_tablet)

val Context.windowManager: WindowManager get() = getSystemService(WINDOW_SERVICE) as WindowManager
