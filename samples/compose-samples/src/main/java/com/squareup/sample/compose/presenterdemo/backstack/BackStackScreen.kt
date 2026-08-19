package com.squareup.sample.compose.presenterdemo.backstack

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import com.squareup.workflow1.presenter.ViewModel
import com.squareup.workflow1.presenter.ui.ViewModel
import com.squareup.workflow1.ui.compose.ComposeScreen

data class BackStackScreen(
  val entries: List<ViewModel>,
  val onBack: (() -> Unit)?,
) : ViewModel, ComposeScreen {

  @Composable
  override fun Content() {
    // Fake implementation.
    AnimatedContent(entries.last()) { viewModel ->
      ViewModel(viewModel)
    }
  }
}
