package com.squareup.sample.authworkflow

import com.squareup.sample.authworkflow.AuthService.AuthRequest
import com.squareup.sample.authworkflow.AuthService.AuthResponse
import com.squareup.sample.authworkflow.AuthService.SecondFactorRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class RealAuthService(val scope: CoroutineScope) : AuthService {

  override fun login(
    request: AuthRequest,
    onLogin: (AuthResponse) -> Unit,
    onError: (Error) -> Unit
  ) {
    scope.launch {
      try {
        val authResponse = loginSuspend(request)
        onLogin(authResponse)
      } catch (e: Error) {
        onError(e)
      }
    }
  }

  override fun secondFactor(
    request: SecondFactorRequest,
    onLogin: (AuthResponse) -> Unit,
    onError: (Error) -> Unit
  ) {
    scope.launch {
      try {
        val authResponse = secondFactorSuspend(request)
        onLogin(authResponse)
      } catch (e: Error) {
        onError(e)
      }
    }
  }

  @Throws(CancellationException::class, IllegalStateException::class)
  override suspend fun loginSuspend(request: AuthRequest): AuthResponse {
    delay(DELAY_MILLIS.toLong())
    return when {
      "password" != request.password ->
        AuthResponse("Unknown email or invalid password", "", false)
      request.email.contains("2fa") -> AuthResponse("", WEAK_TOKEN, true)
      else -> AuthResponse("", REAL_TOKEN, false)
    }
  }

  @Throws(CancellationException::class, IllegalStateException::class)
  override suspend fun secondFactorSuspend(request: SecondFactorRequest): AuthResponse {
    delay(DELAY_MILLIS.toLong())
    return when {
      WEAK_TOKEN != request.token ->
          AuthResponse("404!! What happened to your token there bud?!?!", "", false)
      SECOND_FACTOR != request.secondFactor ->
          AuthResponse(
              "Invalid second factor (try $SECOND_FACTOR)", WEAK_TOKEN,
              true
          )
      else -> AuthResponse("", REAL_TOKEN, false)
    }
  }

  companion object {
    private const val DELAY_MILLIS = 750
    private const val WEAK_TOKEN = "need a second factor there, friend"
    private const val REAL_TOKEN = "welcome aboard!"
    private const val SECOND_FACTOR = "1234"
  }
}
