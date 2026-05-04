package com.squareup.workflow1.tracing

/**
 * Interface abstracting tracing functionality to allow for testing with fake implementations.
 */
public interface TraceInterface {
  public val isTraceable: Boolean
  public val isCurrentlyTracing: Boolean

  public fun beginSection(label: String)
  public fun endSection()
  public fun beginAsyncSection(
    name: String,
    cookie: Int
  )

  public fun endAsyncSection(
    name: String,
    cookie: Int
  )

  public fun logSection(info: String)
}

@Deprecated(
  message = "Renamed to TraceInterface",
  replaceWith = ReplaceWith(
    expression = "TraceInterface",
    imports = arrayOf("com.squareup.workflow1.tracing.TraceInterface")
  )
)
public interface SafeTraceInterface : TraceInterface {
  override val isTraceable: Boolean
  override val isCurrentlyTracing: Boolean

  override fun beginSection(label: String)
  override fun endSection()
  override fun beginAsyncSection(
    name: String,
    cookie: Int
  )

  override fun endAsyncSection(
    name: String,
    cookie: Int
  )

  override fun logSection(info: String)
}
