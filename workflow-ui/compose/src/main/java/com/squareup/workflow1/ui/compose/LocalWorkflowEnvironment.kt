package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.squareup.workflow1.ui.ViewEnvironment

public val LocalWorkflowEnvironment: ProvidableCompositionLocal<ViewEnvironment> =
  compositionLocalOf { ViewEnvironment.EMPTY }
