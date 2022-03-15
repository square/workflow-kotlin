package com.squareup.workflow1.ui.container

import android.content.Context
import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.FrameLayout
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Compatible.Companion.keyFor
import com.squareup.workflow1.ui.R
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.androidx.WorkflowAndroidXSupport
import kotlinx.coroutines.flow.MutableStateFlow

@WorkflowUiExperimentalApi
internal class BodyAndModalsContainer @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyle: Int = 0,
  defStyleRes: Int = 0
) : FrameLayout(context, attributeSet, defStyle, defStyleRes) {
  /**
   * Unique identifier for this view for SavedStateRegistry purposes. Based on the
   * [Compatible.keyFor] the current rendering. Taking this approach allows
   * feature developers to take control over naming, e.g. by wrapping renderings
   * with [NamedScreen][com.squareup.workflow1.ui.NamedScreen].
   */
  private lateinit var savedStateParentKey: String

  private val baseViewStub: WorkflowViewStub = WorkflowViewStub(context).also {
    addView(it, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
  }

  private val dialogs = LayeredDialogs(view = this, modal = true)

  // The bounds of this view in global (display) coordinates, as reported
  // by getGlobalVisibleRect.
  //
  // Made available to managed ModalScreenOverlayDialogFactory instances
  // via the ModalArea key in ViewEnvironment. When this updates their
  // updateBounds methods will fire. They should resize themselves to
  // avoid covering peers of this view.
  private val bounds = MutableStateFlow(Rect())
  private val boundsRect = Rect()

  private val boundsListener = OnGlobalLayoutListener {
    if (getGlobalVisibleRect(boundsRect) && boundsRect != bounds.value) {
      bounds.value = Rect(boundsRect)
    }
    // Should we close the dialogs if getGlobalVisibleRect returns false?
    // https://github.com/square/workflow-kotlin/issues/599
  }

  // Note similar code in DialogHolder.
  private var allowEvents = true
    set(value) {
      val was = field
      field = value
      if (value != was) {
        // https://stackoverflow.com/questions/2886407/dealing-with-rapid-tapping-on-buttons
        // If any motion events were enqueued on the main thread, cancel them.
        dispatchCancelEvent { super.dispatchTouchEvent(it) }
        // When we cancel, have to warn things like RecyclerView that handle streams
        // of motion events and eventually dispatch input events (click, key pressed, etc.)
        // based on them.
        cancelPendingInputEvents()
      }
    }

  fun update(
    newScreen: BodyAndModalsScreen<*, *>,
    viewEnvironment: ViewEnvironment
  ) {
    savedStateParentKey = keyFor(viewEnvironment[Screen])

    val showingModals = newScreen.modals.isNotEmpty()

    // There is a long wait from when we show a dialog until it starts blocking
    // events for us. To compensate we ignore all touches while any dialogs exist.
    allowEvents = !showingModals

    val baseEnv = if (showingModals) viewEnvironment + (CoveredByModal to true) else viewEnvironment
    baseViewStub.show(newScreen.body, baseEnv)

    // Allow modal dialogs to restrict themselves to cover only this view.
    val dialogsEnv = if (showingModals) viewEnvironment + ModalArea(bounds) else viewEnvironment

    dialogs.update(newScreen.modals, dialogsEnv)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    boundsListener.onGlobalLayout()
    viewTreeObserver.addOnGlobalLayoutListener(boundsListener)

    // Wire up dialogs to our parent SavedStateRegistry.
    val parentRegistryOwner = WorkflowAndroidXSupport.stateRegistryOwnerFromViewTreeOrContext(this)
    dialogs.attachToParentRegistryOwner(savedStateParentKey, parentRegistryOwner)
  }

  override fun onDetachedFromWindow() {
    // Disconnect dialogs from our parent SavedStateRegistry so that it doesn't get asked
    // to save state anymore.
    dialogs.detachFromParentRegistry()
    // Don't leak the dialogs if we're suddenly yanked out of view.
    // https://github.com/square/workflow-kotlin/issues/314
    dialogs.update(emptyList(), ViewEnvironment.EMPTY)
    viewTreeObserver.removeOnGlobalLayoutListener(boundsListener)
    bounds.value = Rect()
    super.onDetachedFromWindow()
  }

  override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    return !allowEvents || super.dispatchTouchEvent(event)
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    return !allowEvents || super.dispatchKeyEvent(event)
  }

  override fun onSaveInstanceState(): Parcelable {
    return SavedState(
      superState = super.onSaveInstanceState()!!,
      savedDialogs = dialogs.onSaveInstanceState()
    )
  }

  override fun onRestoreInstanceState(state: Parcelable) {
    (state as? SavedState)
      ?.let {
        dialogs.onRestoreInstanceState(state.savedDialogs)
        super.onRestoreInstanceState(state.superState)
      }
      ?: super.onRestoreInstanceState(super.onSaveInstanceState())
    // Some other class wrote state, but we're not allowed to skip
    // the call to super. Make a no-op call.
  }

  private class SavedState : BaseSavedState {
    constructor(
      superState: Parcelable,
      savedDialogs: LayeredDialogs.SavedState
    ) : super(superState) {
      this.savedDialogs = savedDialogs
    }

    constructor(source: Parcel) : super(source) {
      @Suppress("UNCHECKED_CAST")
      savedDialogs = source.readParcelable(SavedState::class.java.classLoader)!!
    }

    val savedDialogs: LayeredDialogs.SavedState

    override fun writeToParcel(
      out: Parcel,
      flags: Int
    ) {
      super.writeToParcel(out, flags)
      out.writeParcelable(savedDialogs, flags)
    }

    companion object CREATOR : Creator<SavedState> {
      override fun createFromParcel(source: Parcel): SavedState =
        SavedState(source)

      override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
    }
  }

  companion object : ScreenViewFactory<BodyAndModalsScreen<*, *>> by ScreenViewFactory(
    buildView = { _, context, _ ->
      BodyAndModalsContainer(context)
        .apply {
          id = R.id.workflow_body_and_modals_container
          layoutParams = (LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
    },
    updateView = { view, rendering, environment ->
      (view as BodyAndModalsContainer).update(rendering, environment)
    }
  )
}
