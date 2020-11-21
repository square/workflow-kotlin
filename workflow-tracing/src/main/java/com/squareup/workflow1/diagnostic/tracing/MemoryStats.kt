package com.squareup.workflow1.diagnostic.tracing

/**
 * Reports free/available memory.
 *
 * @see RuntimeMemoryStats
 */
public interface MemoryStats {
  public fun freeMemory(): Long
  public fun totalMemory(): Long
}

/**
 * A [MemoryStats] that reports memory stats using this [Runtime] instance.
 */
public object RuntimeMemoryStats : MemoryStats {
  private val runtime: Runtime = Runtime.getRuntime()
  override fun freeMemory(): Long = runtime.freeMemory()
  override fun totalMemory(): Long = runtime.totalMemory()
}
