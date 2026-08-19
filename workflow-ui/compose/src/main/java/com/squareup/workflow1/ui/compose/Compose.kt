package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.squareup.workflow1.ViewModel
import com.squareup.workflow1.ViewModelRef
import com.squareup.workflow1.ViewModelResolver
import com.squareup.workflow1.resolveAsStateFlow

private val LocalViewRegistry = compositionLocalOf<ViewRegistry> { EmptyViewRegistry }

fun interface View<in T : ViewModel> {
  @Composable
  context(_: ViewModelResolver)
  fun Display(
    viewModel: T,
    modifier: Modifier
  )
}

fun interface ViewRegistry {
  fun findView(ref: ViewModelRef<*>): View<ViewModel>?
}

object EmptyViewRegistry : ViewRegistry {
  override fun findView(ref: ViewModelRef<*>): View<ViewModel>? = null
}

private class CompositeViewRegistry(
  private val parent: ViewRegistry,
  private val child: ViewRegistry
) : ViewRegistry {
  override fun findView(ref: ViewModelRef<*>): View<ViewModel>? =
    child.findView(ref) ?: parent.findView(ref)
}

@Composable
fun ViewRegistryProvider(
  viewRegistry: ViewRegistry,
  content: @Composable () -> Unit
) {
  val compositeRegistry = CompositeViewRegistry(LocalViewRegistry.current, viewRegistry)
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
  root: ViewModelRef<*>,
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

@Composable
context(_: ViewModelResolver)
fun ViewModel(
  ref: ViewModelRef<*>,
  modifier: Modifier = Modifier,
) {
  key(ref) {
    val viewRegistry = LocalViewRegistry.current
    val view = remember(viewRegistry) {
      checkNotNull(viewRegistry.findView(ref)) {
        "Could not find a view for ref $ref"
      }
    }
    val viewModel by remember { ref.resolveAsStateFlow() }.collectAsState()
    view.Display(viewModel, modifier)
  }
}
