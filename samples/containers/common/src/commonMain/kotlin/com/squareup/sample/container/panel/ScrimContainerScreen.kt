package com.squareup.sample.container.panel

/**
 * Show a scrim over the [wrapped] item, which is invisible if [dimmed] is false,
 * dark if it is true.
 */
class ScrimContainerScreen<T : Any>(
  val wrapped: T,
  val dimmed: Boolean
)
