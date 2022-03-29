package com.squareup.benchmarks.performance.poetry.views

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import com.squareup.benchmarks.performance.poetry.R
import com.squareup.workflow1.ui.BuilderViewFactory
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.modal.ModalViewContainer

@OptIn(WorkflowUiExperimentalApi::class)
class LoaderContainer(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyle: Int = 0,
  defStyleRes: Int = 0
) : ModalViewContainer(context, attributeSet, defStyle, defStyleRes) {

  companion object : ViewFactory<MayBeLoadingScreen> by BuilderViewFactory(
    type = MayBeLoadingScreen::class,
    viewConstructor = { initialRendering, initialEnv, contextForNewView, _ ->
      LoaderContainer(contextForNewView).apply {
        id = R.id.loading_dialog
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )
        bindShowRendering(initialRendering, initialEnv, ::update)
      }
    }
  )
}
