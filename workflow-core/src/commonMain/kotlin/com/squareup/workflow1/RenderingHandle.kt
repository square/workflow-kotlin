package com.squareup.workflow1

/**
 * An opaque handle to the rendering of a child workflow that was started by
 * [BaseRenderContext.renderWorkflowIndirectly].
 *
 * Most parent workflows never actually _read_ the renderings of their children – containers like
 * backstacks and modal managers simply embed a child's rendering into their own so that the view
 * layer can find it. Those parents can render such children indirectly, and embed the
 * [RenderingHandle] instead of the child's real rendering. That lets the runtime update the child's
 * rendering without having to re-render any of its ancestors.
 *
 * Because of that, this type is deliberately opaque: it exposes nothing about the child's rendering
 * to the parent, and it is not parameterized on the child's `RenderingT`. Only the view layer knows
 * how to turn a handle back into something displayable – see
 * `com.squareup.workflow1.ui.compose.RenderingHandleScreen`.
 *
 * The runtime returns the _same_ [RenderingHandle] instance for every render pass of the same child
 * workflow session, so handles may be compared by identity and are safe to hold on to for as long
 * as the session is running. A new session – a different workflow type, or the same type rendered
 * with a different key – always gets a new handle.
 *
 * ## Implementations
 *
 * This class is abstract because each workflow runtime provides its own implementation, with its
 * own strategy for notifying the view layer that [currentRendering] has changed. It is not intended
 * to be implemented outside of the Workflow library, and its API may change without notice while
 * it is experimental.
 */
@WorkflowExperimentalApi
public abstract class RenderingHandle {
  /**
   * The most recent rendering of the child workflow session this handle was created for.
   *
   * This is intentionally typed as [Any?] – the whole point of a handle is that the parent workflow
   * can't do anything useful with the child's rendering. It is nullable because
   * [BaseRenderContext.renderChild] allows children to render null, and indirect rendering must
   * accept every child that [BaseRenderContext.renderChild] does.
   *
   * This property is meant to be read only by view layer integrations, which are also responsible
   * for observing changes to it. Runtimes are expected to back it with observable state (e.g.
   * Compose snapshot state), so that a view layer that reads it from a composition is invalidated
   * automatically when it changes.
   */
  public abstract val currentRendering: Any?
}
