package com.squareup.workflow1.ui.backstack

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.SparseArray
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.PRIVATE
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.androidx.WorkflowSavedStateRegistryAggregator
import com.squareup.workflow1.ui.getRendering

/**
 * Handles persistence chores for container views that manage a set of [Named] renderings,
 * showing a view for one at a time -- think back stacks or tab sets.
 *
 * - Provides [Parcelable]-based [save] and [restore] methods for use from a
 *   container's [View.onSaveInstanceState] and [View.onRestoreInstanceState] methods.
 * - Also handles androidx [ViewTreeSavedStateRegistryOwner] duties, via
 *  a wrapped instance of [WorkflowSavedStateRegistryAggregator]. This means that container
 *  views using this class must call [attachToParentRegistryOwner] and
 *  [detachFromParentRegistry] when they are [attached][View.onAttachedToWindow] and
 *  [detached][View.onDetachedFromWindow], respectively.
 */
@WorkflowUiExperimentalApi
public class ViewStateCache
@VisibleForTesting(otherwise = PRIVATE)
internal constructor(
  @VisibleForTesting(otherwise = PRIVATE)
  internal val viewStates: MutableMap<String, ViewStateFrame>
) {
  public constructor() : this(mutableMapOf())

  private val stateRegistryAggregator = WorkflowSavedStateRegistryAggregator()

  /**
   * To be called when the set of hidden views changes but the visible view remains
   * the same. Any cached view state held for renderings that are not
   * [compatible][com.squareup.workflow1.ui.compatible] those in [retaining] will be dropped.
   */
  public fun prune(retaining: Collection<Named<*>>) {
    pruneAllKeysExcept(retaining.map { it.compatibilityKey })
  }

  private fun pruneAllKeysExcept(retaining: Collection<String>) {
    val deadKeys = viewStates.keys - retaining
    viewStates -= deadKeys
    stateRegistryAggregator.pruneAllChildRegistryOwnersExcept(retaining)
  }

  /**
   * @param retainedRenderings the renderings to be considered hidden after this update. Any
   * associated view state will be retained in the cache, possibly to be restored to [newView]
   * on a succeeding call to his method. Any other cached view state will be dropped.
   *
   * @param oldViewMaybe the view that is being removed, if any, which is expected to be showing
   * a [Named] rendering. If that rendering is
   * [compatible with][com.squareup.workflow1.ui.compatible] a member of
   * [retainedRenderings], its state will be [saved][View.saveHierarchyState].
   *
   * @param newView the view that is about to be displayed, which must be showing a
   * [Named] rendering. If [compatible][com.squareup.workflow1.ui.compatible]
   * view state is found in the cache, it is [restored][View.restoreHierarchyState].
   *
   * @return true if [newView] has been restored.
   */
  public fun update(
    retainedRenderings: Collection<Named<*>>,
    oldViewMaybe: View?,
    newView: View
  ) {
    val newKey = newView.namedKey
    val hiddenKeys = retainedRenderings.asSequence()
      .map { it.compatibilityKey }
      .toSet()
      .apply {
        require(retainedRenderings.size == size) {
          "Duplicate entries not allowed in $retainedRenderings."
        }
      }

    // Put the [ViewTreeSavedStateRegistryOwner] in place.
    stateRegistryAggregator.installChildRegistryOwnerOn(newView, newKey)

    viewStates.remove(newKey)
      ?.let { newView.restoreHierarchyState(it.viewState) }

    // Save both the view state and state registry of the view that's going away, as long as it's
    // still in the backstack.
    if (oldViewMaybe != null) {
      oldViewMaybe.namedKey.takeIf { hiddenKeys.contains(it) }
        ?.let { savedKey ->
          // View state
          val saved = SparseArray<Parcelable>().apply {
            oldViewMaybe.saveHierarchyState(this)
          }
          viewStates += savedKey to ViewStateFrame(savedKey, saved)

          stateRegistryAggregator.saveAndPruneChildRegistryOwner(savedKey)
        }
    }

    pruneAllKeysExcept(retaining = hiddenKeys + newKey)
  }

  /**
   * Must be called whenever the owning view is [attached to a window][View.onAttachedToWindow].
   * Must eventually be matched with a call to [detachFromParentRegistry].
   */
  public fun attachToParentRegistryOwner(
    key: String,
    parentOwner: SavedStateRegistryOwner
  ) {
    stateRegistryAggregator.attachToParentRegistry(key, parentOwner)
  }

  /**
   * Must be called whenever the owning view is [detached from a window][View.onDetachedFromWindow].
   * Must be matched with a call to [attachToParentRegistryOwner].
   */
  public fun detachFromParentRegistry() {
    stateRegistryAggregator.detachFromParentRegistry()
  }

  /**
   * Replaces the state of the receiver with that of [from]. Typical usage is to call this from
   * a container view's [View.onRestoreInstanceState].
   */
  public fun restore(from: Saved) {
    viewStates.clear()
    viewStates += from.viewStates
  }

  /**
   * Returns a [Parcelable] copy of the internal state of the receiver, for use with
   * a container view's [View.onSaveInstanceState].
   */
  public fun save(): Saved {
    return Saved(this)
  }

  public class Saved : Parcelable {
    internal constructor(viewStateCache: ViewStateCache) {
      this.viewStates = viewStateCache.viewStates.toMap()
    }

    public constructor(source: Parcel) {
      this.viewStates = mutableMapOf<String, ViewStateFrame>()
        .apply {
          @Suppress("UNCHECKED_CAST")
          source.readMap(
            this as MutableMap<Any?, Any?>,
            ViewStateCache::class.java.classLoader
          )
        }
        .toMap()
    }

    internal val viewStates: Map<String, ViewStateFrame>

    override fun describeContents(): Int = 0

    override fun writeToParcel(
      out: Parcel,
      flags: Int
    ) {
      out.writeMap(viewStates)
    }

    public companion object CREATOR : Creator<Saved> {
      override fun createFromParcel(source: Parcel): Saved =
        Saved(source)

      override fun newArray(size: Int): Array<Saved?> = arrayOfNulls(size)
    }
  }
}

@WorkflowUiExperimentalApi
private val View.namedKey: String
  get() {
    val rendering = getRendering<Named<*>>()
    return checkNotNull(rendering?.compatibilityKey) {
      "Expected $this to be showing a ${Named::class.java.simpleName}<*> rendering, " +
        "found $rendering"
    }
  }
