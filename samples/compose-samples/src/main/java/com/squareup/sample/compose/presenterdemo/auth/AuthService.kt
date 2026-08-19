package com.squareup.sample.compose.presenterdemo.auth

import kotlinx.coroutines.flow.StateFlow

interface AuthService {
  val isAuthorized: StateFlow<Boolean>

  suspend fun authenticateStage1(
    username: String,
    password: String
  ): Stage1AuthResult

  suspend fun authenticateStage2(pin: String): Boolean

  enum class Stage1AuthResult {
    Failed,
    Succeeded,
    NeedsStage2
  }
}
