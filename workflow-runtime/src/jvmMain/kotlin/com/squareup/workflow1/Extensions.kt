package com.squareup.workflow1

actual fun SimpleLoggingWorkflowInterceptor.logErrorDelegate(text: String): Unit =
  System.err.println(text)
