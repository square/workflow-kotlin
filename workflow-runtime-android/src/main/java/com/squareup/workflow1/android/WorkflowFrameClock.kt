package com.squareup.workflow1.android

import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.monotonicFrameClock
import androidx.compose.ui.platform.AndroidUiDispatcher
import com.squareup.workflow1.WorkflowFrameClock

@OptIn(ExperimentalComposeApi::class)
public object AndroidFrameClock: WorkflowFrameClock {

  private val composeAndroidFrameClock = AndroidUiDispatcher.Main.monotonicFrameClock

  override suspend fun resumeOnFrame() {
    composeAndroidFrameClock.withFrameNanos {
      println("SAE: With Frame Nanos Callback! at time: $it")
      // no-op, we just need to resume at the frame barrier!
    }
    println("SAE: Resumed from `withFrameNanos`")
  }
}
