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
   *
   * Note that this is *not effective* when using an immediate or unconfined dispatcher as the
   * handling of an action happens synchronously after the coroutine sending it into the sink
   * is resumed. This means it never dispatches multiple coroutines that send actions into the sink
   * before handling the first, so there is never multiple actions to apply!
   *
   * Note further that [WORK_STEALING_DISPATCHER] does not fix this for immediate dispatchers. In
   * order to use this optimization, a non-immediate dispatcher must be used. If using a non-
   * immediate dispatcher, we recommend that you ensure that the Workflow runtime completes all
   * known updates before the next 'frame.' This can be done using a dispatcher that is integrated
   * with the frame clock on your platform. E.g., on Android we recommend using Compose UI's
   * `AndroidUiDispatcher.Main` in order to access these optimizations.
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
   * Wrap the dispatcher passed to the runtime with a special dispatcher that can be advanced
   * explicitly, to allow any tasks scheduled by the workflow runtime to run before certain phases.
   */
  @WorkflowExperimentalRuntime
  WORK_STEALING_DISPATCHER,

  /**
   * If we have more actions to process that are queued on nodes not affected by the last
   * action application, then we will continue to process those actions before another render
   * pass.
   *
   * Note that this is *not effective* when using an immediate or unconfined dispatcher as the
   * handling of an action happens synchronously after the coroutine sending it into the sink
   * is resumed. This means it never dispatches multiple coroutines that send actions into the sink
   * before handling the first, so there is never multiple actions to apply!
   *
   * Note further that [WORK_STEALING_DISPATCHER] does not fix this for immediate dispatchers. In
   * order to use this optimization, a non-immediate dispatcher must be used. If using a non-
   * immediate dispatcher, we recommend that you ensure that the Workflow runtime completes all
   * known updates before the next 'frame.' This can be done using a dispatcher that is integrated
   * with the frame clock on your platform. E.g., on Android we recommend using Compose UI's
   * `AndroidUiDispatcher.Main` in order to access these optimizations.
   */
  @WorkflowExperimentalRuntime
  DRAIN_EXCLUSIVE_ACTIONS,

  /**
   * Replaces the traditional Workflow runtime with the Compose runtime.
   */
  @WorkflowExperimentalRuntime
  COMPOSE_RUNTIME,
  ;

  public companion object {
    /**
     * Baseline configuration where we render for each action and always pass the rendering to
     * the view layer.
     */
    public val RENDER_PER_ACTION: RuntimeConfig = emptySet()

    public val DEFAULT_CONFIG: RuntimeConfig = RENDER_PER_ACTION

    /**
     * Configuration that enables every [RuntimeConfig] option for the traditional (non-Compose)
     * runtime.
     */
    @WorkflowExperimentalRuntime
    public val ALL: RuntimeConfig = entries.toSet() - COMPOSE_RUNTIME

    /**
     * Enum of all reasonable config options. Used especially for parameterized testing.
     */
    @WorkflowExperimentalRuntime
    enum class RuntimeOptions(
      val runtimeConfig: RuntimeConfig
    ) {
      NONE(RENDER_PER_ACTION),
      RENDER_ONLY(setOf(RENDER_ONLY_WHEN_STATE_CHANGES)),
      PARTIAL_TREE(setOf(PARTIAL_TREE_RENDERING)),
      CONFLATE(setOf(CONFLATE_STALE_RENDERINGS)),
      STABLE(setOf(STABLE_EVENT_HANDLERS)),
      DRAIN(setOf(DRAIN_EXCLUSIVE_ACTIONS)),
      STEAL(setOf(WORK_STEALING_DISPATCHER)),
      RENDER_ONLY_PARTIAL_TREE(setOf(RENDER_ONLY_WHEN_STATE_CHANGES, PARTIAL_TREE_RENDERING)),
      RENDER_ONLY_CONFLATE(setOf(RENDER_ONLY_WHEN_STATE_CHANGES, CONFLATE_STALE_RENDERINGS)),
      RENDER_ONLY_STABLE(setOf(RENDER_ONLY_WHEN_STATE_CHANGES, STABLE_EVENT_HANDLERS)),
      RENDER_ONLY_DRAIN(setOf(RENDER_ONLY_WHEN_STATE_CHANGES, DRAIN_EXCLUSIVE_ACTIONS)),
      RENDER_ONLY_STEAL(setOf(RENDER_ONLY_WHEN_STATE_CHANGES, WORK_STEALING_DISPATCHER)),
      PARTIAL_TREE_CONFLATE(setOf(PARTIAL_TREE_RENDERING, CONFLATE_STALE_RENDERINGS)),
      PARTIAL_TREE_STABLE(setOf(PARTIAL_TREE_RENDERING, STABLE_EVENT_HANDLERS)),
      PARTIAL_TREE_DRAIN(setOf(PARTIAL_TREE_RENDERING, DRAIN_EXCLUSIVE_ACTIONS)),
      PARTIAL_TREE_STEAL(setOf(PARTIAL_TREE_RENDERING, WORK_STEALING_DISPATCHER)),
      CONFLATE_STABLE(setOf(CONFLATE_STALE_RENDERINGS, STABLE_EVENT_HANDLERS)),
      CONFLATE_DRAIN(setOf(CONFLATE_STALE_RENDERINGS, DRAIN_EXCLUSIVE_ACTIONS)),
      CONFLATE_STEAL(setOf(CONFLATE_STALE_RENDERINGS, WORK_STEALING_DISPATCHER)),
      STABLE_DRAIN(setOf(STABLE_EVENT_HANDLERS, DRAIN_EXCLUSIVE_ACTIONS)),
      STABLE_STEAL(setOf(STABLE_EVENT_HANDLERS, WORK_STEALING_DISPATCHER)),
      DRAIN_STEAL(setOf(DRAIN_EXCLUSIVE_ACTIONS, WORK_STEALING_DISPATCHER)),
      RENDER_ONLY_PARTIAL_TREE_CONFLATE(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          PARTIAL_TREE_RENDERING,
          CONFLATE_STALE_RENDERINGS
        )
      ),
      RENDER_ONLY_PARTIAL_TREE_STABLE(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          PARTIAL_TREE_RENDERING,
          STABLE_EVENT_HANDLERS
        )
      ),
      RENDER_ONLY_PARTIAL_TREE_DRAIN(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          PARTIAL_TREE_RENDERING,
          DRAIN_EXCLUSIVE_ACTIONS
        )
      ),
      RENDER_ONLY_PARTIAL_TREE_STEAL(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          PARTIAL_TREE_RENDERING,
          WORK_STEALING_DISPATCHER
        )
      ),
      RENDER_ONLY_CONFLATE_STABLE(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          CONFLATE_STALE_RENDERINGS,
          STABLE_EVENT_HANDLERS
        )
      ),
      RENDER_ONLY_CONFLATE_DRAIN(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          CONFLATE_STALE_RENDERINGS,
          DRAIN_EXCLUSIVE_ACTIONS
        )
      ),
      RENDER_ONLY_CONFLATE_STEAL(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          CONFLATE_STALE_RENDERINGS,
          WORK_STEALING_DISPATCHER
        )
      ),
      RENDER_ONLY_STABLE_DRAIN(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          STABLE_EVENT_HANDLERS,
          DRAIN_EXCLUSIVE_ACTIONS
        )
      ),
      RENDER_ONLY_STABLE_STEAL(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          STABLE_EVENT_HANDLERS,
          WORK_STEALING_DISPATCHER
        )
      ),
      RENDER_ONLY_DRAIN_STEAL(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          DRAIN_EXCLUSIVE_ACTIONS,
          WORK_STEALING_DISPATCHER
        )
      ),
      PARTIAL_TREE_CONFLATE_STABLE(
        setOf(
          PARTIAL_TREE_RENDERING,
          CONFLATE_STALE_RENDERINGS,
          STABLE_EVENT_HANDLERS
        )
      ),
      PARTIAL_TREE_CONFLATE_DRAIN(
        setOf(
          PARTIAL_TREE_RENDERING,
          CONFLATE_STALE_RENDERINGS,
          DRAIN_EXCLUSIVE_ACTIONS
        )
      ),
      PARTIAL_TREE_CONFLATE_STEAL(
        setOf(
          PARTIAL_TREE_RENDERING,
          CONFLATE_STALE_RENDERINGS,
          WORK_STEALING_DISPATCHER
        )
      ),
      PARTIAL_TREE_STABLE_DRAIN(
        setOf(
          PARTIAL_TREE_RENDERING,
          STABLE_EVENT_HANDLERS,
          DRAIN_EXCLUSIVE_ACTIONS
        )
      ),
      PARTIAL_TREE_STABLE_STEAL(
        setOf(
          PARTIAL_TREE_RENDERING,
          STABLE_EVENT_HANDLERS,
          WORK_STEALING_DISPATCHER
        )
      ),
      PARTIAL_TREE_DRAIN_STEAL(
        setOf(
          PARTIAL_TREE_RENDERING,
          DRAIN_EXCLUSIVE_ACTIONS,
          WORK_STEALING_DISPATCHER
        )
      ),
      CONFLATE_STABLE_DRAIN(
        setOf(
          CONFLATE_STALE_RENDERINGS,
          STABLE_EVENT_HANDLERS,
          DRAIN_EXCLUSIVE_ACTIONS
        )
      ),
      CONFLATE_STABLE_STEAL(
        setOf(
          CONFLATE_STALE_RENDERINGS,
          STABLE_EVENT_HANDLERS,
          WORK_STEALING_DISPATCHER
        )
      ),
      CONFLATE_DRAIN_STEAL(
        setOf(
          CONFLATE_STALE_RENDERINGS,
          DRAIN_EXCLUSIVE_ACTIONS,
          WORK_STEALING_DISPATCHER
        )
      ),
      STABLE_DRAIN_STEAL(
        setOf(
          STABLE_EVENT_HANDLERS,
          DRAIN_EXCLUSIVE_ACTIONS,
          WORK_STEALING_DISPATCHER
        )
      ),
      RENDER_ONLY_PARTIAL_TREE_CONFLATE_STABLE(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          PARTIAL_TREE_RENDERING,
          CONFLATE_STALE_RENDERINGS,
          STABLE_EVENT_HANDLERS
        )
      ),
      RENDER_ONLY_PARTIAL_TREE_CONFLATE_DRAIN(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          PARTIAL_TREE_RENDERING,
          CONFLATE_STALE_RENDERINGS,
          DRAIN_EXCLUSIVE_ACTIONS
        )
      ),
      RENDER_ONLY_PARTIAL_TREE_CONFLATE_STEAL(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          PARTIAL_TREE_RENDERING,
          CONFLATE_STALE_RENDERINGS,
          WORK_STEALING_DISPATCHER
        )
      ),
      RENDER_ONLY_PARTIAL_TREE_STABLE_DRAIN(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          PARTIAL_TREE_RENDERING,
          STABLE_EVENT_HANDLERS,
          DRAIN_EXCLUSIVE_ACTIONS
        )
      ),
      RENDER_ONLY_PARTIAL_TREE_STABLE_STEAL(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          PARTIAL_TREE_RENDERING,
          STABLE_EVENT_HANDLERS,
          WORK_STEALING_DISPATCHER
        )
      ),
      RENDER_ONLY_PARTIAL_TREE_DRAIN_STEAL(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          PARTIAL_TREE_RENDERING,
          DRAIN_EXCLUSIVE_ACTIONS,
          WORK_STEALING_DISPATCHER
        )
      ),
      RENDER_ONLY_CONFLATE_STABLE_DRAIN(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          CONFLATE_STALE_RENDERINGS,
          STABLE_EVENT_HANDLERS,
          DRAIN_EXCLUSIVE_ACTIONS
        )
      ),
      RENDER_ONLY_CONFLATE_STABLE_STEAL(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          CONFLATE_STALE_RENDERINGS,
          STABLE_EVENT_HANDLERS,
          WORK_STEALING_DISPATCHER
        )
      ),
      RENDER_ONLY_CONFLATE_DRAIN_STEAL(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          CONFLATE_STALE_RENDERINGS,
          DRAIN_EXCLUSIVE_ACTIONS,
          WORK_STEALING_DISPATCHER
        )
      ),
      RENDER_ONLY_STABLE_DRAIN_STEAL(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          STABLE_EVENT_HANDLERS,
          DRAIN_EXCLUSIVE_ACTIONS,
          WORK_STEALING_DISPATCHER
        )
      ),
      PARTIAL_TREE_CONFLATE_STABLE_DRAIN(
        setOf(
          PARTIAL_TREE_RENDERING,
          CONFLATE_STALE_RENDERINGS,
          STABLE_EVENT_HANDLERS,
          DRAIN_EXCLUSIVE_ACTIONS
        )
      ),
      PARTIAL_TREE_CONFLATE_STABLE_STEAL(
        setOf(
          PARTIAL_TREE_RENDERING,
          CONFLATE_STALE_RENDERINGS,
          STABLE_EVENT_HANDLERS,
          WORK_STEALING_DISPATCHER
        )
      ),
      PARTIAL_TREE_CONFLATE_DRAIN_STEAL(
        setOf(
          PARTIAL_TREE_RENDERING,
          CONFLATE_STALE_RENDERINGS,
          DRAIN_EXCLUSIVE_ACTIONS,
          WORK_STEALING_DISPATCHER
        )
      ),
      PARTIAL_TREE_STABLE_DRAIN_STEAL(
        setOf(
          PARTIAL_TREE_RENDERING,
          STABLE_EVENT_HANDLERS,
          DRAIN_EXCLUSIVE_ACTIONS,
          WORK_STEALING_DISPATCHER
        )
      ),
      CONFLATE_STABLE_DRAIN_STEAL(
        setOf(
          CONFLATE_STALE_RENDERINGS,
          STABLE_EVENT_HANDLERS,
          DRAIN_EXCLUSIVE_ACTIONS,
          WORK_STEALING_DISPATCHER
        )
      ),
      RENDER_ONLY_PARTIAL_TREE_CONFLATE_STABLE_DRAIN(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          PARTIAL_TREE_RENDERING,
          CONFLATE_STALE_RENDERINGS,
          STABLE_EVENT_HANDLERS,
          DRAIN_EXCLUSIVE_ACTIONS
        )
      ),
      RENDER_ONLY_PARTIAL_TREE_CONFLATE_STABLE_STEAL(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          PARTIAL_TREE_RENDERING,
          CONFLATE_STALE_RENDERINGS,
          STABLE_EVENT_HANDLERS,
          WORK_STEALING_DISPATCHER
        )
      ),
      RENDER_ONLY_PARTIAL_TREE_CONFLATE_DRAIN_STEAL(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          PARTIAL_TREE_RENDERING,
          CONFLATE_STALE_RENDERINGS,
          DRAIN_EXCLUSIVE_ACTIONS,
          WORK_STEALING_DISPATCHER
        )
      ),
      RENDER_ONLY_PARTIAL_TREE_STABLE_DRAIN_STEAL(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          PARTIAL_TREE_RENDERING,
          STABLE_EVENT_HANDLERS,
          DRAIN_EXCLUSIVE_ACTIONS,
          WORK_STEALING_DISPATCHER
        )
      ),
      RENDER_ONLY_CONFLATE_STABLE_DRAIN_STEAL(
        setOf(
          RENDER_ONLY_WHEN_STATE_CHANGES,
          CONFLATE_STALE_RENDERINGS,
          STABLE_EVENT_HANDLERS,
          DRAIN_EXCLUSIVE_ACTIONS,
          WORK_STEALING_DISPATCHER
        )
      ),
      PARTIAL_TREE_CONFLATE_STABLE_DRAIN_STEAL(
        setOf(
          PARTIAL_TREE_RENDERING,
          CONFLATE_STALE_RENDERINGS,
          STABLE_EVENT_HANDLERS,
          DRAIN_EXCLUSIVE_ACTIONS,
          WORK_STEALING_DISPATCHER
        )
      ),

      COMPOSE_RUNTIME_ONLY(setOf(RuntimeConfigOptions.COMPOSE_RUNTIME)),

      /**
       * Always contains all [RuntimeConfigOptions]. Other values in this enum may happen to contain
       * the same set at some point in time, but this one will also always be updated to include new
       * ones as they're added.
       */
      ALL(RuntimeConfigOptions.ALL)
    }
  }
}
