package com.squareup.sample.authworkflow

import com.squareup.workflow1.ui.Screen

data class LoginScreen(
  val errorMessage: String = "",
  val onLogin: (email: String, password: String) -> Unit = { _, _ -> },
  val onCancel: () -> Unit = {}
) : Screen
