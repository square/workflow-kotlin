package com.squareup.workflow1.ui.backstack.test.fixtures

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.test.fixtures.BackStackContainerLifecycleActivity.TestRendering.LeafRendering
import com.squareup.workflow1.ui.internal.test.AbstractLifecycleTestActivity.LeafView

/**
 * Simple view that has a string [viewState] property that will be saved and restored by the
 * [onSaveInstanceState] and [onRestoreInstanceState] methods.
 */
@OptIn(WorkflowUiExperimentalApi::class)
internal class ViewStateTestView(context: Context) : LeafView<LeafRendering>(context) {

  var viewState: String = ""
  // TODO wtf
  // var viewHooks: ViewHooks? = null

  override fun onSaveInstanceState(): Parcelable {
    val superState = super.onSaveInstanceState()
    // TODO wtf
    // viewHooks?.onSaveInstanceState(this)
    return SavedState(superState, viewState)
  }

  override fun onRestoreInstanceState(state: Parcelable?) {
    (state as? SavedState)?.let {
      viewState = it.viewState
      super.onRestoreInstanceState(state.superState)
    } ?: super.onRestoreInstanceState(state)
    // viewHooks?.onRestoreInstanceState(this)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    // viewHooks?.onAttach(this)
  }

  override fun onDetachedFromWindow() {
    // viewHooks?.onDetach(this)
    super.onDetachedFromWindow()
  }

  private class SavedState : BaseSavedState {
    val viewState: String

    constructor(
      superState: Parcelable?,
      viewState: String
    ) : super(superState) {
      this.viewState = viewState
    }

    constructor(source: Parcel) : super(source) {
      viewState = source.readString()!!
    }

    override fun writeToParcel(out: Parcel, flags: Int) {
      super.writeToParcel(out, flags)
      out.writeString(viewState)
    }

    companion object CREATOR : Creator<SavedState> {
      override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
      override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
    }
  }
}
