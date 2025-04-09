package com.squareup.workflow1.ui.navigation.fixtures

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import com.squareup.workflow1.ui.internal.test.AbstractLifecycleTestActivity.LeafView
import com.squareup.workflow1.ui.navigation.fixtures.BackStackContainerLifecycleActivity.TestRendering.LeafRendering

/**
 * Simple view that has a string [viewState] property that will be saved and restored by the
 * [onSaveInstanceState] and [onRestoreInstanceState] methods.
 */
internal class ViewStateTestView(
  context: Context,
  private val crashOnRestore: Boolean = false
) : LeafView<LeafRendering>(context) {

  var viewState: String = ""

  override fun onSaveInstanceState(): Parcelable {
    val superState = super.onSaveInstanceState()
    return SavedState(superState, viewState)
  }

  override fun onRestoreInstanceState(state: Parcelable?) {
    if (crashOnRestore) throw IllegalArgumentException("Crashing on restore.")
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

    override fun writeToParcel(
      out: Parcel,
      flags: Int
    ) {
      super.writeToParcel(out, flags)
      out.writeString(viewState)
    }

    companion object CREATOR : Creator<SavedState> {
      override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
      override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
    }
  }
}
