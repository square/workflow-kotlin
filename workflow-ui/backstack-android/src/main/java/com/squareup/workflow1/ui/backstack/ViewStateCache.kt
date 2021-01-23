package com.squareup.workflow1.ui.backstack

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.view.View
import android.view.View.BaseSavedState
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.PRIVATE
import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.ViewStateFrame
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
  private val viewStates: MutableMap<String, ViewStateFrame>
) : Parcelable {
  public constructor() : this(mutableMapOf())

  /**
   * The current frame needs to have its SavedStateRegistry saved whenever the container gets a
   * onSaveInstanceState call.
   */
  private var currentFrame: ViewStateFrame? = null

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

    ensureViewStateFrame(newKey).also { newFrame ->
      newFrame.restoreTo(newView)
      currentFrame = newFrame
    }

    if (oldViewMaybe != null) {
      val oldKey = oldViewMaybe.namedKey
      val oldStateFrame = ensureViewStateFrame(oldKey)

      if (oldKey in hiddenKeys) {
        // Old view may be returned to later, so we need to save its state.
        // Note that this must be done before destroying the lifecycle.
        oldStateFrame.performSave(oldViewMaybe)
      }

      // Don't destroy the lifecycle right away, wait until the view is detached (e.g. after the
      // transition has finished).
      oldStateFrame.destroyOnDetach()
    }

    pruneKeys(hiddenKeys)
  }

  private fun ensureViewStateFrame(key: String) =
    viewStates.getOrPut(key) { ViewStateFrame(key) }

  /**
   * Asks the SavedStateRegistry for the current frame to save.
   */
  public fun saveCurrentFrame() {
    // We don't need to pass a view in here because the current view will already have its state
    // saved by the regular view tree traversal.
    currentFrame?.performSave()
  }

  /**
   * Replaces the state of the receiver with that of [from]. Typical usage is to call this from
   * a container view's [View.onRestoreInstanceState].
   */
  public fun restore(from: ViewStateCache) {
    // TODO When does this happen? Is it early enough for saved state registry restoration?
    viewStates.clear()
    viewStates += from.viewStates
    // TODO Call performRestore on the currentFrame's saved state registry?
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
    parcel.writeMap(viewStates as Map<*, *>)
  }

  public companion object CREATOR : Creator<ViewStateCache> {
    override fun createFromParcel(parcel: Parcel): ViewStateCache {
      return mutableMapOf<String, ViewStateFrame>()
          .apply { parcel.readMap(this as Map<*, *>, ViewStateCache::class.java.classLoader) }
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