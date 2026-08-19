package com.squareup.sample.compose.presenterdemo.structurednav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.util.fastForEach
import com.squareup.workflow1.presenter.ChildPresenter
import com.squareup.workflow1.presenter.NavigationPresenter
import com.squareup.workflow1.presenter.PresenterModifier
import com.squareup.workflow1.presenter.PresenterPolicy
import com.squareup.workflow1.presenter.PresenterPolicyScope
import com.squareup.workflow1.presenter.ViewModelRef
import com.squareup.workflow1.presenter.defaultViewModel
import com.squareup.workflow1.presenter.read

/**
 * Emits a back stack that is managed by what child nodes [content] emits. To "navigate forward",
 * compose an additional presenter after composing previous presenters. If you wish a view model
 * to remain on the stack, you must continue to compose it. To remove a view model from the stack,
 * either at the end or somewhere in the middle, just stop composing it.
 *
 * If any child is a [BodyAndOverlaysViewModel], then this presenter emits a
 * [BodyAndOverlaysViewModel] as well, with all bodies merged into a backstack. If
 * [dropInactiveOverlays] is true (the default), then any overlays from the top-most child are
 * lifted out; otherwise all modals are concatenated into a single list.
 */
@Composable
inline fun BackstackPresenter(
  modifier: PresenterModifier = PresenterModifier,
  content: @Composable BackstackScope.() -> Unit
) {
  val presenter = remember { BackstackPresenterImpl() }
  NavigationPresenter(
    modifier = modifier,
    content = { content(presenter) },
    presenterPolicy = presenter
  )
}

interface BackstackScope {
  /**
   * Assigns a back handler to be called when the user navigates back from the modified presenter.
   * The callback should modify some state and recompose without this presenter.
   */
  fun PresenterModifier.onBackRequested(block: () -> Unit): PresenterModifier
}

@PublishedApi
internal class BackstackPresenterImpl : BackstackScope, PresenterPolicy {

  override fun PresenterModifier.onBackRequested(block: () -> Unit): PresenterModifier {
    TODO("Not yet implemented")
  }

  override fun PresenterPolicyScope.produce(children: List<ChildPresenter>) {
    require(children.isNotEmpty()) { "BackstackPresenter requires at least one child." }

    val extractedBodies = mutableListOf<ViewModelRef<*>>()
    var lastChild: ChildPresenter? = null

    children.fastForEach { child ->
      val body = child.read()
      if (body != null) {
        extractedBodies += body
        lastChild = child
      }
    }

    // Forward all the slots from the last child with a body.
    lastChild?.readAllSlotsTo(outputSlots)

    val onBack = lastChild?.let { calculateBackHandler(it) }
    defaultViewModel = BackstackViewModel(
      entries = extractedBodies,
      onBack = onBack,
    )
  }

  /**
   * Forward onBack to last nested backstack, or explicit back handler if last body was a direct
   * child with an explicit back handler.
   */
  private fun PresenterPolicyScope.calculateBackHandler(
    lastBodyChild: ChildPresenter
  ): (() -> Unit)? =
    // TODO read the onBackRequested modifier from this child
    null
}
