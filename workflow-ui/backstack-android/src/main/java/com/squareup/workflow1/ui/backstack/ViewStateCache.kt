package com.squareup.workflow1.ui.backstack

import android.os.Bundle
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.PRIVATE
import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.ViewStateFrame
import com.squareup.workflow1.ui.WorkflowAndroidXSupport.SavedStateRegistryClient
import com.squareup.workflow1.ui.WorkflowAndroidXSupport.compositeViewIdKey
import com.squareup.workflow1.ui.WorkflowAndroidXSupport.createSavedStateRegistryClient
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.getRendering

/**
 * Handles persistence chores for container views that manage a set of [Named] renderings,
 * showing a view for one at a time -- think back stacks or tab sets.
 */
@WorkflowUiExperimentalApi
public class ViewStateCache
@VisibleForTesting(otherwise = PRIVATE)
internal constructor(
  @VisibleForTesting(otherwise = PRIVATE)
  internal val hiddenViewStates: MutableMap<String, ViewStateFrame>
) {
  public constructor() : this(mutableMapOf())

  /**
   * The [ViewStateFrame] that holds the state for the last screen shown by [update]. We need to
   * have a reference to this frame because it needs to have its SavedStateRegistry saved whenever
   * [saveToBundle] is called.
   */
  private var currentFrame: ViewStateFrame? = null

  private var isInstalled = false
  private var isRestored = false

  /**
   * To be called when the set of hidden views changes but the visible view remains
   * the same. Any cached view state held for renderings that are not
   * [compatible][com.squareup.workflow1.ui.compatible] those in [retaining] will be dropped.
   */
  public fun prune(retaining: Collection<Named<*>>) {
    require(isInstalled)
    pruneKeys(retaining.map { it.compatibilityKey })
  }

  private fun pruneKeys(retaining: Collection<String>) {
    val deadKeys = hiddenViewStates.keys - retaining
    hiddenViewStates -= deadKeys
  }

  /**
   * Registers this [ViewStateCache] to start listening for attach/detach and lifecycle events on
   * [view] in order to dispatch the appropriate save/restore calls.
   *
   * This must be called by backstack container views exactly once, and must be called before
   * [update].
   */
  public fun installOnContainer(view: View) {
    check(!isInstalled) { "Expected installOnContainer to only be called once." }
    isInstalled = true

    val registryClient = createSavedStateRegistryClient(
      object : SavedStateRegistryClient {
        override val savedStateRegistryKey: String
          get() {
            require(view.id != View.NO_ID) {
              "Expected BackStackContainer to have an ID set for view state restoration."
            }
            val compositeIdKey = compositeViewIdKey(view)
            return "$compositeIdKey/ViewStateCache"
          }

        override fun onSaveToRegistry(bundle: Bundle) {
          saveToBundle(bundle)
        }

        override fun onRestoreFromRegistry(bundle: Bundle?) {
          restoreFromBundle(bundle)
        }
      }
    )
    view.addOnAttachStateChangeListener(registryClient)
    if (view.isAttachedToWindow) {
      // If we're already attached the onAttached callback won't be invoked, so we call it manually.
      registryClient.onViewAttachedToWindow(view)
    }
  }

  /**
   * @param retainedHiddenRenderings the renderings to be considered hidden after this update. Any
   * associated view state will be retained in the cache, possibly to be restored to [newView]
   * on a succeeding call to his method. Any other cached view state will be dropped.
   *
   * @param oldViewMaybe the view that is being removed, if any, which is expected to be showing
   * a [Named] rendering. If that rendering is
   * [compatible with][com.squareup.workflow1.ui.compatible] a member of
   * [retainedHiddenRenderings], its state will be [saved][View.saveHierarchyState].
   *
   * @param newView the view that is about to be displayed, which must be showing a
   * [Named] rendering. If [compatible][com.squareup.workflow1.ui.compatible]
   * view state is found in the cache, it is [restored][View.restoreHierarchyState].
   *
   * @return true if [newView] has been restored.
   */
  public fun update(
    retainedHiddenRenderings: Collection<Named<*>>,
    oldViewMaybe: View?,
    newView: View
  ) {
    require(isInstalled)

    val oldFrame = currentFrame
    val newKey = newView.namedKey
    val retainedKeys = retainedHiddenRenderings.mapTo(mutableSetOf()) { it.compatibilityKey }
    require(retainedHiddenRenderings.size == retainedKeys.size) {
      "Duplicate entries not allowed in $retainedHiddenRenderings."
    }
    require(newKey !in retainedKeys) {
      "Expected retainedHiddenRenderings to not include the new rendering."
    }

    val restoredFrame = hiddenViewStates.remove(newKey)
    val isPopping = restoredFrame != null
    currentFrame = (restoredFrame ?: ViewStateFrame(newKey)).apply {
      attach(newView)

      if (isRestored || isPopping) {
        // If we haven't been restored yet, and we're not popping, then this is the first view this
        // ViewStateCache has seen. Otherwise, we're expecting a call to restoreFromBundle which
        // will take care of invoking this for us. If we're popping then we are not coming up from a
        // config change or anything.
        restoreAndroidXStateRegistry()
      }

      if (isPopping) {
        // We're navigating back to an old screen, so we need to restore the view hierarchy
        // manually. If we're not popping, then the Android framework will restore the view
        // hierarchy itself.
        restoreViewHierarchyState()
      }
    }

    if (oldViewMaybe != null) {
      // The old view should have been shown by a previous call to update, which should have also
      // set currentFrame, which means oldFrame should never be null.
      requireNotNull(oldFrame) {
        "Expected oldViewMaybe to have been previously passed to update."
      }
      val oldKey = oldViewMaybe.namedKey
      require(oldKey !in hiddenViewStates) {
        "Something's wrong – the old key is already hidden."
      }

      if (oldKey in retainedKeys) {
        // Old view may be returned to later, so we need to save its state.
        // Note that this must be done before destroying the lifecycle.
        oldFrame.performSave(saveViewHierarchyState = true)
      }

      // Don't destroy the lifecycle right away, wait until the view is detached (e.g. after the
      // transition has finished).
      oldFrame.destroyOnDetach()
      hiddenViewStates[oldKey] = oldFrame
    }

    pruneKeys(retainedKeys)
  }

  /**
   * Saves the state registry for the current frame, and all the saved state for hidden frames,
   * to a bundle, and returns it. The result can be passed to [restoreFromBundle].
   */
  @VisibleForTesting(otherwise = PRIVATE)
  internal fun saveToBundle(bundle: Bundle) {
    currentFrame?.let {
      // First ask the current SavedStateRegistry to save its state providers.
      // We don't need to save the view hierarchy here because the current view will already have
      // its state saved by the regular view tree traversal.
      it.performSave(saveViewHierarchyState = false)
      bundle.putParcelable(it.key, it)
    }

    hiddenViewStates.forEach { (key, frame) ->
      bundle.putParcelable(key, frame)
    }
  }

  /**
   * Given a bundle returned from [saveToBundle], restores hidden frames' states, and, if [update]
   * has been called, loads the current frame's state registry data and restores it to the actual
   * registry. If [update] has not been called, the registry data is still loaded, but will be
   * sent to the actual registry when it's created by [update].
   *
   * This method gets called as soon as the lifecycle has moved to the CREATED state. This is
   * necessary because the child state registries must be restored before they see the CREATED state
   * in order to fulfill the SavedStateRegistry contract.
   */
  @VisibleForTesting(otherwise = PRIVATE)
  internal fun restoreFromBundle(bundle: Bundle?) {
    require(!isRestored)
    isRestored = true

    hiddenViewStates.clear()

    bundle?.keySet()?.forEach { key ->
      val frame = bundle.getParcelable<ViewStateFrame>(key)!!
      if (key == currentFrame?.key) {
        // This just passes the data to the frame, it doesn't actually tell the state registry to
        // restore itself from the data.
        currentFrame!!.loadAndroidXStateRegistryFrom(frame)
      } else {
        hiddenViewStates[key] = frame
      }
    }

    // If currentFrame is null here for some reason, that's fine – the next call to update will see
    // that the isRestored flag is set and restore the new frame itself.
    // Note that this needs to be called even if the current frame wasn't restored from the bundle,
    // since it must have been called if we ever ask this frame to performSave in the future.
    currentFrame?.restoreAndroidXStateRegistry()
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
