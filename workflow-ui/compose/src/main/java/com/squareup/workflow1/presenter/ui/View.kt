package com.squareup.workflow1.presenter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastForEach
import com.squareup.workflow1.presenter.ViewModel
import com.squareup.workflow1.presenter.ViewModel.Empty
import com.squareup.workflow1.presenter.ViewModelRef
import com.squareup.workflow1.presenter.ViewModelResolver
import com.squareup.workflow1.presenter.ViewModelStack
import com.squareup.workflow1.presenter.hasType
import kotlin.reflect.typeOf

private val LocalViewRegistry = staticCompositionLocalOf<ViewRegistry> { EmptyViewRegistry }
val LocalViewModelResolver =
  staticCompositionLocalOf<ViewModelResolver> { error("No ViewModelResolver") }

fun interface View<in T : ViewModel> {
  @Composable
  fun Display(viewModel: T)
}

interface ViewRegistry {
  fun findView(viewModel: ViewModel): View<ViewModel>?
}

operator fun ViewRegistry.plus(childRegistry: ViewRegistry): ViewRegistry =
  CompositeViewRegistry(this, childRegistry)

object EmptyViewRegistry : ViewRegistry {
  override fun findView(viewModel: ViewModel): View<ViewModel>? = null
}

private class CompositeViewRegistry(
  private val parent: ViewRegistry,
  private val child: ViewRegistry
) : ViewRegistry {
  override fun findView(viewModel: ViewModel): View<ViewModel>? =
    child.findView(viewModel) ?: parent.findView(viewModel)
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

interface ComposableViewModel<T : ComposableViewModel<T>> : ViewModel {
  @Composable
  fun View(
    viewModel: T,
    modifier: Modifier
  )
}

@Composable
fun RootViewModel(
  root: ViewModelRef,
  resolver: ViewModelResolver,
  registry: ViewRegistry,
  modifier: Modifier = Modifier,
) {
  ViewRegistryProvider(registry) {
    context(resolver) {
      ViewModel(root, modifier)
    }
  }
}

/**
 * Displays [viewModel] by looking up a [View] for it in the [ViewRegistry].
 *
 * If [viewModel] is a [ViewModelRef] then it's assumed the [ViewRegistry] will look up the view
 * based on the ref's type, not the referent type, and the view's `Display` composable is keyed on
 * the instance of the ref.
 */
@NonRestartableComposable
@Composable
fun ViewModel(
  viewModel: ViewModel,
  modifier: Modifier = Modifier,
) {
  val ref = viewModel as? ViewModelRef
  if (ref != null) {
    ViewModel(ref, modifier)
  } else {
    ViewModelImpl(
      viewModel = viewModel,
      modifier = modifier,
    )
  }
}

/**
 * Displays [ref] by looking up a [View] for it in the [ViewRegistry].
 *
 * It's assumed the [ViewRegistry] will look up the view based on the ref's type, not the referent
 * type, and the view's `Display` composable is keyed on the instance of the ref.
 */
@Composable
fun ViewModel(
  ref: ViewModelRef,
  modifier: Modifier = Modifier,
) {
  // Key the whole child composition on the ref instance, not just the view resolution, since views
  // for different refs should not share any state.
  // TODO is this actually useful?
  key(ref) {
    val resolver = LocalViewModelResolver.current
    val resolved = resolver.resolveAsState(ref).value

    ViewModelImpl(
      viewModel = resolved,
      modifier = modifier,
    )
  }
}

@NonRestartableComposable
@Composable
private fun ViewModelImpl(
  viewModel: ViewModel,
  modifier: Modifier,
) {
  val viewRegistry = LocalViewRegistry.current
  val view: View<ViewModel> = remember(viewRegistry) {
    viewRegistry.findViewWithBuiltins(viewModel)
  }

  Box(
    propagateMinConstraints = true,
    modifier = modifier
  ) {
    view.Display(viewModel)
  }
}

@Suppress("UNCHECKED_CAST")
private fun ViewRegistry.findViewWithBuiltins(viewModel: ViewModel): View<ViewModel> =
  if (viewModel === Empty) {
    EmptyViewModelView as View<ViewModel>
  } else if (viewModel.hasType<ViewModelStack>()) {
    ViewModelStackView as View<ViewModel>
  } else {
    checkNotNull(findView(viewModel)) {
      "Could not find a view for view model $viewModel"
    }
  }

inline fun <reified T : ViewModel> ViewModel.hasType(): Boolean =
  hasType(typeOf<T>())

private object EmptyViewModelView : View<Empty> {
  @NonRestartableComposable
  @ReadOnlyComposable
  @NonSkippableComposable
  @Composable override fun Display(viewModel: Empty) {
    // Noop
  }
}

private object ViewModelStackView : View<ViewModelStack> {
  @Composable
  override fun Display(viewModel: ViewModelStack) {
    Box(propagateMinConstraints = true) {
      viewModel.viewModels.fastForEach { viewModel ->
        ViewModel(viewModel)
      }
    }
  }
}
