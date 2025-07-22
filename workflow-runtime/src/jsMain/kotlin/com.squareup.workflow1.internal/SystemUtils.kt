package com.squareup.workflow1.internal

import kotlin.js.Date

internal actual fun currentTimeMillis(): Long = Date.now().toLong()
