package com.squareup.workflow1

internal expect open class ErrorLoggingInterceptor() : SimpleLoggingWorkflowInterceptor {
  val errors: MutableList<String>

  override fun log(text: String)

  override fun logError(text: String)

  companion object {
    val EXPECTED_ERRORS: List<String>
  }
}
