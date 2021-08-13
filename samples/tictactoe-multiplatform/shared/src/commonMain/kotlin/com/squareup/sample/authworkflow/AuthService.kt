package com.squareup.sample.authworkflow

interface AuthService {
  suspend fun login(request: AuthRequest): AuthResponse

  suspend fun secondFactor(request: SecondFactorRequest): AuthResponse

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
