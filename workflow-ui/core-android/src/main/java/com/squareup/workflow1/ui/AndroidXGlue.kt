package com.squareup.workflow1.ui

import android.os.Bundle
import android.os.Parcel
import android.view.View
import android.view.View.OnAttachStateChangeListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.ON_RESUME
import androidx.lifecycle.Lifecycle.Event.ON_STOP
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner

/**
 *
 * @param restored True iff this glue was created by restoring from a parcel.
 */
// TODO make this a proper Parcelable
public class AndroidXGlue(
  private val compatibilityId: String,
  public val restored: Boolean
) : SavedStateRegistryOwner, ViewBindingInterceptor {

  private val lifecycle = LifecycleRegistry(this)
  private val savedStateRegistry = SavedStateRegistryController.create(this)

  override fun getLifecycle(): Lifecycle = lifecycle
  override fun getSavedStateRegistry(): SavedStateRegistry = savedStateRegistry.savedStateRegistry

  /**
   * Installs the glue on the first bound view. Add this instance to the [ViewEnvironment] passed
   * to [ViewFactory.buildView] by calling [ViewEnvironment.withViewBindingInterceptor] to install
   * it on the view.
   */
  @OptIn(WorkflowUiExperimentalApi::class)
  override fun initializeView(
    view: View,
    initialRendering: Any,
    initialViewEnvironment: ViewEnvironment,
    proceed: (ViewEnvironment) -> Unit
  ) {
    // This function will be called inside bindShowRendering, after the view is instantiated
    // but before the first showRendering call is made.
    // At this point, the glue was either just restored from a parcel and matches this
    // rendering, or it was created just now for this rendering.
    // This call is what makes the various owners available to the child view. This means
    // lifecycle, saved state, etc, are NOT available until
    ViewTreeLifecycleOwner.set(view, this)
    ViewTreeSavedStateRegistryOwner.set(view, this)

    view.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
      override fun onViewAttachedToWindow(v: View) {
        // Now that all the initialization is done, we need to notify any lifecycle observers
        // that it's started.
        lifecycle.handleLifecycleEvent(ON_RESUME)
      }

      override fun onViewDetachedFromWindow(v: View) {
        lifecycle.handleLifecycleEvent(ON_STOP)
      }
    })

    // Remove ourself as an interceptor so we don't intercept any grandchildren.
    proceed(initialViewEnvironment.withoutViewBindingInterceptor(this))
  }

  public fun matches(rendering: Any): Boolean =
    compatibilityId == compatibilityId(rendering)

  public fun handleLifecycleEvent(event: Lifecycle.Event) {
    lifecycle.handleLifecycleEvent(event)
  }

  public fun writeToParcel(out: Parcel) {
    out.writeString(compatibilityId)
    val bundle = Bundle()
    savedStateRegistry.performSave(bundle)
    out.writeBundle(bundle)
  }

  public companion object {
    public fun forRendering(rendering: Any): AndroidXGlue =
      AndroidXGlue(compatibilityId(rendering), restored = false)

    public fun readFromParcel(source: Parcel): AndroidXGlue? {
      val compatibilityId = source.readString() ?: return null
      val savedStateBundle = source.readBundle(AndroidXGlue::class.java.classLoader) ?: return null
      return AndroidXGlue(compatibilityId, restored = true).also { glue ->
        glue.savedStateRegistry.performRestore(savedStateBundle)
      }
    }

    /**
     * Returns a string that corresponds to [compatible] – any two objects that are [compatible] will
     * return the same key, and any two objects that are not [compatible] will return different keys.
     */
    @OptIn(WorkflowUiExperimentalApi::class)
    private fun compatibilityId(rendering: Any): String = buildString {
      append(rendering::class.java.name)
      if (rendering is Compatible) {
        append(rendering.compatibilityKey)
      }
    }
  }
}
