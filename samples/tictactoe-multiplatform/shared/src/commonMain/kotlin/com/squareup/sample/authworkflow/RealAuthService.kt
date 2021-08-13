package com.squareup.sample.authworkflow

import com.squareup.sample.authworkflow.AuthService.AuthRequest
import com.squareup.sample.authworkflow.AuthService.AuthResponse
import com.squareup.sample.authworkflow.AuthService.SecondFactorRequest
import kotlinx.coroutines.delay

class RealAuthService : AuthService {

  override suspend fun login(request: AuthRequest): AuthResponse {
    delay(DELAY_MILLIS.toLong())
    return when {
      "password" != request.password ->
          AuthResponse("Unknown email or invalid password", "", false)
      request.email.contains("2fa") -> AuthResponse("", WEAK_TOKEN, true)
      else -> AuthResponse("", REAL_TOKEN, false)
    }
  }

  override suspend fun secondFactor(request: SecondFactorRequest): AuthResponse {
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
