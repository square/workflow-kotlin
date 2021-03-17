/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.sample.nestedrenderings

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowLayout
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.compose.withCompositionRoot
import com.squareup.workflow1.ui.renderWorkflowIn
import kotlinx.coroutines.flow.StateFlow

@OptIn(WorkflowUiExperimentalApi::class)
private val viewRegistry = ViewRegistry(
    RecursiveViewFactory,
    LegacyRunner
)

@OptIn(WorkflowUiExperimentalApi::class)
private val viewEnvironment =
  ViewEnvironment(mapOf(ViewRegistry to viewRegistry)).withCompositionRoot { content ->
    CompositionLocalProvider(LocalBackgroundColor provides Color.Green) {
      content()
    }
  }

@WorkflowUiExperimentalApi
class NestedRenderingsActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val model: NestedRenderingsModel by viewModels()
    setContentView(
        WorkflowLayout(this).apply {
          start(
              renderings = model.renderings,
              environment = viewEnvironment
          )
        }
    )
  }

  class NestedRenderingsModel(savedState: SavedStateHandle) : ViewModel() {
    @OptIn(WorkflowUiExperimentalApi::class)
    val renderings: StateFlow<Any> by lazy {
      renderWorkflowIn(
          workflow = RecursiveWorkflow,
          scope = viewModelScope,
          savedStateHandle = savedState
      )
    }
  }
}
