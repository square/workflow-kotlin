package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.DESTROYED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.setContentWithLifecycle(
  lifecycleOwner: LifecycleOwner = IosLifecycleOwner(),
  content: @Composable () -> Unit
) {
  setContent {
    (lifecycleOwner as? IosLifecycleOwner)?.let {
      DisposableEffect(Unit) {
        with(lifecycleOwner.registry) {
          currentState = RESUMED

          onDispose {
            currentState = DESTROYED
          }
        }
      }
    }

    CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
      content()
    }
  }
}


class IosLifecycleOwner : LifecycleOwner {
  val registry = LifecycleRegistry(this)
  override val lifecycle: Lifecycle
    get() = registry
}
