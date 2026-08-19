package com.squareup.sample.compose.presenterdemo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import com.squareup.workflow1.presenter.Presenter
import com.squareup.workflow1.presenter.ViewModel
import com.squareup.workflow1.presenter.ViewModelProducer
import com.squareup.workflow1.presenter.ViewModelProducerScope
import com.squareup.workflow1.presenter.ViewModelRef
import com.squareup.workflow1.presenter.simplify
import com.squareup.workflow1.presenter.subcomposePresenter
import com.squareup.workflow1.presenter.ui.ViewModel
import com.squareup.workflow1.ui.compose.ComposeScreen
import kotlinx.coroutines.awaitCancellation

enum class ModalLayer {
  FullScreen,
  Sheet,
  Popup,
}

private val LocalModalManager = staticCompositionLocalOf<ModalManager> { error("No ModalManager") }

fun interface ModalPresenter {
  fun presentModalForLayer(
    layer: ModalLayer,
    modalEntries: List<ViewModelRef>
  ): ViewModel

  companion object {
    val Default: ModalPresenter = { _, children -> children.simplify() }
  }
}

@Composable
fun ModalHostPresenter(
  modalPresenter: ModalPresenter = ModalPresenter.Default,
  content: @Composable () -> Unit
) {
  // This implements both the ViewModel and Producer.
  val modalManager = remember { ModalManager() }
  modalManager.modalPresenter = modalPresenter

  Presenter(modalManager) {
    CompositionLocalProvider(LocalModalManager provides modalManager, content = content)
  }
}

/**
 * Presents [content] as the contents of the [layer] modal inside a [ModalHostPresenter].
 */
@Composable
fun ModalPresenter(
  layer: ModalLayer,
  content: @Composable () -> Unit
) {
  val manager = LocalModalManager.current
  val layerModel = subcomposePresenter(content)
  LaunchedEffect(layer, manager, layerModel) {
    manager.showModal(layer, layerModel)
  }
}

data class ModalHostViewModel(
  val body: ViewModelRef,
  val modals: List<ViewModel>,
) : ViewModel, ComposeScreen {
  @Composable
  override fun Content() {
    Box(
      propagateMinConstraints = true,
      modifier = Modifier.fillMaxSize()
    ) {
      ViewModel(body)
      modals.fastForEach { modal ->
        ViewModel(
          modal,
          modifier = Modifier
            .background(Color.Gray.copy(alpha = 0.5f))
            .wrapContentSize()
        )
      }
    }
  }
}

private class ModalManager : ViewModelProducer<ModalHostViewModel> {

  var modalPresenter by mutableStateOf(ModalPresenter.Default)

  private val modalsByLayer = ModalLayer.entries.fastMap {
    SnapshotStateList<ViewModelRef>()
  }

  suspend fun showModal(
    layer: ModalLayer,
    content: ViewModelRef
  ) {
    val layerList = modalsByLayer[layer.ordinal]
    layerList += content
    try {
      awaitCancellation()
    } finally {
      layerList -= content
    }
  }

  override fun ViewModelProducerScope.produce(children: List<ViewModelRef>): ModalHostViewModel =
    ModalHostViewModel(
      body = children.last(),
      modals = ModalLayer.entries.fastMap { layer ->
        val modalEntries = modalsByLayer[layer.ordinal]
        modalPresenter.presentModalForLayer(layer, modalEntries)
      }
    )
}
