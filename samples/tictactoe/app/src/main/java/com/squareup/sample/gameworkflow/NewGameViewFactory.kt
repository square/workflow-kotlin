package com.squareup.sample.gameworkflow

import android.view.LayoutInflater
import android.view.ViewGroup
import com.squareup.sample.tictactoe.databinding.NewGameLayoutBinding
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.ScreenViewUpdater
import com.squareup.workflow1.ui.ScreenViewFactory

@OptIn(WorkflowUiExperimentalApi::class)
internal val NewGameViewFactory: ScreenViewFactory<NewGameScreen> =
  ScreenViewFactory.ofViewBinding<BindingT, ScreenT>({ inflater: LayoutInflater, parent: ViewGroup?, attachToParent: Boolean ->
    NewGameLayoutBinding.inflate(
      inflater,
      parent,
      attachToParent
    )
  }) { binding ->
    ScreenViewUpdater<ScreenT> { rendering, viewEnvironment ->
      if (binding.playerX.text.isBlank()) binding.playerX.setText(rendering.defaultNameX)
      if (binding.playerO.text.isBlank()) binding.playerO.setText(rendering.defaultNameO)
      binding.startGame.setOnClickListener {
        rendering.onStartGame(binding.playerX.text.toString(), binding.playerO.text.toString())
      }
      binding.root.backPressedHandler = { rendering.onCancel() }
    }
  }
