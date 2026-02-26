package com.squareup.sample.thingy

import com.squareup.workflow1.SessionWorkflow
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.identifier
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.navigation.BackStackScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
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
    val actionQueue = ActionQueue()
    val dispatcher = BackStackDispatcher()
    val rootJob = Job(parent = workflowScope.coroutineContext.job)
    lateinit var rootNode: BackStackNode

    fun launchRootNode() {
      rootNode.launch {
        with(workflow) {
          rootNode.runBackStack(
            props = propsFlow,
            emitOutput = { output ->
              // Launch with dispatcher to trigger onIdle and actually enqueue an action.
              workflowScope.launch(dispatcher) {
                actionQueue.enqueueOutputEmission(output)
              }
            }
          )
        }
      }
    }

    rootNode = BackStackNode(
      actionQueue = actionQueue,
      key = workflow.identifier.toString(),
      parentJob = workflowScope.coroutineContext.job,
      dispatcher = dispatcher,
      onCancel = {
        rootJob.cancelChildren()
        launchRootNode()
      },
      onEmitOutputAction = {
        TODO("how to trigger more actions?")
      }
    )

    dispatcher.runThenDispatchImmediately {
      launchRootNode()
    }

    val initialStack = buildList {
      actionQueue.consumeActionsToStack(this)
    }

    @Suppress("UNCHECKED_CAST")
    return BackStackState(
      stack = initialStack,
      props = propsFlow as MutableStateFlow<Any?>,
      backStackFactory = backStackFactory,
      actionQueue = actionQueue,
      dispatcher = dispatcher,
    )
  }

  override fun onPropsChanged(
    old: PropsT,
    new: PropsT,
    state: BackStackState
  ): BackStackState = state.setProps(new)

  override fun render(
    renderProps: PropsT,
    renderState: BackStackState,
    context: RenderContext<PropsT, BackStackState, OutputT>
  ): BackStackScreen<Screen> {
    @Suppress("UNCHECKED_CAST")
    return renderState.renderOn(context as RenderContext<Any?, BackStackState, Any?>)
  }

  override fun snapshotState(state: BackStackState): Snapshot? = null
}
