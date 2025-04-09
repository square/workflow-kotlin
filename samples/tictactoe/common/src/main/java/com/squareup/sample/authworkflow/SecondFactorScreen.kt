package com.squareup.sample.authworkflow

import com.squareup.workflow1.ui.Screen

data class SecondFactorScreen(
  val errorMessage: String = "",
  val onSubmit: (String) -> Unit = {},
  val onCancel: () -> Unit = {}
) : Screen
