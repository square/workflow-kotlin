package com.squareup.workflow1.internal.compose.runtime

import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlinx.coroutines.CoroutineDispatcher

internal actual val GlobalSnapshotCoroutineDispatcher: CoroutineDispatcher
  get() = AndroidUiDispatcher.Main[CoroutineDispatcher]!!
