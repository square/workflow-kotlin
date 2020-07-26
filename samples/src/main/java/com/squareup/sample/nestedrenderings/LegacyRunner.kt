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

import androidx.compose.Composable
import androidx.ui.core.Modifier
import androidx.ui.layout.fillMaxSize
import androidx.ui.tooling.preview.Preview
import com.squareup.sample.databinding.LegacyViewBinding
import com.squareup.sample.nestedrenderings.RecursiveWorkflow.LegacyRendering
import com.squareup.workflow.ui.compose.tooling.preview
import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.LayoutRunner.Companion.bind
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * A [LayoutRunner] that renders [LegacyRendering]s using the legacy view framework.
 */
@OptIn(WorkflowUiExperimentalApi::class)
class LegacyRunner(private val binding: LegacyViewBinding) : LayoutRunner<LegacyRendering> {

  override fun showRendering(
    rendering: LegacyRendering,
    viewEnvironment: ViewEnvironment
  ) {
    binding.stub.update(rendering.rendering, viewEnvironment)
  }

  companion object : ViewFactory<LegacyRendering> by bind(
      LegacyViewBinding::inflate, ::LegacyRunner
  )
}

@OptIn(WorkflowUiExperimentalApi::class)
@Preview(widthDp = 200, heightDp = 150, showBackground = true)
@Composable private fun LegacyRunnerPreview() {
  LegacyRunner.preview(
      rendering = LegacyRendering("child"),
      placeholderModifier = Modifier.fillMaxSize()
  )
}
