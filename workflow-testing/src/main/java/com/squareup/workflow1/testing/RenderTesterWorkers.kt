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
package com.squareup.workflow1.testing

import com.squareup.workflow1.ExperimentalWorkflowApi
import com.squareup.workflow1.Worker
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowOutput
import com.squareup.workflow1.testing.RenderTester.ChildWorkflowMatch.Matched
import com.squareup.workflow1.testing.RenderTester.ChildWorkflowMatch.NotMatched
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.isSupertypeOf
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.typeOf

/**
 * Specifies that this render pass is expected to run a [Worker] with the given [outputType].
 *
 * @param outputType the [KType] of the [Worker]'s `OutputT` type parameter.
 * @param key The key passed to [runningWorker][com.squareup.workflow1.runningWorker] when rendering
 * this workflow.
 * @param output If non-null, [WorkflowOutput.value] will be emitted when this worker is ran.
 * The [WorkflowAction] used to handle this output can be verified using methods on
 * [RenderTestResult].
 * @param description Optional string that will be used to describe this expectation in error
 * messages.
 */
/* ktlint-disable parameter-list-wrapping */
@OptIn(ExperimentalStdlibApi::class)
public inline fun <PropsT, StateT, OutputT, RenderingT>
    RenderTester<PropsT, StateT, OutputT, RenderingT>.expectWorkerOutputting(
  outputType: KType,
  key: String = "",
  crossinline assertWorker: (Worker<*>) -> Unit = {},
  output: WorkflowOutput<*>? = null,
  description: String = ""
): RenderTester<PropsT, StateT, OutputT, RenderingT> = expectWorker(
/* ktlint-enable parameter-list-wrapping */
    workerType = Worker::class.createType(listOf(KTypeProjection.covariant(outputType))),
    key = key,
    assertWorker = { assertWorker(it) },
    output = output,
    description = description.ifBlank { "worker outputting $outputType" + keyDescription(key) }
)

/**
 * Specifies that this render pass is expected to run a [Worker] that has the same type of the given
 * worker and for which the actual worker's [`doesSameWorkAs`][Worker.doesSameWorkAs] method returns
 * true. If a worker is ran that matches the type of [expected], but the actual worker's
 * `doesSameWorkAs` returns false, then an [AssertionError] will be thrown. If you need to perform
 * custom assertions, use the overload of this method that takes an `assertWhen` parameter.
 *
 * @param expected Worker passed to the actual worker's
 * [doesSameWorkAs][Worker.doesSameWorkAs] method to assert the worker matches.
 * @param key The key passed to [runningWorker][com.squareup.workflow1.runningWorker] when rendering
 * this workflow.
 * @param output If non-null, [WorkflowOutput.value] will be emitted when this worker is ran.
 * The [WorkflowAction] used to handle this output can be verified using methods on
 * [RenderTestResult].
 * @param description Optional string that will be used to describe this expectation in error
 * messages.
 */
/* ktlint-disable parameter-list-wrapping */
@OptIn(ExperimentalStdlibApi::class)
public inline fun <
    PropsT, StateT, OutputT, RenderingT, WorkerOutputT, reified WorkerT : Worker<WorkerOutputT>>
    RenderTester<PropsT, StateT, OutputT, RenderingT>.expectWorker(
  expected: WorkerT,
  key: String = "",
  output: WorkflowOutput<WorkerOutputT>? = null,
  description: String = ""
): RenderTester<PropsT, StateT, OutputT, RenderingT> = expectWorker(
/* ktlint-enable parameter-list-wrapping */
    workerType = typeOf<WorkerT>(),
    key = key,
    assertWorker = {
      if (!it.doesSameWorkAs(expected)) {
        throw AssertionError(
            "Expected actual worker's doesSameWorkAs to return true for expected worker $description\n" +
                "  expected=$expected\n" +
                "  actual=$it"
        )
      }
    },
    output = output,
    description = description.ifBlank { "worker $expected" + keyDescription(key) }
)

/**
 * Specifies that this render pass is expected to run a [Worker] with the given [workerClass]. The
 * worker's output type is not taken into consideration.
 *
 * @param workerClass The [KClass] of the worker that is expected to be run.
 * @param key The key passed to [runningWorker][com.squareup.workflow1.runningWorker] when rendering
 * this workflow.
 * @param assertWorker A function that will be passed the actual worker that matches this
 * expectation and can perform custom assertions on the worker instance.
 * @param output If non-null, [WorkflowOutput.value] will be emitted when this worker is ran.
 * The [WorkflowAction] used to handle this output can be verified using methods on
 * [RenderTestResult].
 * @param description Optional string that will be used to describe this expectation in error
 * messages.
 */
@OptIn(ExperimentalWorkflowApi::class)
/* ktlint-disable parameter-list-wrapping */
public inline fun <PropsT, StateT, OutputT, RenderingT, WorkerOutputT, WorkerT : Worker<WorkerOutputT>>
    RenderTester<PropsT, StateT, OutputT, RenderingT>.expectWorker(
  workerClass: KClass<out WorkerT>,
  key: String = "",
  crossinline assertWorker: (WorkerT) -> Unit = {},
  output: WorkflowOutput<WorkerOutputT>? = null,
  description: String = ""
): RenderTester<PropsT, StateT, OutputT, RenderingT> =
/* ktlint-enable parameter-list-wrapping */
  expectWorker(
      workerType = workerClass.starProjectedType,
      key = key,
      assertWorker = {
        @Suppress("UNCHECKED_CAST")
        assertWorker(it as WorkerT)
      },
      output = output,
      description = description.ifBlank { "worker $workerClass" + keyDescription(key) }
  )

/**
 * Specifies that this render pass is expected to run a [Worker] whose [KType] matches [workerType].
 *
 * @param workerType The [KType] of the [Worker] that is expected to be run. This will be compared
 * against the concrete type of the worker that is passed to
 * [runningWorker][com.squareup.workflow.runningWorker], but may be a supertype of that type. E.g.
 * an expected worker type of `typeOf<Worker<Collection<CharSequence>>>()` will match a worker that
 * has the type `SomeConcreteWorker<List<String>>`.
 * @param key The key passed to [runningWorker][com.squareup.workflow1.runningWorker] when rendering
 * this workflow.
 * @param assertWorker A function that will be passed the actual worker that matches this
 * expectation and can perform custom assertions on the worker instance.
 * @param output If non-null, [WorkflowOutput.value] will be emitted when this worker is ran.
 * The [WorkflowAction] used to handle this output can be verified using methods on
 * [RenderTestResult].
 * @param description Optional string that will be used to describe this expectation in error
 * messages.
 */
@OptIn(ExperimentalWorkflowApi::class, ExperimentalStdlibApi::class)
/* ktlint-disable parameter-list-wrapping */
public fun <PropsT, StateT, OutputT, RenderingT>
    RenderTester<PropsT, StateT, OutputT, RenderingT>.expectWorker(
  workerType: KType,
  key: String = "",
  assertWorker: (Worker<*>) -> Unit = {},
  output: WorkflowOutput<*>? = null,
  description: String = ""
): RenderTester<PropsT, StateT, OutputT, RenderingT> =
/* ktlint-enable parameter-list-wrapping */
  expectWorker(
      description = description.ifBlank { workerType.toString() + keyDescription(key) },
      output = output,
      exactMatch = true
  ) { actualWorkerType, worker, actualKey ->
    val ruleExpectsNothing = typeOf<Worker<Nothing>>().isSupertypeOf(workerType)
    val actualExpectsNothing = typeOf<Worker<Nothing>>().isSupertypeOf(actualWorkerType)

    // We have to take some care when the actual worker is `Worker<Nothing>`, b/c
    // Worker<Something>.isSupertypeOf(Worker<Nothing>) is always true -- Nothing is
    // the bottom type. So, we only make that check if the rule is `Worker<Nothing>`,
    // or both the rule and the actual are `Worker<Something>`.

    (key == actualKey &&
        (ruleExpectsNothing || !actualExpectsNothing) && workerType.isSupertypeOf(actualWorkerType))
        .also { if (it) assertWorker(worker) }
  }

/**
 * Specifies that this render pass is expected to run a [Worker] that matches [predicate].
 *
 * @param description A string that will be used to describe this expectation in error messages.
 * This overload requires a description since none can be derived from the predicate.
 * @param output If non-null, [WorkflowOutput.value] will be emitted when this worker is ran.
 * The [WorkflowAction] used to handle this output can be verified using methods on
 * [RenderTestResult].
 * @param exactMatch If true, then the test will fail if any other matching expectations are also
 * exact matches, and the expectation will only be allowed to match a single worker.
 * If false, the match will only be used if no other expectations return exclusive matches (in
 * which case the first match will be used), and the expectation may match multiple workers.
 * @param predicate A function which receives the actual instance of the worker, and the key
 * string, passed to [runningWorker][com.squareup.workflow.runningWorker], and returns true if the
 * worker matches the expectation.
 */
@OptIn(ExperimentalWorkflowApi::class)
/* ktlint-disable parameter-list-wrapping */
internal fun <PropsT, StateT, OutputT, RenderingT>
    RenderTester<PropsT, StateT, OutputT, RenderingT>.expectWorker(
  description: String,
  output: WorkflowOutput<*>? = null,
  exactMatch: Boolean = true,
  predicate: (
    workerType: KType,
    worker: Worker<*>,
    key: String
  ) -> Boolean
): RenderTester<PropsT, StateT, OutputT, RenderingT> =
/* ktlint-enable parameter-list-wrapping */
  expectWorkflow(
      description = description,
      exactMatch = exactMatch
  ) { invocation ->
    val workerType = invocation.workflow.workerWorkflowWorkerTypeOrNull()
        ?: return@expectWorkflow NotMatched
    if (predicate(workerType, invocation.props as Worker<*>, invocation.renderKey)) {
      Matched(Unit, output)
    } else {
      NotMatched
    }
  }

@PublishedApi internal fun keyDescription(key: String) =
  key.takeUnless { it.isEmpty() }
      ?.let { " with key \"$key\"" }
      .orEmpty()

private fun KType.isInstance(value: Any): Boolean {
  val actualClass = value::class
  if (classifier == actualClass) {
    // Exact KClass match means we don't need to compare any type parameters or supertypes.
    return true
  }
  // If the types aren't the same, then check for subtyping. Note that allSupertypes does not
  // include actualClass.
  return actualClass.allSupertypes.any { it.isSubtypeOf(this) }
}
