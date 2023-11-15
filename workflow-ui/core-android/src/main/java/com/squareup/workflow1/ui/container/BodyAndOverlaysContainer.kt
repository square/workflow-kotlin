package com.squareup.workflow1.ui.container

import android.content.Context
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.R
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.screen

/**
 * Default container for [Overlay] renderings, providing support for
 * orthogonal subtypes [ModalOverlay] and [ScreenOverlay]. As much
 * work as possible is delegated to the public [LayeredDialogSessions]
 * support class, to make it practical to write custom forks should
 * the need arise.
 */
@WorkflowUiExperimentalApi
internal class BodyAndOverlaysContainer @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyle: Int = 0,
  defStyleRes: Int = 0
) : FrameLayout(context, attributeSet, defStyle, defStyleRes) {
  /**
   * The unique `SavedStateRegistry` key passed to [LayeredDialogSessions.onAttachedToWindow],
   * derived from the first rendering passed to [update]. See the doc on
   * [LayeredDialogSessions.onAttachedToWindow] for details.
   */
  private lateinit var savedStateParentKey: String

  private val baseViewStub: WorkflowViewStub = WorkflowViewStub(context).apply {
    propagatesLayoutParams = false
    addView(this)
  }

  private val dialogs = LayeredDialogSessions.forView(
    view = this,
    superDispatchTouchEvent = { super.dispatchTouchEvent(it) }
  )

  fun update(
    newScreen: BodyAndOverlaysScreen<*, *>,
    viewEnvironment: ViewEnvironment
  ) {
    savedStateParentKey = Compatible.keyFor(screen)

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
    return !dialogs.allowBodyEvents || super.dispatchTouchEvent(event)
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    return !dialogs.allowBodyEvents || super.dispatchKeyEvent(event)
  }

  override fun onSaveInstanceState(): Parcelable {
    return SavedState(
      superState = super.onSaveInstanceState()!!,
      savedDialogSessions = dialogs.onSaveInstanceState()
    )
  }

  override fun onRestoreInstanceState(state: Parcelable) {
    (state as? SavedState)
      ?.let {
        dialogs.onRestoreInstanceState(state.savedDialogSessions)
        super.onRestoreInstanceState(state.superState)
      }
      ?: super.onRestoreInstanceState(super.onSaveInstanceState())
    // Some other class wrote state, but we're not allowed to skip
    // the call to super. Make a no-op call.
  }

  private class SavedState : BaseSavedState {
    constructor(
      superState: Parcelable,
      savedDialogSessions: LayeredDialogSessions.SavedState
    ) : super(superState) {
      this.savedDialogSessions = savedDialogSessions
    }

    constructor(source: Parcel) : super(source) {
      savedDialogSessions = if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
        source.readParcelable(
          LayeredDialogSessions.SavedState::class.java.classLoader,
          LayeredDialogSessions.SavedState::class.java
        )!!
      } else {
        @Suppress("DEPRECATION")
        source.readParcelable(LayeredDialogSessions.SavedState::class.java.classLoader)!!
      }
    }

    val savedDialogSessions: LayeredDialogSessions.SavedState

    override fun writeToParcel(
      out: Parcel,
      flags: Int
    ) {
      super.writeToParcel(out, flags)
      out.writeParcelable(savedDialogSessions, flags)
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
      BodyAndOverlaysContainer(context)
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
