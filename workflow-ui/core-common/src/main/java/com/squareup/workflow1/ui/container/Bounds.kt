package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@WorkflowUiExperimentalApi
public class Bounds(
  public val left: Int,
  public val top: Int,
  public val right: Int,
  public val bottom: Int
) {
  public constructor() : this(0, 0, 0, 0)

  public fun copy(
    left: Int = this.left,
    top: Int = this.top,
    right: Int = this.right,
    bottom: Int = this.bottom
  ): Bounds = Bounds(left, top, right, bottom)

  public operator fun component1(): Int = left
  public operator fun component2(): Int = top
  public operator fun component3(): Int = right
  public operator fun component4(): Int = bottom

  public val height: Int get() = bottom - top
  public val width: Int get() = right - left

  override fun equals(other: Any?): Boolean {
    if (this === other) return true

    return (other as? Bounds)?.let { otherBounds ->
      left == otherBounds.left &&
        top == otherBounds.top &&
        right == otherBounds.right &&
        bottom == otherBounds.bottom
    } ?: false
  }

  override fun hashCode(): Int {
    var result = left
    result = 31 * result + top
    result = 31 * result + right
    result = 31 * result + bottom
    return result
  }

  override fun toString(): String {
    return "Bounds(left=$left, top=$top, right=$right, bottom=$bottom)"
  }
}
