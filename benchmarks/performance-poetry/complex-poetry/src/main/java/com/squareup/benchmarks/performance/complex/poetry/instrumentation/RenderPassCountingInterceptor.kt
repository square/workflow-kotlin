package com.squareup.benchmarks.performance.complex.poetry.instrumentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.WorkflowInterceptor.RenderContextInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.compose.BaseComposeRenderContext
import com.squareup.workflow1.compose.ComposeWorkflowInterceptor
import com.squareup.workflow1.compose.ComposeWorkflowInterceptor.ComposeRenderContextInterceptor

/**
 * Used to count the number of render passes for a Workflow tree as well as each time that a node
 * is rendered 'fresh' (= state is different than the cached version) or 'stale' (= state is the
 * same as the cached version).
 *
 * This is convenient to use in integration tests that verify that the # of render passes and the
 * ratio of 'fresh' to 'stale' renderings for a scenario are constant.
 */
class RenderPassCountingInterceptor : ComposeWorkflowInterceptor, Resettable {
  val renderEfficiencyTracking = RenderEfficiency()
  private var renderPassStats: RenderStats = RenderStats()
  private val nodeStates: MutableMap<Long, String> = mutableMapOf()

  override fun <P, S, O, R> onRender(
    renderProps: P,
    renderState: S,
    context: BaseRenderContext<P, S, O>,
    proceed: (P, S, RenderContextInterceptor<P, S, O>?) -> R,
    session: WorkflowSession
  ): R {
    val isRoot = before(session, renderState)
    return proceed(renderProps, renderState, null).also {
      after(isRoot)
    }
  }

  @Composable
  override fun <P, S, O, R> Rendering(
    renderProps: P,
    renderState: S,
    context: BaseComposeRenderContext<P, S, O>,
    session: WorkflowSession,
    proceed: @Composable
    (P, S, ComposeRenderContextInterceptor<P, S, O>?) -> R
  ): R {
    val isRoot = remember(session, renderState) {
      before(session, renderState)
    }
    SideEffect {
      after(isRoot)
    }
    return proceed(renderProps, renderState, null)
  }

  private fun <S> before(
    session: WorkflowSession,
    renderState: S
  ): Boolean {
    val isRoot = session.parent == null

    renderPassStats.apply {
      // Update stats for this render pass with this node.
      val renderStateString = renderState.toString()
      val lastState = nodeStates[session.sessionId]
      if (lastState == null) {
        nodesRenderedFresh++
      } else {
        if (lastState.contentEquals(renderStateString)) {
          nodesRenderedStale++
        } else {
          nodesRenderedFresh++
        }
      }
      nodeStates[session.sessionId] = renderStateString
    }
    return isRoot
  }

  private fun after(isRoot: Boolean) {
    if (isRoot) {
      renderEfficiencyTracking.totalRenderPasses += 1
      renderEfficiencyTracking.totalNodeStats += renderPassStats
      renderPassStats = RenderStats()
    }
  }

  /**
   * Reset all the counters.
   */
  override fun reset() {
    renderEfficiencyTracking.reset()
    nodeStates.clear()
  }
}
