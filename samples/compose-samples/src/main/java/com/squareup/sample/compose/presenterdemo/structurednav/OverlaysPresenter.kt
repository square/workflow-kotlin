package com.squareup.sample.compose.presenterdemo.structurednav

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastForEach
import com.squareup.workflow1.presenter.NavigationPresenter
import com.squareup.workflow1.presenter.PresenterModifier
import com.squareup.workflow1.presenter.PresenterSlot
import com.squareup.workflow1.presenter.ViewModelRef
import com.squareup.workflow1.presenter.defaultViewModel
import com.squareup.workflow1.presenter.mapDefaultSlotTo
import com.squareup.workflow1.presenter.read
import com.squareup.workflow1.presenter.resolve
import com.squareup.workflow1.presenter.ui.ViewModel
import com.squareup.workflow1.ui.compose.ComposeScreen

data class OverlayViewModel(
  val content: ViewModelRef<*>,
  val onDismissRequest: (() -> Unit)? = null,
)

fun PresenterModifier.overlay(
  slot: PresenterSlot<OverlayViewModel>,
  onDismissRequest: (() -> Unit)? = null
): PresenterModifier = mapDefaultSlotTo(slot) {
  OverlayViewModel(
    content = it,
    onDismissRequest = onDismissRequest
  )
}

@Composable
fun OverlaysPresenter(
  vararg overlaySlots: PresenterSlot<OverlayViewModel>,
  content: @Composable () -> Unit
) {
  NavigationPresenter(
    content = content,
    presenterPolicy = { children ->
      var bodyViewModel: ViewModelRef<*>? = null
      val overlayViewModels = Array<ViewModelRef<OverlayViewModel>?>(overlaySlots.size) { null }

      // For each slot, take the last child that has a view model in that slot.
      children.fastForEach { child ->
        child.read()?.let { bodyViewModel = it }
        overlaySlots.forEachIndexed { i, slot ->
          if (slot in child) {
            overlayViewModels[i] = child.read(slot)
          }
        }

        // Forward any other slots, with later children taking precedence.
        child.readAllSlotsTo(outputSlots)
      }

      defaultViewModel = OverlaysViewModel(
        body = bodyViewModel!!,
        overlays = overlayViewModels.filterNotNull()
      )
    },
  )
}

data class OverlaysViewModel(
  val body: ViewModelRef<*>,
  val overlays: List<ViewModelRef<OverlayViewModel>>,
) : ComposeScreen {
  @Composable
  override fun Content() {
    Box(contentAlignment = Alignment.Center) {
      ViewModel(body, Modifier.fillMaxSize())

      overlays.forEach { overlay ->
        key(overlay) {
          // Shim
          val overlayModel = overlay.resolve()
          Box(
            Modifier
              .matchParentSize()
              .background(Color.Gray.copy(alpha = 0.4f))
              .pointerInput(overlay) {
                detectTapGestures { overlayModel.onDismissRequest?.invoke() }
              }
          )

          // Overlay
          ViewModel(overlayModel.content)
        }
      }
    }
  }
}
