package com.squareup.workflow1.ui.container

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_CANCEL
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import com.squareup.workflow1.ui.ManualScreenViewFactory
import com.squareup.workflow1.ui.R
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.bindShowRendering

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

  private val dialogs = LayeredDialogs(this)

  fun update(
    newScreen: BodyAndModalsScreen<*, *>,
    viewEnvironment: ViewEnvironment
  ) {
    baseViewStub.show(newScreen.body, viewEnvironment.withBackStackStateKeyPrefix("[base]"))
    dialogs.update(newScreen.modals, viewEnvironment) {
      // If a new Dialog is being shown, cancel any late breaking events that
      // got queued up on the main thread while we were spinning it up.
      dropLateEvents()
    }
  }

  /**
   * There is a long wait from when we show a dialog until it starts blocking
   * events for us. To compensate we ignore all touches while any dialogs exist.
   */
  override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
    // See also the call to [dropLateEvents] from [update].
    return dialogs.hasDialogs || super.dispatchTouchEvent(ev)
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

  private fun dropLateEvents() {
    // If any motion events were enqueued on the main thread, cancel them.
    val now = SystemClock.uptimeMillis()
    MotionEvent.obtain(now, now, ACTION_CANCEL, 0.0f, 0.0f, 0).also { cancelEvent ->
      super.dispatchTouchEvent(cancelEvent)
      cancelEvent.recycle()
    }

    // View classes like RecyclerView handle streams of motion events
    // and eventually dispatch input events (click, key pressed, etc.)
    // based on them. This call warns them to clean up any such work
    // that might have been in midstream, as opposed to crashing when
    // we post that cancel event.
    cancelPendingInputEvents()
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
