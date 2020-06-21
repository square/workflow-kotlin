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
package com.squareup.workflow.testing

import com.squareup.workflow.StatefulWorkflow
import com.squareup.workflow.WorkflowSeed
import com.squareup.workflow.WorkflowSeed.Companion.forTest
import com.squareup.workflow.WorkflowSeed.Companion.fromRootWorkflowSnapshot
import com.squareup.workflow.WorkflowSeed.Companion.fromSnapshot
import com.squareup.workflow.WorkflowSession
import com.squareup.workflow.internal.DoubleCheckingWorkflowLoop
import com.squareup.workflow.internal.RealWorkflowLoop
import com.squareup.workflow.launchWorkflowImpl
import com.squareup.workflow.launchWorkflowIn
import com.squareup.workflow.testing.WorkflowTestParams.StartMode.StartFresh
import com.squareup.workflow.testing.WorkflowTestParams.StartMode.StartFromCompleteSnapshot
import com.squareup.workflow.testing.WorkflowTestParams.StartMode.StartFromState
import com.squareup.workflow.testing.WorkflowTestParams.StartMode.StartFromWorkflowSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.TestOnly

/**
 * Launches the [workflow] in a new coroutine in [scope]. The workflow tree is seeded with
 * [WorkflowTestParams.StartMode] and the first value emitted by [props].  Subsequent values
 * emitted from [props] will be used to re-render the workflow.
 *
 * See [launchWorkflowIn] for documentation about most of the parameters and behavior.
 */
@TestOnly
fun <PropsT, StateT, OutputT : Any, RenderingT, RunnerT> launchWorkflowForTestFromStateIn(
  scope: CoroutineScope,
  workflow: StatefulWorkflow<PropsT, StateT, OutputT, RenderingT>,
  props: Flow<PropsT>,
  testParams: WorkflowTestParams<StateT>,
  beforeStart: CoroutineScope.(session: WorkflowSession<OutputT, RenderingT>) -> RunnerT
): RunnerT {
  val workflowInitializer: (PropsT) -> WorkflowSeed<PropsT, StateT> = { initialProps ->
    when (val startMode = testParams.startFrom) {
      StartFresh -> fromSnapshot(workflow, initialProps, snapshot = null)
      is StartFromState -> forTest(initialProps, startMode.state)
      is StartFromWorkflowSnapshot -> {
        fromRootWorkflowSnapshot(workflow, initialProps, startMode.snapshot.bytes)
      }
      is StartFromCompleteSnapshot -> fromSnapshot(workflow, initialProps, startMode.snapshot.bytes)
    }
  }

  return launchWorkflowImpl(
      scope,
      if (testParams.checkRenderIdempotence) DoubleCheckingWorkflowLoop() else RealWorkflowLoop(),
      workflow,
      props,
      workflowInitializer = workflowInitializer,
      beforeStart = beforeStart,
      // Use the base context as the starting point for the worker context since it's often used
      // to pass in test dispatchers.
      // Remove any job present in the base context since worker context aren't allowed to contain
      // jobs (it would violate structured concurrency).
      workerContext = scope.coroutineContext.minusKey(Job)
  )
}
