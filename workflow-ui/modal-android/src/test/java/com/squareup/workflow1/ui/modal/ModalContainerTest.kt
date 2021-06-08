package com.squareup.workflow1.ui.modal

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// SDK 28 required for the four-arg constructor we use in our custom view classes.
@Config(manifest = Config.NONE, sdk = [28])
@OptIn(WorkflowUiExperimentalApi::class)
internal class ModalContainerTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @OptIn(WorkflowUiExperimentalApi::class)
  val modalContainer = object : ModalContainer<Any>(context) {
    override fun buildDialog(
      initialModalRendering: Any,
      initialViewEnvironment: ViewEnvironment
    ): DialogRef<Any> = error("unimplemented")

    override fun updateDialog(dialogRef: DialogRef<Any>) = Unit
  }.apply { id = 42 }

  @Test fun ignoresAlienViewState() {
    var saved = false
    val weirdView = object : View(context) {
      override fun onSaveInstanceState(): Parcelable {
        saved = true
        super.onSaveInstanceState()
        return Bundle()
      }
    }.apply { id = 42 }

    val viewState = SparseArray<Parcelable>()
    weirdView.saveHierarchyState(viewState)
    assertThat(saved).isTrue()

    modalContainer.restoreHierarchyState(viewState)
    // No crash, no bug.
  }
}
