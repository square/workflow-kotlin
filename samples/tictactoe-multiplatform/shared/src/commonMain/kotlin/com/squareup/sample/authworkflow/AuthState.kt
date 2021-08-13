package com.squareup.sample.authworkflow

sealed class AuthState {
  internal data class LoginPrompt(val errorMessage: String = "") : AuthState()

  internal data class Authorizing(
    val email: String,
    val password: String
  ) : AuthState()

  internal data class SecondFactorPrompt(
    val tempToken: String,
    val errorMessage: String = ""
  ) : AuthState()

  internal data class AuthorizingSecondFactor(
    val tempToken: String,
    val secondFactor: String
  ) : AuthState()
}

sealed class AuthResult {
  data class Authorized(val token: String) : AuthResult()
  object Canceled : AuthResult()
}
