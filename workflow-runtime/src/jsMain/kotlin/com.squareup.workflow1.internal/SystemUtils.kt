package com.squareup.workflow1.internal

import kotlin.js.Date

actual fun currentTimeMillis(): Long = Date.now().toLong()
