package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
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
import com.squareup.workflow1.compose.LocalWorkflowComposableRenderer
import com.squareup.workflow1.compose.WorkflowComposableRenderer
import com.squareup.workflow1.compose.internal.Trapdoor
import com.squareup.workflow1.compose.internal._DO_NOT_USE_invokeComposeWorkflowProduceRendering
import com.squareup.workflow1.identifier
import com.squareup.workflow1.intercept
import com.squareup.workflow1.internal.IdCounter
import com.squareup.workflow1.trace

@WorkflowExperimentalApi
@NonRestartableComposable
@Composable
public fun <R> ProvideWorkflowComposableRenderer(
  runtimeConfig: RuntimeConfig = emptySet(),
  workflowTracer: WorkflowTracer? = null,
  workflowInterceptor: WorkflowInterceptor = NoopWorkflowInterceptor,
  content: @Composable () -> R
): R = ProvideWorkflowComposableRenderer(
  workflowTracer = workflowTracer,
  workflowInterceptor = workflowInterceptor,
  sessionProvider = remember(runtimeConfig, workflowTracer) {
    DefaultSessionProvider(
      runtimeConfig = runtimeConfig,
      workflowTracer = workflowTracer,
      idCounter = IdCounter(),
      parent = null,
    )
  },
  content = content
)

internal class DefaultSessionProvider(
  private val runtimeConfig: RuntimeConfig,
  private val workflowTracer: WorkflowTracer?,
  private val idCounter: IdCounter,
  private val parent: WorkflowSession?,
) : WorkflowSessionProvider {
  override fun createSession(identifier: WorkflowIdentifier): WorkflowSession {
    return object : WorkflowSession {
      override val identifier: WorkflowIdentifier
        get() = identifier
      override val renderKey: String
        get() = ""
      override val sessionId: Long = idCounter.createId()
      override val parent: WorkflowSession?
        get() = this@DefaultSessionProvider.parent
      override val runtimeConfig: RuntimeConfig
        get() = this@DefaultSessionProvider.runtimeConfig
      override val workflowTracer: WorkflowTracer?
        get() = this@DefaultSessionProvider.workflowTracer
    }
  }

  override fun getSessionProviderForChild(parent: WorkflowSession): WorkflowSessionProvider =
    DefaultSessionProvider(
      runtimeConfig = runtimeConfig,
      workflowTracer = workflowTracer,
      idCounter = idCounter,
      parent = parent,
    )
}

@OptIn(WorkflowExperimentalApi::class)
@NonRestartableComposable
@Composable
internal fun <R> ProvideWorkflowComposableRenderer(
  workflowTracer: WorkflowTracer? = null,
  workflowInterceptor: WorkflowInterceptor = NoopWorkflowInterceptor,
  sessionProvider: WorkflowSessionProvider,
  content: @Composable () -> R
): R {
  val renderer = remember(workflowTracer, workflowInterceptor) {
    RuntimeRenderer(
      workflowTracer = workflowTracer,
      workflowInterceptor = workflowInterceptor,
      sessionProvider = sessionProvider,
    )
  }
  return withCompositionLocals(
    LocalWorkflowComposableRenderer provides renderer,
    content = content
  )
}

@OptIn(WorkflowExperimentalApi::class)
private class RuntimeRenderer(
  private val workflowTracer: WorkflowTracer?,
  private val workflowInterceptor: WorkflowInterceptor,
  private val sessionProvider: WorkflowSessionProvider,
) : WorkflowComposableRenderer {

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
    val session = remember { sessionProvider.createSession(childWorkflow.identifier) }
    workflowInterceptor.onSessionStarted(
      // Not exactly the same scope as passed to onInitialState inside renderChild, but it's a
      // parent so it's probably fine.
      workflowScope = rememberCoroutineScope(),
      session = session,
    )

    val statefulWorkflow = Trapdoor.open { trapdoor ->
      @Suppress("UNCHECKED_CAST")
      if (childWorkflow is ComposeWorkflow<*, *, *>) {
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

    // TODO tracing should probably include interceptors, so we'd need to wrap again.

    workflowTracer.trace("RuntimeRenderer.proceed") {
      withCompositionLocals(
        LocalWorkflowSessionProvider provides sessionProvider.getSessionProviderForChild(parent = session)
      ) {
        proceed(interceptedWorkflow, props, onOutput)
      }
    }
  }
}

internal val LocalWorkflowSessionProvider = staticCompositionLocalOf<WorkflowSessionProvider> {
  error("No WorkflowSessionProvider provided")
}

internal interface WorkflowSessionProvider {
  fun createSession(identifier: WorkflowIdentifier): WorkflowSession
  fun getSessionProviderForChild(parent: WorkflowSession): WorkflowSessionProvider
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
