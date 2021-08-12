package com.squareup.sample.gameworkflow

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.parse
import okio.ByteString

/**
 * The [lastTurn] of a tic tac toe game, and its [ending]. Serves as the
 * result type for [TakeTurnsWorkflow].
 */
data class CompletedGame(
  val ending: Ending,
  val lastTurn: Turn = Turn()
) {

  fun toSnapshot(): Snapshot {
    return Snapshot.write { sink ->
      sink.writeInt(ending.ordinal)
    }
  }

  companion object {
    fun fromSnapshot(byteString: ByteString): CompletedGame {
      return byteString.parse { source ->
        CompletedGame(
            Ending.values()[source.readInt()]
        )
      }
    }
  }
}

enum class Ending {
  Victory,
  Draw,
  Quitted
}
