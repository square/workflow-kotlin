package com.squareup.sample.mainworkflow

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.parse
import com.squareup.workflow1.readUtf8WithLength
import com.squareup.workflow1.writeUtf8WithLength
import okio.ByteString

/**
 * The state of [MainWorkflow]. Indicates which nested workflow is running, and records
 * the current nested state.
 */
sealed class MainState {

  internal object Authenticating : MainState()

  internal object RunningGame : MainState()

  fun toSnapshot(): Snapshot {
    return Snapshot.write { sink -> sink.writeUtf8WithLength(this::class.java.name) }
  }

  companion object {
    fun fromSnapshot(byteString: ByteString): MainState = byteString.parse {
      return when (val mainStateName = it.readUtf8WithLength()) {
        Authenticating::class.java.name -> Authenticating
        RunningGame::class.java.name -> RunningGame
        else -> throw IllegalArgumentException("Unrecognized state: $mainStateName")
      }
    }
  }
}
