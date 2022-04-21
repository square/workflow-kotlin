package com.squareup.sample.gameworkflow

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerInfoTest {
  @Test fun readWriteTurn() {
    val before = PlayerInfo("able", "baker")
    val out = before.toSnapshot()
    val after = PlayerInfo.fromSnapshot(out.bytes)
    assertThat(after).isEqualTo(before)
  }
}
