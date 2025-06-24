package com.squareup.benchmarks.performance.complex.poetry.instrumentation

import androidx.compose.runtime.Composable
import androidx.tracing.Trace
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoemWorkflow
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoemsBrowserWorkflow
import com.squareup.benchmarks.performance.complex.poetry.instrumentation.PerformanceTracingInterceptor.Companion.NODES_TO_TRACE
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.RenderContextInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.workflowIdentifier

/**
 * Used to instrumentation [Trace] sections for each render pass of the Workflow tree as well
 * as a specific list of nodes specified by [NODES_TO_TRACE].
 *
 * This can be combined with a [androidx.benchmark.macro.TraceSectionMetric] in a benchmark to
 * conveniently print out results.
 */
class PerformanceTracingInterceptor(
  private val sample: Boolean = false
) : WorkflowInterceptor, Resettable {
  private var totalRenderPasses = 0

  override fun <P, S, O, R> onRender(
    renderProps: P,
    renderState: S,
    context: BaseRenderContext<P, S, O>,
    proceed: (P, S, RenderContextInterceptor<P, S, O>?) -> R,
    session: WorkflowSession
  ): R = traceRender(session) {
    proceed(renderProps, renderState, null)
  }

  @OptIn(WorkflowExperimentalApi::class)
  @Composable
  override fun <P, O, R> onRenderComposeWorkflow(
    renderProps: P,
    emitOutput: (O) -> Unit,
    proceed: @Composable (P, (O) -> Unit) -> R,
    session: WorkflowSession
  ): R = traceRender(session) {
    proceed(renderProps, emitOutput)
  }

  private inline fun <R> traceRender(
    session: WorkflowSession,
    render: () -> R
  ): R {
    val isRoot = session.parent == null
    val traceIdIndex = NODES_TO_TRACE.indexOfFirst { it.second == session.identifier }
    val renderPassMarker = totalRenderPasses.toString()
      .padStart(RENDER_PASS_DIGITS, '0')

    if (isRoot && (!sample || totalRenderPasses.mod(2) == 0)) {
      val sectionName = "${renderPassMarker}_Render_Pass_"
      Trace.beginSection(sectionName)
    }

    if (traceIdIndex > -1 && !sample) {
      // Trace section for specially tracked WorkflowIdentifiers.
      val sectionName = "${renderPassMarker}_Render_Pass_Node_" +
        "${NODES_TO_TRACE[traceIdIndex].first}_"
      Trace.beginSection(sectionName)
    }

    return render().also {
      if (traceIdIndex > -1 && !sample) {
        Trace.endSection()
      }
      if (isRoot) {
        if (!sample || totalRenderPasses.mod(2) == 0) {
          Trace.endSection()
        }
        totalRenderPasses++
      }
    }
  }

  override fun reset() {
    totalRenderPasses = 0
  }

  companion object {
    const val RENDER_PASS_DIGITS = 3

    // Particular Workflow nodes that we want to trace rendering of.
    val NODES_TO_TRACE = arrayOf(
      "PerformancePoemWorkflow" to PerformancePoemWorkflow::class.workflowIdentifier,
      "PerformancePoemsBrowserWorkflow" to PerformancePoemsBrowserWorkflow::class.workflowIdentifier
    )
  }
}
