package com.squareup.sample.authworkflow

data class LoginScreen(
  val errorMessage: String = "",
  val onLogin: (email: String, password: String) -> Unit = { _, _ -> },
  val onCancel: () -> Unit = {}
)
