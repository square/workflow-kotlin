package com.squareup.workflow1.internal.compose.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val GlobalSnapshotCoroutineDispatcher: CoroutineDispatcher
  get() = Dispatchers.Main
