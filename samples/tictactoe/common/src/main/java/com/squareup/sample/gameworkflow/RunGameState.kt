package com.squareup.sample.gameworkflow

import com.squareup.sample.gameworkflow.SyncState.SAVING
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.parse
import com.squareup.workflow1.readByteStringWithLength
import com.squareup.workflow1.readUtf8WithLength
import com.squareup.workflow1.writeByteStringWithLength
import com.squareup.workflow1.writeUtf8WithLength
import okio.ByteString

/**
 * The state of a [RealRunGameWorkflow].
 */
sealed class RunGameState {
  internal data class Playing(
    val playerInfo: PlayerInfo,
    val resume: Turn? = null
  ) : RunGameState()

  internal data class NewGame(
    val defaultXName: String = "X",
    val defaultOName: String = "O"
  ) : RunGameState()

  internal data class MaybeQuitting(
    val playerInfo: PlayerInfo,
    val completedGame: CompletedGame
  ) : RunGameState()

  internal data class MaybeQuittingForSure(
    val playerInfo: PlayerInfo,
    val completedGame: CompletedGame
  ) : RunGameState()

  data class GameOver(
    val playerInfo: PlayerInfo,
    val completedGame: CompletedGame,
    val syncState: SyncState = SAVING
  ) : RunGameState()

  fun toSnapshot(): Snapshot {
    return Snapshot.write { sink ->
      sink.writeUtf8WithLength(this::class.java.name)

      when (this) {
        is Playing -> {
          sink.writeByteStringWithLength(playerInfo.toSnapshot().bytes)
        }
        is NewGame -> {
          sink.writeUtf8WithLength(defaultXName)
          sink.writeUtf8WithLength(defaultOName)
        }
        is MaybeQuitting -> {
          sink.writeByteStringWithLength(completedGame.toSnapshot().bytes)
          sink.writeByteStringWithLength(playerInfo.toSnapshot().bytes)
        }
        is MaybeQuittingForSure -> {
          sink.writeByteStringWithLength(completedGame.toSnapshot().bytes)
          sink.writeByteStringWithLength(playerInfo.toSnapshot().bytes)
        }
        is GameOver -> {
          sink.writeByteStringWithLength(completedGame.toSnapshot().bytes)
          sink.writeByteStringWithLength(playerInfo.toSnapshot().bytes)
        }
      }
    }
  }

  companion object {
    fun fromSnapshot(byteString: ByteString): RunGameState {
      byteString.parse { source ->
        return when (val className = source.readUtf8WithLength()) {
          Playing::class.java.name -> Playing(
              PlayerInfo.fromSnapshot(source.readByteStringWithLength())
          )

          NewGame::class.java.name -> NewGame(
              source.readUtf8WithLength(),
              source.readUtf8WithLength()
          )

          MaybeQuitting::class.java.name -> MaybeQuitting(
              PlayerInfo.fromSnapshot(source.readByteStringWithLength()),
              CompletedGame.fromSnapshot(source.readByteStringWithLength())
          )

          MaybeQuittingForSure::class.java.name -> MaybeQuittingForSure(
              PlayerInfo.fromSnapshot(source.readByteStringWithLength()),
              CompletedGame.fromSnapshot(source.readByteStringWithLength())
          )

          GameOver::class.java.name -> GameOver(
              PlayerInfo.fromSnapshot(source.readByteStringWithLength()),
              CompletedGame.fromSnapshot(source.readByteStringWithLength())
          )

          else -> throw IllegalArgumentException("Unknown type $className")
        }
      }
    }
  }
}

/**
 * Sub-state of [RunGameState.GameOver], indicates if we're in the process of saving the game,
 * or if not, how that went.
 */
enum class SyncState {
  SAVING,
  SAVE_FAILED,
  SAVED
}
