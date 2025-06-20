package com.squareup.workflow1

import kotlin.RequiresOptIn.Level.ERROR
import kotlin.annotation.AnnotationRetention.BINARY

/**
 * This is used to mark any experimental runtimes.
 */
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.TYPEALIAS
)
@MustBeDocumented
@Retention(value = BINARY)
@RequiresOptIn(level = ERROR)
public annotation class WorkflowExperimentalRuntime

public typealias RuntimeConfig = Set<RuntimeConfigOptions>

/**
 * A specification of the possible Workflow Runtime options.
 */
public enum class RuntimeConfigOptions {
  /**
   * If state has not changed from an action cascade (as determined via `equals()`),
   * do not re-render. For example, when this is selected and `noAction()` is enqueued,
   * the current `render()` pass will short circuit and no rendering will be posted
   * through the `StateFlow` returned from `renderWorkflowIn()`.
   *
   * This has been mostly proven out. However, be careful if you have any non-Workflow
   * code you integrate with that depends on the Workflow tree re-rendering to pick up
   * changes from its equivalent 'view model.' You should change some kind of Workflow
   * state when updating that external code if you want Workflow to pick up the change
   * and render again.
   */
  @WorkflowExperimentalRuntime
  RENDER_ONLY_WHEN_STATE_CHANGES,

  /**
   * Only re-render each active Workflow node if:
   * 1. Its own state changed, OR
   * 2. One of its descendant's state has changed.
   *
   * Otherwise return the cached rendering (as there is no way it could have changed).
   *
   * Note however that you must be careful using this because there may be external
   * state that your Workflow draws in and re-renders, and if that is not explicitly
   * tracked within that Workflow's state then the Workflow will not re-render.
   * In this case  make sure that the implicit state is tracked within the Workflow's
   * `StateT` in some way, even if only via a hash token.
   */
  @WorkflowExperimentalRuntime
  PARTIAL_TREE_RENDERING,

  /**
   * If we have more actions to process, do so before passing the rendering to the UI layer.
   */
  @WorkflowExperimentalRuntime
  CONFLATE_STALE_RENDERINGS,

  /**
   * Changes the default value of the `remember: Boolean?` parameter of
   * `RenderContext.eventHandler` calls from `false` to `true`.
   */
  @WorkflowExperimentalRuntime
  STABLE_EVENT_HANDLERS,

  /**
   * If we have more actions to process that are queued on nodes not affected by the last
   * action application, then we will continue to process those actions before another render
   * pass.
   */
  @WorkflowExperimentalRuntime
  DRAIN_EXCLUSIVE_ACTIONS,
  ;

  public companion object {
    /**
     * Baseline configuration where we render for each action and always pass the rendering to
     * the view layer.
     */
    public val RENDER_PER_ACTION: RuntimeConfig = emptySet()

    public val DEFAULT_CONFIG: RuntimeConfig = RENDER_PER_ACTION

    /**
     * Configuration that enables every [RuntimeConfig] option.
     */
    @WorkflowExperimentalRuntime
    public val ALL: RuntimeConfig = entries.toSet()

    /**
     * Enum of all reasonable config options. Used especially for parameterized testing.
     */
    @WorkflowExperimentalRuntime
    enum class RuntimeOptions(
      val runtimeConfig: RuntimeConfig
    ) {
      DEFAULT(RENDER_PER_ACTION),
      RENDER_ONLY(setOf(RENDER_ONLY_WHEN_STATE_CHANGES)),
      CONFLATE(setOf(CONFLATE_STALE_RENDERINGS)),
      STABLE(setOf(STABLE_EVENT_HANDLERS)),

      DEA(setOf(DRAIN_EXCLUSIVE_ACTIONS)),
      RENDER_ONLY_CONFLATE(setOf(RENDER_ONLY_WHEN_STATE_CHANGES, CONFLATE_STALE_RENDERINGS)),
      RENDER_ONLY_PARTIAL(setOf(RENDER_ONLY_WHEN_STATE_CHANGES, PARTIAL_TREE_RENDERING)),

      RENDER_ONLY_DEA(setOf(RENDER_ONLY_WHEN_STATE_CHANGES, DRAIN_EXCLUSIVE_ACTIONS)),
      RENDER_ONLY_CONFLATE_STABLE(
        setOf(RENDER_ONLY_WHEN_STATE_CHANGES, CONFLATE_STALE_RENDERINGS, STABLE_EVENT_HANDLERS)
      ),
      RENDER_ONLY_CONFLATE_PARTIAL(
        setOf(RENDER_ONLY_WHEN_STATE_CHANGES, CONFLATE_STALE_RENDERINGS, PARTIAL_TREE_RENDERING)
      ),

      RENDER_ONLY_CONFLATE_DEA(
        setOf(RENDER_ONLY_WHEN_STATE_CHANGES, CONFLATE_STALE_RENDERINGS, DRAIN_EXCLUSIVE_ACTIONS)
      ),
      RENDER_ONLY_PARTIAL_STABLE(
        setOf(RENDER_ONLY_WHEN_STATE_CHANGES, PARTIAL_TREE_RENDERING, STABLE_EVENT_HANDLERS)
      ),

      RENDER_ONLY_PARTIAL_DEA(
        setOf(RENDER_ONLY_WHEN_STATE_CHANGES, PARTIAL_TREE_RENDERING, DRAIN_EXCLUSIVE_ACTIONS)
      ),
      RENDER_ONLY_DEA_STABLE(
        setOf(RENDER_ONLY_WHEN_STATE_CHANGES, DRAIN_EXCLUSIVE_ACTIONS, STABLE_EVENT_HANDLERS)
      ),
      RENDER_ONLY_CONFLATE_PARTIAL_STABLE(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          CONFLATE_STALE_RENDERINGS,
          PARTIAL_TREE_RENDERING,
          STABLE_EVENT_HANDLERS,
        )
      ),
      RENDER_ONLY_CONFLATE_PARTIAL_DEA(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          CONFLATE_STALE_RENDERINGS,
          PARTIAL_TREE_RENDERING,
          DRAIN_EXCLUSIVE_ACTIONS,
        )
      ),
      RENDER_ONLY_PARTIAL_STABLE_DEA(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          PARTIAL_TREE_RENDERING,
          STABLE_EVENT_HANDLERS,
          DRAIN_EXCLUSIVE_ACTIONS,
        )
      ),
      RENDER_ONLY_CONFLATE_PARTIAL_STABLE_DEA(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          CONFLATE_STALE_RENDERINGS,
          PARTIAL_TREE_RENDERING,
          STABLE_EVENT_HANDLERS,
          DRAIN_EXCLUSIVE_ACTIONS,
        )
      ),
    }
  }
}
