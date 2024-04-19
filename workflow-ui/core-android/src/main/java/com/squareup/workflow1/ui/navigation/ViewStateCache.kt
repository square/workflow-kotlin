package com.squareup.workflow1.ui.navigation

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.Log
import android.util.SparseArray
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PRIVATE
import androidx.savedstate.SavedStateRegistryOwner
import com.squareup.workflow1.ui.Compatible.Companion.keyFor
import com.squareup.workflow1.ui.NamedScreen
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.androidx.WorkflowSavedStateRegistryAggregator
import com.squareup.workflow1.ui.showing

/**
 * Handles persistence chores for container views that manage a set of [NamedScreen] renderings,
 * showing a view for one at a time -- think back stacks or tab sets.
 *
 * - Provides [Parcelable]-based [save] and [restore] methods for use from a
 *   container's [View.onSaveInstanceState] and [View.onRestoreInstanceState] methods.
 * - Also handles androidx [SavedStateRegistryOwner] duties, via
 *   a wrapped instance of [WorkflowSavedStateRegistryAggregator]. This means that container
 *   views using this class must call [attachToParentRegistryOwner] and
 *   [detachFromParentRegistry] when they are [attached][View.onAttachedToWindow] and
 *   [detached][View.onDetachedFromWindow], respectively.
 */
@WorkflowUiExperimentalApi
public class ViewStateCache
@VisibleForTesting(otherwise = PRIVATE)
internal constructor(
  @get:VisibleForTesting(otherwise = PRIVATE)
  internal val viewStates: MutableMap<String, ViewStateFrame>
) {
  public constructor() : this(mutableMapOf())

  private val stateRegistryAggregator = WorkflowSavedStateRegistryAggregator()

  /**
   * To be called when the set of hidden views changes but the visible view remains
   * the same. Any cached view state held for renderings that are not
   * [compatible][com.squareup.workflow1.ui.compatible] those in [retaining] will be dropped.
   */
  public fun prune(retaining: Collection<NamedScreen<*>>) {
    pruneAllKeysExcept(retaining.map { it.compatibilityKey })
  }

  private fun pruneAllKeysExcept(retaining: Collection<String>) {
    val deadKeys = viewStates.keys - retaining
    viewStates -= deadKeys
    stateRegistryAggregator.pruneAllChildRegistryOwnersExcept(retaining)
  }

  /**
   * @param retainedRenderings the renderings to be considered hidden after this update. Any
   * associated view state will be retained in the cache, possibly to be restored to the view
   * of [newHolder] on a succeeding call to his method. Any other cached view state will be dropped.
   *
   * @param oldHolderMaybe the view that is being removed, if any, which is expected to be showing
   * a [NamedScreen] rendering. If that rendering is
   * [compatible with][com.squareup.workflow1.ui.compatible] a member of
   * [retainedRenderings], its state will be [saved][View.saveHierarchyState].
   *
   * @param newHolder the view that is about to be displayed, which must be showing a
   * [NamedScreen] rendering. If [compatible][com.squareup.workflow1.ui.compatible]
   * view state is found in the cache, it is [restored][View.restoreHierarchyState].
   *
   * @return true if [newHolder] has been restored.
   */
  public fun update(
    retainedRenderings: Collection<NamedScreen<*>>,
    oldHolderMaybe: ScreenViewHolder<NamedScreen<*>>?,
    newHolder: ScreenViewHolder<NamedScreen<*>>
  ) {
    val newKey = keyFor(newHolder.showing)
    val hiddenKeys = retainedRenderings.asSequence()
      .map { it.compatibilityKey }
      .toSet()
      .apply {
        require(retainedRenderings.size == size) {
          "Duplicate entries not allowed in $retainedRenderings."
        }
      }

    // Put the [SavedStateRegistryOwner] in place.
    stateRegistryAggregator.installChildRegistryOwnerOn(newHolder.view, newKey)

    viewStates.remove(newKey)
      ?.let {
        try {
          newHolder.view.restoreHierarchyState(it.viewState)
        } catch (e: Exception) {
          Log.w("Workflow", "ViewStateCache failed to restore view state for $newKey", e)
        }
      }

    // Save both the view state and state registry of the view that's going away, as long as it's
    // still in the backstack.
    if (oldHolderMaybe != null) {
      keyFor(oldHolderMaybe.showing).takeIf { hiddenKeys.contains(it) }
        ?.let { savedKey ->
          // View state
          val saved = SparseArray<Parcelable>().apply {
            oldHolderMaybe.view.saveHierarchyState(this)
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
          if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            source.readMap(
              this as MutableMap<Any?, Any?>,
              ViewStateCache::class.java.classLoader,
              Any::class.java,
              Any::class.java
            )
          } else {
            @Suppress("DEPRECATION")
            source.readMap(
              this as MutableMap<Any?, Any?>,
              ViewStateCache::class.java.classLoader
            )
          }
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
