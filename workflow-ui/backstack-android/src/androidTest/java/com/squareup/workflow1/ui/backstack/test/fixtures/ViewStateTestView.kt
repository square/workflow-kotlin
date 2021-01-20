package com.squareup.workflow1.ui.backstack.test.fixtures

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.view.View

/**
 * Simple view that has a string [viewState] property that will be saved and restored by the
 * [onSaveInstanceState] and [onRestoreInstanceState] methods.
 */
class ViewStateTestView(context: Context) : View(context) {

  var viewState: String = ""

  override fun onSaveInstanceState(): Parcelable {
    return SavedState(super.onSaveInstanceState(), viewState)
  }

  override fun onRestoreInstanceState(state: Parcelable) {
    (state as? SavedState)?.let {
      viewState = it.viewState
      super.onRestoreInstanceState(state.superState)
    } ?: super.onRestoreInstanceState(state)
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
