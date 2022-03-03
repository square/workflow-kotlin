@file:OptIn(WorkflowUiExperimentalApi::class)
package com.squareup.sample.dungeon

import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.AlertOverlay

class DungeonRootUi constructor(
  body: Screen,
  alert: AlertOverlay? = null
) : AndroidScreen<DungeonRootUi> {
  override val viewFactory: ScreenViewFactory<DungeonRootUi>
    get() = TODO("ray")
}
