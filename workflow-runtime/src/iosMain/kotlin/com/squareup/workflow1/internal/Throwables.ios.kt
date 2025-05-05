package com.squareup.workflow1.internal

actual fun <T : Throwable> T.withKey(stackTraceKey: Any): T = this
