package com.squareup.sample.gameworkflow

import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.parse
import com.squareup.workflow1.readUtf8WithLength
import com.squareup.workflow1.writeUtf8WithLength
import okio.ByteString

/**
 * Defines the names of the players of a Tic Tac Toe game.
 */
data class PlayerInfo(
  val xName: String = "",
  val oName: String = ""
) {
  fun toSnapshot(): Snapshot = Snapshot.write { sink ->
    sink.writeUtf8WithLength(xName)
    sink.writeUtf8WithLength(oName)
  }

  companion object {
    fun fromSnapshot(byteString: ByteString): PlayerInfo = byteString.parse {
      PlayerInfo(
        it.readUtf8WithLength(),
        it.readUtf8WithLength()
      )
    }
  }
}
