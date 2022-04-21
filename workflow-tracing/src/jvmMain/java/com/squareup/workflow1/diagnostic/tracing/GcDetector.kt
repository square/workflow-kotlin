package com.squareup.workflow1.diagnostic.tracing

internal typealias GcDetectorConstructor = (onGcDetected: () -> Unit) -> GcDetector

/**
 * Class that does rough logging of garbage collection runs by allocating an unowned object that
 * logs a trace event when its finalizer is ran.
 *
 * Internal and open for testing.
 */
internal open class GcDetector(private val onGcDetected: () -> Unit) {

  @Volatile private var running = true

  private inner class GcCanary {
    @Throws(Throwable::class) protected fun finalize() {
      if (!running) return

      onGcDetected()
      GcCanary()
    }
  }

  init {
    GcCanary()
  }

  fun stop() {
    running = false
  }
}
