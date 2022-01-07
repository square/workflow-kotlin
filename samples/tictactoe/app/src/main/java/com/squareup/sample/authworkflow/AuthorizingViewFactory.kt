package com.squareup.sample.authworkflow

import android.view.LayoutInflater
import android.view.ViewGroup
import com.squareup.sample.tictactoe.databinding.AuthorizingLayoutBinding
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewUpdater
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
internal val AuthorizingViewFactory: ScreenViewFactory<AuthorizingScreen> =
  ScreenViewFactory.ofViewBinding<BindingT, ScreenT>({ inflater: LayoutInflater, parent: ViewGroup?, attachToParent: Boolean ->
    AuthorizingLayoutBinding.inflate(
      inflater,
      parent,
      attachToParent
    )
  }) { binding ->
    ScreenViewUpdater<ScreenT> { rendering, viewEnvironment ->
      binding.authorizingMessage.text = rendering.message
    }
  }
