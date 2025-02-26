package com.squareup.workflow1.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val GlobalSnapshotCoroutineDispatcher: CoroutineDispatcher
  get() = Dispatchers.Main
