/*
 * Copyright 2019 Square Inc.
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
package com.squareup.workflow1.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.ui.WorkflowRunner.Config
import kotlinx.coroutines.isActive

/**
 * A [Fragment] that can run a workflow. Subclasses implement [onCreateWorkflow]
 * to configure themselves with a [Workflow], [ViewRegistry] and [inputs][Config.props].
 *
 * For a workflow with no inputs, or a static configuration, that's as simple as:
 *
 *    class HelloWorkflowFragment : WorkflowFragment<Unit, Unit>() {
 *      override fun onCreateWorkflow(): Config<Unit, Unit> {
 *        return Config(
 *            workflow = HelloWorkflow,
 *            input = Unit
 *        )
 *      }
 *    }
 *
 * A fragment to run a workflow whose configuration may need to be updated could
 * provide a method like this:
 *
 *   class HelloWorkflowFragment : WorkflowFragment<HelloInput, Unit>() {
 *     private val inputs = BehaviorSubject.createDefault(HelloInput.Fnord)
 *
 *     fun input(input: HelloInput) = inputs.onNext(input)
 *
 *     override fun onCreateWorkflow(): Config<HelloInput, Unit> {
 *       return Config(
 *           workflow = HelloWorkflow,
 *           inputs = inputs
 *       )
 *     }
 *   }
 */
@WorkflowUiExperimentalApi
abstract class WorkflowFragment<PropsT, OutputT> : Fragment() {
  private lateinit var _runner: WorkflowRunner<OutputT>

  /**
   * Provides the [ViewRegistry] used to display workflow renderings.
   */
  protected abstract val viewEnvironment: ViewEnvironment

  /**
   * Called from [onCreateView], so it should be safe for implementations
   * to call [getActivity].
   */
  protected abstract fun onCreateWorkflow(): Config<PropsT, OutputT>

  /**
   * Called with the output emitted by the root workflow. Called only while
   * the fragment is active, and always called from the UI thread.
   */
  protected abstract fun onResult(output: OutputT)

  /**
   * Provides subclasses with access to the products of the running [Workflow].
   * Safe to call after [onCreateView].
   */
  protected val runner: WorkflowRunner<OutputT> get() = _runner

  final override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): WorkflowLayout {
    val workflowLayout = WorkflowLayout(inflater.context)
    _runner = WorkflowRunner.startWorkflow(this, ::onCreateWorkflow)

    // https://github.com/square/workflow-kotlin/issues/14
    // We're careful to start up the workflow runtime before the view is attached,
    // since that's what LayoutRunner promises. When we're sloppy about that, we
    // break things like Jetpack Navigation and nested fragments.
    workflowLayout.start(runner.renderings, viewEnvironment)

    lifecycleScope.launchWhenStarted {
      while (isActive) {
        onResult(runner.receiveOutput())
      }
    }
    return workflowLayout
  }
}
