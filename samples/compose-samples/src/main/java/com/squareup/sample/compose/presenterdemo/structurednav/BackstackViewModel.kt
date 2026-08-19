package com.squareup.sample.compose.presenterdemo.structurednav

import com.squareup.workflow1.presenter.ViewModelRef

data class BackstackViewModel(
  val entries: List<ViewModelRef<*>>,
  val onBack: (() -> Unit)?,
)
