package com.squareup.sample.authworkflow

data class SecondFactorScreen(
  val errorMessage: String = "",
  val onSubmit: (String) -> Unit = {},
  val onCancel: () -> Unit = {}
)
