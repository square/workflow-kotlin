package com.squareup.workflow1

/**
 * A special workflow that renders the entire subtree below it with the Compose runtime. This is a
 * finer-grained way to turn on the [RuntimeConfigOptions.COMPOSE_RUNTIME] flag.
 */
public class ComposeRuntimeSwizzlerWorkflow<P, O, R>(public val child: Workflow<P, O, R>) :
  Workflow<P, O, R> {
  override fun asStatefulWorkflow(): StatefulWorkflow<P, *, O, R> {
    throw UnsupportedOperationException(
      "This workflow is handled directly by the workflow runtime."
    )
  }
}
