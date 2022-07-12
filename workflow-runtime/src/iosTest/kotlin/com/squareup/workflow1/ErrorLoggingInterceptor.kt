package com.squareup.workflow1

internal actual open class ErrorLoggingInterceptor : SimpleLoggingWorkflowInterceptor() {
  actual val errors = mutableListOf<String>()

  actual override fun log(text: String) {
    throw IllegalArgumentException()
  }

  actual override fun logError(text: String) {
    errors += text
  }

  actual companion object {
    actual val EXPECTED_ERRORS = listOf(
      "ErrorLoggingInterceptor.logBeforeMethod threw exception:\n" +
        IllegalArgumentException::class.qualifiedName.toString(),
      "ErrorLoggingInterceptor.logAfterMethod threw exception:\n" +
        IllegalArgumentException::class.qualifiedName.toString()
    )
  }
}
