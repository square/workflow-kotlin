package com.squareup.workflow1

import com.squareup.workflow1.WorkflowInterceptor.RenderContextInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.internal.getValue
import com.squareup.workflow1.internal.setValue
import com.squareup.workflow1.internal.threadLocalOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope

/**
 * A [WorkflowInterceptor] that just prints all method calls using [log].
 */
public open class SimpleLoggingWorkflowInterceptor : WorkflowInterceptor {
  private var indentLevel by threadLocalOf { 0 }

  override fun onSessionStarted(
    workflowScope: CoroutineScope,
    session: WorkflowSession
  ) {
    invokeSafely("logBeforeMethod") { logBeforeMethod("onInstanceStarted", session) }
  }

  override fun <P, S, O> onSessionCancelled(
    cause: CancellationException?,
    droppedActions: List<WorkflowAction<P, S, O>>,
    session: WorkflowSession
  ) {
    invokeSafely("logAfterMethod") { logAfterMethod("onInstanceStarted", session) }
  }

  override fun <P, S> onInitialState(
    props: P,
    snapshot: Snapshot?,
    workflowScope: CoroutineScope,
    proceed: (P, Snapshot?, CoroutineScope) -> S,
    session: WorkflowSession
  ): S = logMethod("onInitialState", session) {
    proceed(props, snapshot, workflowScope)
  }

  override fun <P, S> onPropsChanged(
    old: P,
    new: P,
    state: S,
    proceed: (P, P, S) -> S,
    session: WorkflowSession
  ): S = logMethod("onPropsChanged", session) {
    proceed(old, new, state)
  }

  override fun <P, S, O, R> onRender(
    renderProps: P,
    renderState: S,
    context: BaseRenderContext<P, S, O>,
    proceed: (P, S, RenderContextInterceptor<P, S, O>?) -> R,
    session: WorkflowSession
  ): R = logMethod("onRender", session) {
    proceed(renderProps, renderState, SimpleLoggingContextInterceptor(session))
  }

  override fun <S> onSnapshotState(
    state: S,
    proceed: (S) -> Snapshot?,
    session: WorkflowSession
  ): Snapshot? = logMethod("onSnapshotState", session) {
    proceed(state)
  }

  private inline fun <T> logMethod(
    name: String,
    session: WorkflowSession,
    vararg extras: Pair<String, Any?>,
    block: () -> T
  ): T {
    val currentIndentLevel = indentLevel
    invokeSafely("logBeforeMethod") { logBeforeMethod(name, session, *extras) }
    indentLevel = currentIndentLevel + 1
    return block().also {
      indentLevel = currentIndentLevel
      invokeSafely("logAfterMethod") { logAfterMethod(name, session, *extras) }
    }
  }

  /**
   * Executes [function], catching and printing any exceptions it throws without rethrowing them.
   */
  private inline fun invokeSafely(
    name: String,
    function: () -> Unit
  ) {
    try {
      function()
    } catch (e: Throwable) {
      val className =
        this::class.simpleName ?: "anonymous " + SimpleLoggingWorkflowInterceptor::class.simpleName
      logError("$className.$name threw exception:\n$e")
    }
  }

  /**
   * Called with descriptions of every event. Default implementation just calls [log].
   */
  protected open fun logBeforeMethod(
    name: String,
    session: WorkflowSession,
    vararg extras: Pair<String, Any?>,
  ) {
    log("START| ${" ".repeat(indentLevel)}${formatLogMessage(name, session, extras)}")
  }

  /**
   * Called with descriptions of every event. Default implementation just calls [log].
   */
  protected open fun logAfterMethod(
    name: String,
    session: WorkflowSession,
    vararg extras: Pair<String, Any?>,
  ) {
    log("  END| ${" ".repeat(indentLevel)}${formatLogMessage(name, session, extras)}")
  }

  /**
   * Called by [logBeforeMethod] and [logAfterMethod] to display a log message.
   */
  protected open fun log(text: String) {
    println(text)
  }

  protected open fun logError(text: String): Unit = println("ERROR: $text")

  private fun formatLogMessage(
    name: String,
    session: WorkflowSession,
    extras: Array<out Pair<String, Any?>>
  ): String = if (extras.isEmpty()) {
    "$name($session)"
  } else {
    "$name($session, ${extras.toMap()})"
  }

  private inner class SimpleLoggingContextInterceptor<P, S, O>(
    private val session: WorkflowSession
  ) : RenderContextInterceptor<P, S, O> {

    override fun onActionSent(
      action: WorkflowAction<P, S, O>,
      proceed: (WorkflowAction<P, S, O>) -> Unit
    ) {
      logMethod("onActionSent", session, "action" to action) {
        proceed(action)
      }
    }

    override fun onRunningSideEffect(
      key: String,
      sideEffect: suspend () -> Unit,
      proceed: (key: String, sideEffect: suspend () -> Unit) -> Unit
    ) {
      proceed(key) {
        logMethod("onSideEffectRunning", session, "key" to key) {
          sideEffect()
        }
      }
    }
  }
}
