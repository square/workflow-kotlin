package com.squareup.workflow1

internal expect fun SimpleLoggingWorkflowInterceptor.logErrorDelegate(text: String): Unit
