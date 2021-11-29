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
import com.squareup.workflow1.ui.ManualScreenViewFactory
import com.squareup.workflow1.ui.R
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.bindShowRendering
import kotlinx.coroutines.flow.MutableStateFlow

@WorkflowUiExperimentalApi
internal class BodyAndModalsContainer @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyle: Int = 0,
  defStyleRes: Int = 0
) : FrameLayout(context, attributeSet, defStyle, defStyleRes) {
  private val baseViewStub: WorkflowViewStub = WorkflowViewStub(context).also {
    addView(it, ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT))
  }

  private val dialogs = LayeredDialogs(view = this, modal = true)

  private val bounds = MutableStateFlow(Bounds())
  private val boundsRect = Rect()
  private val boundsListener = OnGlobalLayoutListener {
    getGlobalVisibleRect(boundsRect)
    bounds.value = boundsRect.toBounds()
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
    baseViewStub.show(newScreen.body, viewEnvironment.withBackStackStateKeyPrefix("[base]"))

     // There is a long wait from when we show a dialog until it starts blocking
     // events for us. To compensate we ignore all touches while any dialogs exist.
    allowEvents = newScreen.modals.isEmpty()
    // Allow modal dialogs to restrict themselves to cover only this view.
    dialogs.update(newScreen.modals, viewEnvironment + (ModalArea to ModalArea(bounds)))
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    boundsListener.onGlobalLayout()
    viewTreeObserver.addOnGlobalLayoutListener(boundsListener)
  }

  override fun onDetachedFromWindow() {
    viewTreeObserver.removeOnGlobalLayoutListener(boundsListener)
    bounds.value = Bounds()
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

  companion object : ScreenViewFactory<BodyAndModalsScreen<*, *>>
  by ManualScreenViewFactory(
    type = BodyAndModalsScreen::class,
    viewConstructor = { initialRendering, initialEnv, context, _ ->
      BodyAndModalsContainer(context)
        .apply {
          id = R.id.workflow_body_and_modals_container
          layoutParams = (LayoutParams(MATCH_PARENT, MATCH_PARENT))
          bindShowRendering(initialRendering, initialEnv, ::update)
        }
    }
  )
}
