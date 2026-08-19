package com.squareup.workflow1.presenter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.squareup.workflow1.presenter.ViewModelRef
import com.squareup.workflow1.presenter.resolve
import kotlin.reflect.KClass

private val LocalViewRegistry = staticCompositionLocalOf<ViewRegistry> { EmptyViewRegistry }

fun interface View<in T : Any> {
  @Composable
  fun Display(
    viewModel: T,
    modifier: Modifier,
  )
}

interface ViewRegistry {
  fun <T : Any> findView(viewModelType: KClass<T>): View<T>?
}

operator fun ViewRegistry.plus(childRegistry: ViewRegistry): ViewRegistry =
  CompositeViewRegistry(this, childRegistry)

object EmptyViewRegistry : ViewRegistry {
  override fun <T : Any> findView(viewModelType: KClass<T>): View<T>? = null
}

private class CompositeViewRegistry(
  private val parent: ViewRegistry,
  private val child: ViewRegistry
) : ViewRegistry {
  override fun <T : Any> findView(viewModelType: KClass<T>): View<T>? =
    child.findView(viewModelType) ?: parent.findView(viewModelType)
}

@Composable
fun ViewRegistryProvider(
  viewRegistry: ViewRegistry,
  content: @Composable () -> Unit
) {
  val parentRegistry = LocalViewRegistry.current
  // Registry identity is keyed on by some composables, so keep the same instance across
  // recompositions.
  val compositeRegistry = remember(viewRegistry, parentRegistry) {
    parentRegistry + viewRegistry
  }
  CompositionLocalProvider(
    LocalViewRegistry provides compositeRegistry,
    content = content
  )
}

/**
 * Displays [ref] by looking up a [View] for it in the [ViewRegistry].
 *
 * It's assumed the [ViewRegistry] will look up the view based on the ref's type, not the referent
 * type, and the view's `Display` composable is keyed on the instance of the ref.
 */
@Composable
fun <T : Any> ViewModel(
  ref: ViewModelRef<T>,
  modifier: Modifier = Modifier,
) {
  val refType = ref.type
  key(refType) {
    val viewRegistry = LocalViewRegistry.current
    val view: View<T> = remember(viewRegistry) {
      viewRegistry.findView(refType) ?: error("Could not find View for model type: $refType")
    }

    val resolved = ref.resolve()
    view.Display(resolved, modifier)
  }
}
