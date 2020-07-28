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
package com.squareup.workflow1

import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class SimpleLoggingWorkflowInterceptorTest {

  @Test fun `onSessionStarted handles logging exceptions`() {
    val interceptor = ErrorLoggingInterceptor()
    val scope = CoroutineScope(EmptyCoroutineContext)
    interceptor.onSessionStarted(scope, TestWorkflowSession)
    scope.cancel()

    assertEquals(ErrorLoggingInterceptor.EXPECTED_ERRORS, interceptor.errors)
  }

  @Test fun `onInitialState handles logging exceptions`() {
    val interceptor = ErrorLoggingInterceptor()
    interceptor.onInitialState(Unit, null, { _, _ -> }, TestWorkflowSession)

    assertEquals(ErrorLoggingInterceptor.EXPECTED_ERRORS, interceptor.errors)
  }

  @Test fun `onPropsChanged handles logging exceptions`() {
    val interceptor = ErrorLoggingInterceptor()
    interceptor.onPropsChanged(Unit, Unit, Unit, { _, _, _ -> }, TestWorkflowSession)

    assertEquals(ErrorLoggingInterceptor.EXPECTED_ERRORS, interceptor.errors)
  }

  @Test fun `onRender handles logging exceptions`() {
    val interceptor = ErrorLoggingInterceptor()
    val context = object : BaseRenderContext<Unit, Unit, Nothing> {
      override val actionSink: Sink<WorkflowAction<Unit, Unit, Nothing>>
        get() = fail()

      override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
        child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
        props: ChildPropsT,
        key: String,
        handler: (ChildOutputT) -> WorkflowAction<Unit, Unit, Nothing>
      ): ChildRenderingT = fail()

      override fun runningSideEffect(
        key: String,
        sideEffect: suspend () -> Unit
      ) = fail()
    }
    interceptor.onRender(Unit, Unit, context, { _, _, _ -> }, TestWorkflowSession)

    assertEquals(ErrorLoggingInterceptor.EXPECTED_ERRORS, interceptor.errors)
  }

  @Test fun `onSnapshotState handles logging exceptions`() {
    val interceptor = ErrorLoggingInterceptor()
    interceptor.onSnapshotState(Unit, { null }, TestWorkflowSession)

    assertEquals(ErrorLoggingInterceptor.EXPECTED_ERRORS, interceptor.errors)
  }

  private class ErrorLoggingInterceptor : SimpleLoggingWorkflowInterceptor() {
    val errors = mutableListOf<String>()

    override fun log(text: String) {
      throw IllegalArgumentException()
    }

    override fun logError(text: String) {
      errors += text
    }

    companion object {
      val EXPECTED_ERRORS = listOf(
          "ErrorLoggingInterceptor.logBeforeMethod threw exception:\n" +
              IllegalArgumentException::class.qualifiedName.toString(),
          "ErrorLoggingInterceptor.logAfterMethod threw exception:\n" +
              IllegalArgumentException::class.qualifiedName.toString()
      )
    }
  }

  @OptIn(ExperimentalWorkflowApi::class)
  private object TestWorkflowSession : WorkflowSession {
    @OptIn(ExperimentalStdlibApi::class)
    override val identifier: WorkflowIdentifier = unsnapshottableIdentifier(typeOf<Unit>())
    override val renderKey: String get() = "key"
    override val sessionId: Long get() = 42
    override val parent: WorkflowSession? get() = null
  }
}
