package com.squareup.workflow1.internal

actual fun <T : Throwable> T.withKey(stackTraceKey: Any): T = apply {
  val realTop = stackTrace[0]
  val fakeTop = StackTraceElement(
    // Real class name to ensure that we are still "in project".
    realTop.className,
    "fakeMethodForCrashGrouping",
    /* fileName = */ stackTraceKey.toString(),
    /* lineNumber = */ stackTraceKey.hashCode()
  )
  stackTrace = stackTrace.toMutableList().apply { add(0, fakeTop) }.toTypedArray()
}
