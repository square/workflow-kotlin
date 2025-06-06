package com.squareup.sample.dungeon

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.squareup.workflow1.ui.withRegistry
import com.squareup.workflow1.ui.workflowContentView
import kotlinx.coroutines.flow.map

class DungeonActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Ignore config changes for now.
    val component = Component(this)
    val model: TimeMachineModel by viewModels { component.timeMachineModelFactory }

    workflowContentView
      .take(lifecycle, model.renderings.map { it.withRegistry(component.viewRegistry) })
  }
}
