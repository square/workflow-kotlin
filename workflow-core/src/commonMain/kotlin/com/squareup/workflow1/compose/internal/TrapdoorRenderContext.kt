package com.squareup.workflow1.compose.internal

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.Sink
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.compose.renderChild
import com.squareup.workflow1.identifier
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KType

/**
 * Implements [BaseRenderContext] on top of Compose. All methods call [Trapdoor.composeReturning]
 * to run composable code, and they all must start with a call to the [key] function. This puts
 * a "movable group" around all their composable code. When the render method is called, it results
 * in a sequence of calls to [runningSideEffect], [renderChild], [remember], etc, which turn into
 * a series of calls to `key { composableImpl }`. Wrapping everything with [key] ensures that even
 * if the workflow's render method makes these calls in a different order between render passes,
 * the state associated with each call is preserved for whatever identify that call uses (e.g.
 * [renderChild] uses [Workflow.id]).
 */
@OptIn(WorkflowExperimentalApi::class)
public class TrapdoorRenderContext<PropsT, StateT, OutputT>(
  override val runtimeConfig: RuntimeConfig,
  override val workflowTracer: WorkflowTracer?,
  override val actionSink: Sink<WorkflowAction<PropsT, StateT, OutputT>>,
  private val handleChildAction: (WorkflowAction<PropsT, StateT, OutputT>) -> Unit,
  private val trapdoor: Trapdoor,
) : BaseRenderContext<PropsT, StateT, OutputT> {
  override fun runningSideEffect(
    key: String,
    sideEffect: suspend CoroutineScope.() -> Unit
  ) {
    trapdoor.composeReturning {
      key(key) {
        // We use the key function instead of passing key to LaunchedEffect since we want
        // this group to be movable, not just restartable.
        LaunchedEffect(sideEffect, block = sideEffect)
      }
    }
  }

  override fun <ResultT> remember(
    key: String,
    resultType: KType,
    vararg inputs: Any?,
    calculation: () -> ResultT
  ): ResultT = trapdoor.composeReturning {
    // TODO RememberStore also keys off inputs, but i don't think we need to make that part of
    //  the movable group's dataKey?
    key(key, resultType) {
      remember(*inputs, calculation = calculation)
    }
  }

  override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
  ): ChildRenderingT {
    // Create this lambda outside the composable so compose doesn't try to memoize it.
    val onOutput: (ChildOutputT) -> Unit = { output ->
      val action = handler(output)
      handleChildAction(action)
    }

    return trapdoor.composeReturning {
      // renderChild creates a movable group based on child.identifier, but does not include
      // key.
      key(child.identifier, key) {
        renderChild(child, props, onOutput = onOutput)
      }
    }
  }
}
