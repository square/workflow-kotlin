package com.squareup.sample.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.squareup.workflow1.Presenter
import com.squareup.workflow1.ViewModelProducer
import com.squareup.workflow1.ViewModelProducerContext
import com.squareup.workflow1.ViewModelRef
import com.squareup.workflow1.ui.compose.ComposableViewModel

@Composable
fun BackStackPresenter(content: @Composable () -> Unit) {
  Presenter(
    viewModelProducer = BackStackProducer,
    content = content
  )
}

private data class BackStackScreen(
  val entries: List<ViewModelRef<*>>
) : ComposableViewModel<BackStackScreen> {
  @Composable
  override fun View(
    viewModel: BackStackScreen,
    modifier: Modifier
  ) {
    TODO()
  }
}

private object BackStackProducer : ViewModelProducer<BackStackScreen> {
  context(_: ViewModelProducerContext)
  override fun produce(children: List<ViewModelRef<*>>): BackStackScreen {
    return BackStackScreen(children)
  }
}
