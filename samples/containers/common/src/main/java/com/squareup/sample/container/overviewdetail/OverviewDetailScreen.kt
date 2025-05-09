package com.squareup.sample.container.overviewdetail

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.Unwrappable
import com.squareup.workflow1.ui.navigation.BackStackScreen
import com.squareup.workflow1.ui.navigation.plus

/**
 * Rendering type for overview / detail containers, with [BackStackScreen] in both roles.
 *
 * Containers may choose to display both children side by side in a split view, or concatenate them
 * (overview + detail) in a single pane.
 *
 * @param selectDefault optional function that a split view container may call to request
 * that a selection be made to fill a null [detailRendering]. This function _must_ perform
 * an action that leads to an updated rendering with either a non-null [detailRendering],
 * or a null [selectDefault]. **[selectDefault] cannot be a no-op.**
 */
class OverviewDetailScreen<out T : Screen> private constructor(
  val overviewRendering: BackStackScreen<T>,
  val detailRendering: BackStackScreen<T>? = null,
  val selectDefault: (() -> Unit)? = null
) : Screen, Unwrappable {
  constructor(
    overviewRendering: BackStackScreen<T>,
    detailRendering: BackStackScreen<T>
  ) : this(overviewRendering, detailRendering, null)

  /**
   * @param selectDefault optional function that a split view container may call to request
   * that a selection be made to fill a null [detailRendering].
   */
  constructor(
    overviewRendering: BackStackScreen<T>,
    selectDefault: (() -> Unit)? = null
  ) : this(overviewRendering, null, selectDefault)

  operator fun component1(): BackStackScreen<T> = overviewRendering
  operator fun component2(): BackStackScreen<T>? = detailRendering

  /**
   * For nicer logging. See the call to [unwrap][com.squareup.workflow1.ui.unwrap]
   * in the activity.
   */
  override val unwrapped = detailRendering ?: overviewRendering

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as OverviewDetailScreen<*>

    return overviewRendering == other.overviewRendering &&
      detailRendering == other.detailRendering &&
      selectDefault == other.selectDefault
  }

  override fun hashCode(): Int {
    var result = overviewRendering.hashCode()
    result = 31 * result + (detailRendering?.hashCode() ?: 0)
    result = 31 * result + (selectDefault?.hashCode() ?: 0)
    return result
  }

  override fun toString(): String {
    return "OverviewDetailScreen(overviewRendering=$overviewRendering, " +
      "detailRendering=$detailRendering, " +
      "selectDefault=$selectDefault)"
  }
}

/**
 * Returns a new [OverviewDetailScreen] appending the
 * [overviewRendering][OverviewDetailScreen.overviewRendering] and
 * [detailRendering][OverviewDetailScreen.detailRendering] of [other] to those of the receiver.
 * If the new screen's `detailRendering` is `null`, it will have the
 * [selectDefault][OverviewDetailScreen.selectDefault] function of [other].
 */
operator fun <T : Screen> OverviewDetailScreen<T>.plus(
  other: OverviewDetailScreen<T>
): OverviewDetailScreen<T> {
  val newOverview = overviewRendering + other.overviewRendering
  val newDetail = detailRendering
    ?.let { it + other.detailRendering }
    ?: other.detailRendering

  return if (newDetail == null) {
    OverviewDetailScreen(newOverview, other.selectDefault)
  } else {
    OverviewDetailScreen(newOverview, newDetail)
  }
}
