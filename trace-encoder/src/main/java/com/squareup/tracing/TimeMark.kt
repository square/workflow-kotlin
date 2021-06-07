package com.squareup.tracing

/**
 * Interface that represents a time point. Remains bound to the time source it was taken from and
 * allows querying for the duration of time elapsed from that point (see the val [elapsedNow]).
 */
public interface TimeMark {
  /**
   * Returns the amount of time passed from this mark measured with the time source from which this
   * mark was taken.
   *
   * Note that the content of this val can change on subsequent invocations.
   */
  public val elapsedNow: Long
}
