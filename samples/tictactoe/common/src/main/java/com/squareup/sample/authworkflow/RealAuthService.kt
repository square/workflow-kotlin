package com.squareup.sample.authworkflow

import com.squareup.sample.authworkflow.AuthService.AuthRequest
import com.squareup.sample.authworkflow.AuthService.AuthResponse
import com.squareup.sample.authworkflow.AuthService.SecondFactorRequest
import io.reactivex.Single
import java.lang.String.format
import java.util.concurrent.TimeUnit

class RealAuthService : AuthService {

  override fun login(request: AuthRequest): Single<AuthResponse> {
    return when {
      "password" != request.password -> response(
        AuthResponse("Unknown email or invalid password", "", false)
      )
      request.email.contains("2fa") -> response(AuthResponse("", WEAK_TOKEN, true))
      else -> response(AuthResponse("", REAL_TOKEN, false))
    }
  }

  override fun secondFactor(request: SecondFactorRequest): Single<AuthResponse> {
    return when {
      WEAK_TOKEN != request.token -> response(
        AuthResponse("404!! What happened to your token there bud?!?!", "", false)
      )
      SECOND_FACTOR != request.secondFactor -> response(
        AuthResponse(
          format("Invalid second factor (try %s)", SECOND_FACTOR),
          WEAK_TOKEN,
          true
        )
      )
      else -> response(AuthResponse("", REAL_TOKEN, false))
    }
  }

  companion object {
    private const val DELAY_MILLIS = 750
    private const val WEAK_TOKEN = "need a second factor there, friend"
    private const val REAL_TOKEN = "welcome aboard!"
    private const val SECOND_FACTOR = "1234"

    private fun <R> response(response: R): Single<R> {
      return Single.just(response)
        .delay(DELAY_MILLIS.toLong(), TimeUnit.MILLISECONDS)
    }
  }
}
