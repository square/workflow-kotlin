package com.squareup.sample.dungeon

import android.content.Context
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * Interface for TestApplication to implement to configure fake loading delay for UI tests.
 */
interface DungeonApplication {
  suspend fun delayForFakeLoad()
}

/**
 * Retrieves the loading delay from the application if it is a [DungeonApplication], or else a
 * default.
 */
@OptIn(ExperimentalTime::class)
suspend fun Context.delayForFakeLoad() =
  (applicationContext as? DungeonApplication)?.delayForFakeLoad()
    ?: delay(1.seconds.inWholeMilliseconds)
