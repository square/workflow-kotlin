package com.squareup.sample.dungeon

import android.app.Application

/**
 * Custom application to disable fake loading delays.
 */
class TestApplication : Application(), DungeonApplication {
  override suspend fun delayForFakeLoad() = Unit
}
