package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.compose.ComposeWorkflow
import com.squareup.workflow1.compose.ComposeWorkflowInterceptor
import com.squareup.workflow1.compose.LocalWorkflowComposableRuntimeConfig
import com.squareup.workflow1.compose.internal.Trapdoor
import com.squareup.workflow1.compose.internal._DO_NOT_USE_invokeComposeWorkflowProduceRendering
import com.squareup.workflow1.identifier
import com.squareup.workflow1.intercept
import com.squareup.workflow1.internal.IdCounter
import com.squareup.workflow1.internal.WorkflowNodeId
import com.squareup.workflow1.internal.id

private val LocalParentWorkflowSession = staticCompositionLocalOf<WorkflowSession?> { null }

private class ComposeWorkflowSession(
  id: WorkflowNodeId,
  idCounter: IdCounter,
  override val parent: WorkflowSession?,
  override val runtimeConfig: RuntimeConfig,
  override val workflowTracer: WorkflowTracer?
) : WorkflowSession {
  override val sessionId: Long = idCounter.createId()
  override val identifier: WorkflowIdentifier = id.identifier
  override val renderKey: String = id.name

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
  idCounter: IdCounter,
  runtimeConfig: RuntimeConfig,
  workflowTracer: WorkflowTracer?
): WorkflowSession = ComposeWorkflowSession(
  id = id,
  idCounter = idCounter,
  parent = this,
  runtimeConfig = runtimeConfig,
  workflowTracer = workflowTracer
)

@OptIn(WorkflowExperimentalApi::class)
@ReadOnlyComposable
@Composable
private fun WorkflowSession?.createChild(
  id: WorkflowNodeId,
  idCounter: IdCounter,
): WorkflowSession = createChild(
  id = id,
  idCounter = idCounter,
  runtimeConfig = this?.runtimeConfig
    ?: LocalWorkflowComposableRuntimeConfig.current.runtimeConfig,
  workflowTracer = this?.workflowTracer
    ?: LocalWorkflowComposableRuntimeConfig.current.workflowTracer,
)

@OptIn(WorkflowExperimentalApi::class)
internal fun WorkflowInterceptor.asComposeWorkflowInterceptor(
  parentSession: WorkflowSession?,
  idCounter: IdCounter,
): ComposeWorkflowInterceptor? =
  if (this === NoopWorkflowInterceptor) null else {
    WorkflowInterceptingComposeInterceptor(this, parentSession, idCounter)
  }

@OptIn(WorkflowExperimentalApi::class)
private class WorkflowInterceptingComposeInterceptor(
  private val workflowInterceptor: WorkflowInterceptor,
  private val parentSession: WorkflowSession?,
  private val idCounter: IdCounter,
) : ComposeWorkflowInterceptor {

  @Composable
  override fun <PropsT, OutputT, RenderingT> renderChild(
    childWorkflow: Workflow<PropsT, OutputT, RenderingT>,
    props: PropsT,
    onOutput: ((OutputT) -> Unit)?,
    proceed: @Composable (
      childWorkflow: Workflow<PropsT, OutputT, RenderingT>,
      props: PropsT,
      onOutput: ((OutputT) -> Unit)?
    ) -> RenderingT
  ): RenderingT = key(childWorkflow.identifier) {
    val session = LocalParentWorkflowSession.current?.createChild(childWorkflow.id(), idCounter)
      ?: parentSession.createChild(childWorkflow.id(), idCounter)

    workflowInterceptor.onSessionStarted(
      // Not exactly the same scope as passed to onInitialState inside renderChild, but it's a
      // parent so it's probably fine.
      workflowScope = rememberCoroutineScope(),
      session = session,
    )

    val statefulWorkflow = Trapdoor.open { trapdoor ->
      @Suppress("UNCHECKED_CAST")
      if (childWorkflow is ComposeWorkflow<*, *, *>) {
        // The WorkflowInterceptor API requires StatefulWorkflows, so shove it in there.
        (childWorkflow as ComposeWorkflow<PropsT, OutputT, RenderingT>)
          .asStatefulWorkflow(trapdoor, onOutput)
      } else {
        childWorkflow.asStatefulWorkflow()
      } as StatefulWorkflow<PropsT, Any?, OutputT, RenderingT>
    }

    val interceptedWorkflow = workflowInterceptor.intercept(
      workflow = statefulWorkflow,
      workflowSession = session
    )

    withCompositionLocals(LocalParentWorkflowSession provides session) {
      proceed(interceptedWorkflow, props, onOutput)
    }
  }
}

@OptIn(WorkflowExperimentalApi::class)
private fun <PropsT, OutputT, RenderingT>
  ComposeWorkflow<PropsT, OutputT, RenderingT>.asStatefulWorkflow(
  trapdoor: Trapdoor,
  onOutput: ((OutputT) -> Unit)?,
) = object : StatefulWorkflow<PropsT, Unit, OutputT, RenderingT>() {
  override fun initialState(
    props: PropsT,
    snapshot: Snapshot?
  ) {
  }

  override fun render(
    renderProps: PropsT,
    renderState: Unit,
    context: RenderContext<PropsT, Unit, OutputT>
  ): RenderingT {
    return trapdoor.composeReturning {
      _DO_NOT_USE_invokeComposeWorkflowProduceRendering(
        workflow = this@asStatefulWorkflow,
        props = renderProps,
        emitOutput = onOutput ?: {}
      )
    }
  }

  override fun snapshotState(state: Unit): Snapshot? = null
}
