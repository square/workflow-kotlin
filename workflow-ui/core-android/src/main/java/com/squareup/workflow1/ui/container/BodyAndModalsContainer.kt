package com.squareup.workflow1.ui.container

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.R
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ScreenViewHolder.Companion.Showing
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub

@WorkflowUiExperimentalApi
internal class BodyAndModalsContainer @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyle: Int = 0,
  defStyleRes: Int = 0
) : FrameLayout(context, attributeSet, defStyle, defStyleRes) {
  /**
   */
  private lateinit var savedStateParentKey: String

  private val baseViewStub: WorkflowViewStub = WorkflowViewStub(context).also {
    addView(it, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
  }

  private val dialogs = LayeredDialogs.forView(
    view = this
  ) { super.dispatchTouchEvent(it) }

  fun update(
    newScreen: BodyAndOverlaysScreen<*, *>,
    viewEnvironment: ViewEnvironment
  ) {
    savedStateParentKey = Compatible.keyFor(viewEnvironment[Showing])

    dialogs.update(newScreen.overlays, viewEnvironment) { env ->
      baseViewStub.show(newScreen.body, env)
    }
  }

  override fun onAttachedToWindow() {
    // I tried to move this to the attachStateChangeListener in LayeredDialogs.Companion.forView,
    // but that fires too late and we crash with the dreaded
    // "You can consumeRestoredStateForKey only after super.onCreate of corresponding component".

    super.onAttachedToWindow()
    // Wire up dialogs to our parent SavedStateRegistry.
    dialogs.onAttachedToWindow(savedStateParentKey, this)
  }

  override fun onDetachedFromWindow() {
    // Disconnect dialogs from our parent SavedStateRegistry so that it doesn't get asked
    // to save state anymore.
    dialogs.onDetachedFromWindow()

    super.onDetachedFromWindow()
  }

  override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    return !dialogs.allowEvents || super.dispatchTouchEvent(event)
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    return !dialogs.allowEvents || super.dispatchKeyEvent(event)
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

  companion object : ScreenViewFactory<BodyAndOverlaysScreen<*, *>>
  by ScreenViewFactory.fromCode(
    buildView = { _, initialEnvironment, context, _ ->
      BodyAndModalsContainer(context)
        .let { view ->
          view.id = R.id.workflow_body_and_modals_container
          view.layoutParams = (LayoutParams(MATCH_PARENT, MATCH_PARENT))

          ScreenViewHolder(initialEnvironment, view) { rendering, environment ->
            view.update(rendering, environment)
          }
        }
    }
  )
}
