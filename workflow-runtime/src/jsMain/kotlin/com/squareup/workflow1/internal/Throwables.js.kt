package com.squareup.workflow1.internal

public actual fun <T : Throwable> T.withKey(stackTraceKey: Any): T = this
