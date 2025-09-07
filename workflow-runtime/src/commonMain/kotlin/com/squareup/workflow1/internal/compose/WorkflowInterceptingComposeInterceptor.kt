package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.compose.ComposeWorkflow
import com.squareup.workflow1.compose.ComposeWorkflowInterceptor
import com.squareup.workflow1.compose.WorkflowComposableRuntimeConfig
import com.squareup.workflow1.compose.internal._UNSAFE_createComposeWorkflowAsStatefulWorkflow
import com.squareup.workflow1.compose.internal.withCompositionLocals
import com.squareup.workflow1.intercept
import com.squareup.workflow1.internal.IdCounter
import com.squareup.workflow1.internal.WorkflowNodeId
import com.squareup.workflow1.internal.createId
import com.squareup.workflow1.internal.id
import com.squareup.workflow1.workflowSessionToString

/**
 * Returns a [WorkflowComposableRuntimeConfig] that contains a view of a [WorkflowInterceptor] as a
 * [ComposeWorkflowInterceptor]. When the returned config is provided to a compose workflow
 * runtime it will intercept all `renderChild` calls through appropriate [WorkflowInterceptor]
 * methods.
 */
@OptIn(WorkflowExperimentalApi::class)
internal fun WorkflowComposableRuntimeConfig(
  interceptor: WorkflowInterceptor,
  parentSession: WorkflowSession?,
  idCounter: IdCounter?,
  runtimeConfig: RuntimeConfig,
  workflowTracer: WorkflowTracer?,
): WorkflowComposableRuntimeConfig {
  val composeInterceptor = if (interceptor === NoopWorkflowInterceptor) null else {
    WorkflowInterceptingComposeInterceptor(
      workflowInterceptor = interceptor,
      rootSession = parentSession,
      runtimeConfig = runtimeConfig,
      workflowTracer = workflowTracer,
      idCounter = idCounter,
    )
  }
  return WorkflowComposableRuntimeConfig(
    runtimeConfig = runtimeConfig,
    workflowTracer = workflowTracer,
    interceptor = composeInterceptor,
  )
}

@OptIn(WorkflowExperimentalApi::class)
private class WorkflowInterceptingComposeInterceptor(
  private val workflowInterceptor: WorkflowInterceptor,
  private val rootSession: WorkflowSession?,
  private val runtimeConfig: RuntimeConfig,
  private val workflowTracer: WorkflowTracer?,
  private val idCounter: IdCounter?,
) : ComposeWorkflowInterceptor {

  /** Cache of [WorkflowInterceptor.composeInterceptor] in case it's computed dynamically. */
  private val composeInterceptor = workflowInterceptor.composeInterceptor

  @Composable
  override fun <PropsT, OutputT, RenderingT> renderWorkflow(
    childWorkflow: Workflow<PropsT, OutputT, RenderingT>,
    props: PropsT,
    onOutput: ((OutputT) -> Unit)?,
    proceed: @Composable (
      childWorkflow: Workflow<PropsT, OutputT, RenderingT>,
      props: PropsT,
      onOutput: ((OutputT) -> Unit)?
    ) -> RenderingT
  ): RenderingT {
    val localParentSession = LocalParentWorkflowSession.current
    val session = remember {
      localParentSession?.createChild(childWorkflow.id(), idCounter)
        ?: rootSession.createChild(childWorkflow.id(), idCounter)
    }
    return withCompositionLocals(LocalParentWorkflowSession provides session) {
      // onSessionStarted needs to only run once per instance of renderChild, but we can't use an
      // effect since it needs to be called before render.
      val sessionScope = rememberCoroutineScope()
      remember {
        workflowInterceptor.onSessionStarted(
          // Not exactly the same scope as passed to onInitialState inside renderChild, but it's a
          // parent so it's probably fine.
          workflowScope = sessionScope,
          session = session,
        )
      }

      val isComposeWorkflow = childWorkflow is ComposeWorkflow<*, *, *>
      if (isComposeWorkflow && composeInterceptor != null) {
        composeInterceptor.renderWorkflow(
          childWorkflow = childWorkflow,
          props = props,
          onOutput = onOutput,
          proceed = proceed
        )
      } else {
        // TODO Most of these calls just allocate simple wrapper StatefulWorkflows, so they don't
        //  need to be remembered unless we find we're doing too many allocations. Need to measure
        //  to find out whether remember or allocating is the slower option.
        @Suppress("UNCHECKED_CAST")
        val statefulWorkflow = if (isComposeWorkflow) {
          // This is the slow/weird case that requires intercepting a ComposeWorkflow as a
          // StatefulWorkflow so we wrap it with a special one that just passes through the
          // produceRendering call.
          _UNSAFE_createComposeWorkflowAsStatefulWorkflow(
            composeWorkflow = childWorkflow as ComposeWorkflow<*, *, *>
          )
        } else {
          // asStatefulWorkflow implementations tend to be cached by the Workflow instance.
          childWorkflow.asStatefulWorkflow()
        } as StatefulWorkflow<PropsT, Any?, OutputT, RenderingT>
        val interceptedWorkflow = workflowInterceptor.intercept(
          workflow = statefulWorkflow,
          workflowSession = session
        )
        proceed(interceptedWorkflow, props, onOutput)
      }
    }
  }

  private fun WorkflowSession?.createChild(
    id: WorkflowNodeId,
    idCounter: IdCounter?,
  ): WorkflowSession = createChild(
    id = id,
    idCounter = idCounter,
    runtimeConfig = runtimeConfig,
    workflowTracer = workflowTracer,
  )
}

private val LocalParentWorkflowSession = staticCompositionLocalOf<WorkflowSession?> { null }

private class ComposeWorkflowSession(
  id: WorkflowNodeId,
  idCounter: IdCounter?,
  override val parent: WorkflowSession?,
  override val runtimeConfig: RuntimeConfig,
  override val workflowTracer: WorkflowTracer?
) : WorkflowSession {
  override val sessionId: Long = idCounter.createId()
  override val identifier: WorkflowIdentifier = id.identifier
  override val renderKey: String = id.name

  override fun toString(): String = workflowSessionToString()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class != other::class) return false

    other as ComposeWorkflowSession

    if (sessionId != other.sessionId) return false
    if (identifier != other.identifier) return false
    if (renderKey != other.renderKey) return false
    if (parent != other.parent) return false
    if (runtimeConfig != other.runtimeConfig) return false
    if (workflowTracer != other.workflowTracer) return false

    return true
  }

  override fun hashCode(): Int {
    var result = sessionId.hashCode()
    result = 31 * result + identifier.hashCode()
    result = 31 * result + renderKey.hashCode()
    result = 31 * result + (parent?.hashCode() ?: 0)
    result = 31 * result + runtimeConfig.hashCode()
    result = 31 * result + (workflowTracer?.hashCode() ?: 0)
    return result
  }
}

internal fun WorkflowSession?.createChild(
  id: WorkflowNodeId,
  idCounter: IdCounter?,
  runtimeConfig: RuntimeConfig,
  workflowTracer: WorkflowTracer?
): WorkflowSession = ComposeWorkflowSession(
  id = id,
  idCounter = idCounter,
  parent = this,
  runtimeConfig = runtimeConfig,
  workflowTracer = workflowTracer
)
