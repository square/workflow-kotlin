package com.squareup.workflow1

internal actual fun SimpleLoggingWorkflowInterceptor.logErrorDelegate(text: String): Unit =
  System.err.println(text)
