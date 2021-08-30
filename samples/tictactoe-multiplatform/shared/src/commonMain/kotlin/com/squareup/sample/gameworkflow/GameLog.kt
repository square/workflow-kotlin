package com.squareup.sample.gameworkflow

import com.squareup.sample.gameworkflow.GameLog.LogResult
import com.squareup.sample.gameworkflow.GameLog.LogResult.LOGGED
import com.squareup.sample.gameworkflow.GameLog.LogResult.TRY_LATER
import kotlinx.coroutines.delay

/**
 * "Saves" game state, to demonstrate using services from a workflow.
 * Actually just reports success or failure, usually the latter.
 */
interface GameLog {
  enum class LogResult {
    TRY_LATER,
    LOGGED
  }

  suspend fun logGame(game: CompletedGame): LogResult
}

class RealGameLog : GameLog {
  private var attempt = 1

  override suspend fun logGame(game: CompletedGame): LogResult {
    delay(1000)
    return if (attempt++ % 3 == 0) {
      LOGGED
    } else {
      TRY_LATER
    }
  }
}
