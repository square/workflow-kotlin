package com.squareup.benchmarks.performance.complex.poetry.views

import android.app.Dialog
import android.content.Context
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ProgressBar
import com.squareup.workflow1.ui.AndroidOverlay
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.OverlayDialogFactory
import kotlin.reflect.KClass

@OptIn(WorkflowUiExperimentalApi::class)
object LoaderSpinner : AndroidOverlay<LoaderSpinner> {
  override val dialogFactory: OverlayDialogFactory<LoaderSpinner>
    get() = object : OverlayDialogFactory<LoaderSpinner> {
      override val type = LoaderSpinner::class

      override fun buildDialog(
        initialRendering: LoaderSpinner,
        initialEnvironment: ViewEnvironment,
        context: Context
      ): Dialog = Dialog(context).apply {
        setContentView(
          ProgressBar(context).apply {
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            isIndeterminate = true
          }
        )
      }

      override fun updateDialog(
        dialog: Dialog,
        rendering: LoaderSpinner,
        environment: ViewEnvironment
      ) = Unit
    }
}
