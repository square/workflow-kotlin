package com.squareup.workflow1.ui.backstack

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.SparseArray
import android.view.View
import android.view.View.BaseSavedState
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.PRIVATE
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.WorkflowLifecycleOwner
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.ViewStateCache.SavedState
import com.squareup.workflow1.ui.getRendering

/**
 * Handles persistence chores for container views that manage a set of [Named] renderings,
 * showing a view for one at a time -- think back stacks or tab sets.
 *
 * This class implements [Parcelable] so that it can be preserved from
 * a container view's own [View.saveHierarchyState] method. A simple container can
 * return [SavedState] from that method rather than creating its own persistence class.
 */
@WorkflowUiExperimentalApi
public class ViewStateCache
@VisibleForTesting(otherwise = PRIVATE)
internal constructor(
  @VisibleForTesting(otherwise = PRIVATE)
  internal val viewStates: MutableMap<String, ViewStateFrame>
) : Parcelable {
  public constructor() : this(mutableMapOf())

  /**
   * To be called when the set of hidden views changes but the visible view remains
   * the same. Any cached view state held for renderings that are not
   * [compatible][com.squareup.workflow1.ui.compatible] those in [retaining] will be dropped.
   */
  public fun prune(retaining: Collection<Named<*>>) {
    pruneKeys(retaining.map { it.compatibilityKey })
  }

  private fun pruneKeys(retaining: Collection<String>) {
    val deadKeys = viewStates.keys - retaining
    viewStates -= deadKeys
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

    // Always install a SavedStateRegistryOwner on the view, even if we don't have any data to
    // restore to it yet. If it's not restored from a frame immediately below, it will be restored
    // by the restore() method.
    val lifecycleOwner = WorkflowLifecycleOwner.get(newView)!!
    val stateOwner = BackStackStateRegistryOwner(lifecycleOwner)
    ViewTreeSavedStateRegistryOwner.set(newView, stateOwner)

    viewStates.remove(newKey)
      ?.let { frame ->
        stateOwner.performRestore(frame.stateRegistryBundle)
        newView.restoreHierarchyState(frame.viewState)
      }

    if (oldViewMaybe != null) {
      oldViewMaybe.namedKey.takeIf { hiddenKeys.contains(it) }
        ?.let { savedKey ->
          // View state
          val saved = SparseArray<Parcelable>().apply {
            oldViewMaybe.saveHierarchyState(this)
          }

          // AndroidX SavedStateRegistry state
          val stateBundle = oldViewMaybe.saveStateRegistry()

          viewStates += savedKey to ViewStateFrame(savedKey, saved, stateBundle)
        }
    }

    pruneKeys(hiddenKeys)
  }

  /**
   * Saves the `SavedStateRegistry` of the [currentView] to this cache. This must be called before
   * this cache is asked to parcel itself.
   */
  public fun saveCurrentViewStateRegistry(currentView: View) {
    val stateBundle = currentView.saveStateRegistry()
    currentView.namedKey.let { saveKey ->
      // We don't need to save view state for the _current_ view, the Android framework handles
      // that.
      viewStates += saveKey to ViewStateFrame(saveKey, viewState = null, stateBundle)
    }
  }

  private fun View.saveStateRegistry(): Bundle = Bundle().also { bundle ->
    val stateOwner = ViewTreeSavedStateRegistryOwner.get(this) as BackStackStateRegistryOwner
    stateOwner.performSave(bundle)
  }

  /**
   * Replaces the state of the receiver with that of [from]. Typical usage is to call this from
   * a container view's [View.onRestoreInstanceState].
   */
  public fun restore(
    from: ViewStateCache,
    currentView: View?
  ) {
    viewStates.clear()
    viewStates += from.viewStates

    // If there's a child view being shown, we need to manually restore its SavedStateRegistry.
    if (currentView != null) {
      viewStates.remove(currentView.namedKey)
        ?.let { frame ->
          // The registry owner will have been installed by the update() method.
          val stateOwner =
            ViewTreeSavedStateRegistryOwner.get(currentView) as BackStackStateRegistryOwner
          stateOwner.performRestore(frame.stateRegistryBundle)

          // We don't need to restore view state, the Android framework will take care of that. In
          // fact, the viewState property will be null in this case.
        }
    }
  }

  public fun ensureStateRegistryRestored(view:View) {
    val stateOwner = ViewTreeSavedStateRegistryOwner.get(view) as BackStackStateRegistryOwner
    stateOwner.ensureRestored()
  }

  /**
   * Convenience for use in [View.onSaveInstanceState] and [View.onRestoreInstanceState]
   * methods of container views that have no other state of their own to save.
   *
   * More interesting containers should create their own subclass of [BaseSavedState]
   * rather than trying to extend this one.
   */
  public class SavedState : BaseSavedState {
    public constructor(
      superState: Parcelable?,
      viewStateCache: ViewStateCache
    ) : super(superState) {
      this.viewStateCache = viewStateCache
    }

    public constructor(source: Parcel) : super(source) {
      this.viewStateCache = source.readParcelable(SavedState::class.java.classLoader)!!
    }

    public val viewStateCache: ViewStateCache

    override fun writeToParcel(
      out: Parcel,
      flags: Int
    ) {
      super.writeToParcel(out, flags)
      out.writeParcelable(viewStateCache, flags)
    }

    public companion object CREATOR : Creator<SavedState> {
      override fun createFromParcel(source: Parcel): SavedState =
        SavedState(source)

      override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
    }
  }

// region Parcelable

  override fun describeContents(): Int = 0

  override fun writeToParcel(
    parcel: Parcel,
    flags: Int
  ) {
    @Suppress("UNCHECKED_CAST")
    parcel.writeMap(viewStates as MutableMap<Any?, Any?>)
  }

  public companion object CREATOR : Creator<ViewStateCache> {
    override fun createFromParcel(parcel: Parcel): ViewStateCache {
      @Suppress("UNCHECKED_CAST")
      return mutableMapOf<String, ViewStateFrame>()
        .apply {
          parcel.readMap(
            this as MutableMap<Any?, Any?>,
            ViewStateCache::class.java.classLoader
          )
        }
        .let { ViewStateCache(it) }
    }

    override fun newArray(size: Int): Array<ViewStateCache?> = arrayOfNulls(size)
  }

// endregion
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
