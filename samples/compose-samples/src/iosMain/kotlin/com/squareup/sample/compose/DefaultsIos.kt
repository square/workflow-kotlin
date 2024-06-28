package com.squareup.sample.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import store

@Composable
actual fun BackHandler(isEnabled: Boolean, onBack: () -> Unit) {
  LaunchedEffect(isEnabled) {
    if (isEnabled) {
      store.events.collect {
        onBack()
      }
    }
  }
}
