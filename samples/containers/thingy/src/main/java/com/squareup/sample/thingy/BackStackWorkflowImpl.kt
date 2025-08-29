package com.squareup.sample.thingy

import com.squareup.workflow1.SessionWorkflow
import com.squareup.workflow1.Sink
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.action
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.navigation.BackStackScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(WorkflowExperimentalApi::class)
internal class BackStackWorkflowImpl<PropsT, OutputT>(
  private val workflow: BackStackWorkflow<PropsT, OutputT>
) : SessionWorkflow<
  PropsT,
  BackStackState,
  OutputT,
  BackStackScreen<Screen>
  >() {

  override fun initialState(
    props: PropsT,
    snapshot: Snapshot?,
    workflowScope: CoroutineScope
  ): BackStackState {
    val propsFlow = MutableStateFlow(props)
    val backStackFactory = workflow.getBackStackFactory(workflowScope.coroutineContext)

    @Suppress("UNCHECKED_CAST")
    val initialState = BackStackState(
      stack = emptyList(),
      props = propsFlow as MutableStateFlow<Any?>,
      backStackFactory = backStackFactory,
    )

    // TODO move this into the launch call so the scope is correct (use this instead of
    //  workflowScope).
    val scope = BackStackScopeImpl<OutputT>(
      coroutineScope = workflowScope,
    )
    workflowScope.launch(start = CoroutineStart.UNDISPATCHED) {
      with(workflow) {
        scope.runBackStack(propsFlow, emitOutput = { output ->
          @Suppress("UNCHECKED_CAST")
          (scope.actionSink as Sink<WorkflowAction<PropsT, BackStackState, OutputT>>)
            .send(action("emitOutput") { setOutput(output) })
        })
      }
    }

    // TODO gather initial state from the coroutine

    return initialState
  }

  override fun onPropsChanged(
    old: PropsT,
    new: PropsT,
    state: BackStackState
  ): BackStackState = state.apply {
    setProps(new)
    // TODO gather updated state from coroutine
  }

  override fun render(
    renderProps: PropsT,
    renderState: BackStackState,
    context: RenderContext<PropsT, BackStackState, OutputT>
  ): BackStackScreen<Screen> {
    val renderings = renderState.mapFrames { frame ->
      when (frame) {
        is WorkflowFrame<*, *, *, *, *> -> {
          @Suppress("UNCHECKED_CAST")
          (frame as WorkflowFrame<PropsT, OutputT, *, *, *>).renderWorkflow(context)
        }

        is ScreenFrame<*, *> -> {
          frame.screen
        }
      }
    }
    return renderState.createBackStack(renderings)
  }

  override fun snapshotState(state: BackStackState): Snapshot? = null
}
