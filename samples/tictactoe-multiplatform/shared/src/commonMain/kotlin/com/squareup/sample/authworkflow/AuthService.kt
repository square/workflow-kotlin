package com.squareup.sample.authworkflow

import kotlin.coroutines.cancellation.CancellationException

interface AuthService {

  fun login(
    request: AuthRequest,
    onLogin: (AuthResponse) -> Unit,
    onError: (Error) -> Unit
  ): Unit

  fun secondFactor(
    request: SecondFactorRequest,
    onLogin: (AuthResponse) -> Unit,
    onError: (Error) -> Unit
  ): Unit

  @Throws(CancellationException::class, IllegalStateException::class)
  suspend fun loginSuspend(request: AuthRequest): AuthResponse

  @Throws(CancellationException::class, IllegalStateException::class)
  suspend fun secondFactorSuspend(request: SecondFactorRequest): AuthResponse

  data class AuthRequest(
    val email: String,
    val password: String
  )

  data class AuthResponse(
    val errorMessage: String,
    val token: String,
    val twoFactorRequired: Boolean
  )

  data class SecondFactorRequest(
    val token: String,
    val secondFactor: String
  )
}
