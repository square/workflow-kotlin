package com.squareup.workflow.internal

import com.squareup.workflow.diagnostic.WorkflowDiagnosticListener
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * TODO write documentation
 *
 * @param workerContext [CoroutineContext] that is appended to the end of the context used to launch
 * worker coroutines. This context will override anything from the workflow's scope and any other
 * hard-coded values added to worker contexts. It must not contain a [Job] element (it would violate
 * structured concurrency).
 * @param diagnosticListener
 */
internal class WorkflowRuntime(
  val workerContext: CoroutineContext = EmptyCoroutineContext,
  val diagnosticListener: WorkflowDiagnosticListener? = null
) {
  private var nextDiagnosticId = 0L

  fun createDiagnosticId(parentId: DiagnosticId = DiagnosticId.ROOT): DiagnosticId =
    parentId.createChildId(nextDiagnosticId++)
}
