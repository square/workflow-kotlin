package com.squareup.sample.dungeon

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

class DungeonActivity : AppCompatActivity() {

  @OptIn(WorkflowUiExperimentalApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Ignore config changes for now.
    val component = Component(this)
    val model: TimeMachineModel by viewModels { component.timeMachineModelFactory }

    setContentView(
      WorkflowLayout(this).apply { start(model.renderings, component.viewRegistry) }
    )
  }
}
