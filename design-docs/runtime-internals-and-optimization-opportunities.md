# Workflow Runtime Internals And Optimization Opportunities

Date: 2026-03-11

## Goal

Document how the `workflow-runtime` internals currently work, then identify practical optimization projects that can be prototyped and benchmarked.

## Sources Used

- Slack discussion on `ActiveStagingList` identity checks and `forEachStaging` hot paths (`C07UHTZKAPJ`, thread `1741210422.360469`, Mar 5-6 2025).
- Runtime internals in `workflow-runtime`.
- Event handler + `remember` implementation in `workflow-core`.
- Existing tracing and benchmark infrastructure in `workflow-tracing` and `benchmarks`.

## Runtime Architecture Today

### 1. Entry Point And Loop Shape

The low-level runtime entry point is `renderWorkflowIn` in `workflow-runtime/src/commonMain/kotlin/com/squareup/workflow1/RenderWorkflow.kt`.

- It optionally wraps the scope dispatcher in `WorkStealingDispatcher` when `WORK_STEALING_DISPATCHER` is enabled (`RenderWorkflow.kt:154-162`).
- It creates a `WorkflowRunner` and performs the first render pass synchronously before launching the loop coroutine (`RenderWorkflow.kt:163-191`).
- The runtime loop then repeats:
  - wait for action or props update,
  - optionally drain exclusive actions,
  - render,
  - optionally conflate stale renderings,
  - emit rendering and output (`RenderWorkflow.kt:233-317`).

### 2. WorkflowRunner Responsibilities

`WorkflowRunner` coordinates props updates, tree action waiting, and render/snapshot passes (`workflow-runtime/src/commonMain/kotlin/com/squareup/workflow1/internal/WorkflowRunner.kt`).

- Deduplicates initial props emission via `dropWhile { it == currentProps }` to avoid an immediate second render (`WorkflowRunner.kt:36-49`).
- `nextRendering()` calls root `render` then `snapshot`, wrapped by interceptor hook `onRenderAndSnapshot` (`WorkflowRunner.kt:68-74`).
- `awaitAndApplyAction()` uses `select` over props channel + root tree selectors (`WorkflowRunner.kt:83-90`).

### 3. WorkflowNode As The Core State Machine Host

`WorkflowNode` manages per-node state, rendering, action channels, dirty flags, side effects, remember cache, and child subtree manager (`workflow-runtime/src/commonMain/kotlin/com/squareup/workflow1/internal/WorkflowNode.kt`).

Important fields and behavior:

- Uses `SubtreeManager` for children (`WorkflowNode.kt:84-93`).
- Uses `ActiveStagingList` for side effects and remembered values (`WorkflowNode.kt:94-96`).
- Tracks dirty status with `selfStateDirty` and `subtreeStateDirty` for partial tree rendering (`WorkflowNode.kt:103-111`).
- Re-renders only when needed if `PARTIAL_TREE_RENDERING` is enabled (`WorkflowNode.kt:312-316`).
- Commit phase after each render:
  - `subtreeManager.commitRenderedChildren()`
  - start staged side effect jobs
  - cancel obsolete side effects
  - commit remembered entries (`WorkflowNode.kt:323-331`).

### 4. Child Reconciliation Model

`SubtreeManager` implements child rendering/reuse/teardown (`workflow-runtime/src/commonMain/kotlin/com/squareup/workflow1/internal/SubtreeManager.kt`).

- Child nodes are tracked with active and staging collections (`SubtreeManager.kt:32-78`).
- On each `renderChild` call:
  - validate sibling key uniqueness by scanning staging (`forEachStaging`) (`SubtreeManager.kt:127-135`),
  - `retainOrCreate` child by searching active (`SubtreeManager.kt:138-143`),
  - update handler and render child (`SubtreeManager.kt:145-147`).
- On commit, children left in old active are cancelled (`SubtreeManager.kt:110-119`).

### 5. ActiveStagingList + InlineLinkedList

`ActiveStagingList` is the dual-list abstraction used by children/side-effects/remembered values (`workflow-runtime/src/commonMain/kotlin/com/squareup/workflow1/internal/ActiveStagingList.kt`).

- `retainOrCreate` does a linear `removeFirst(predicate)` from active, then appends to staging (`ActiveStagingList.kt:42-49`).
- `commitStaging` calls `onRemove` for remaining active entries, swaps list references, clears new staging (`ActiveStagingList.kt:55-65`).

`InlineLinkedList` is a custom intrusive singly-linked list (`workflow-runtime/src/commonMain/kotlin/com/squareup/workflow1/internal/InlineLinkedList.kt`).

- Nodes carry their own `nextListNode` pointer.
- Operations are minimal and allocation-light: append, iterate, remove-first-by-predicate, clear.

### 6. Where Uniqueness Checks Happen

The sibling/remember/side-effect duplicate checks are all linear scans over staging:

- Child uniqueness in `SubtreeManager.render` (`SubtreeManager.kt:127-135`).
- Side effect key uniqueness in `WorkflowNode.runningSideEffect` (`WorkflowNode.kt:175-183`).
- Remember uniqueness (`key + resultType + inputs`) in `WorkflowNode.remember` (`WorkflowNode.kt:192-206`).

This matches the Slack thread concern: repeated `forEachStaging` checks in hot render paths.

### 7. EventHandler + remember Coupling

Stable event handlers are implemented in `HandlerBox.kt` and route through `BaseRenderContext.remember` when `remember = true` (or when runtime enables stable handlers by default):

- `eventHandler*` uses `remember(name, typeOf<...>())` to retrieve a stable handler box (`workflow-core/src/commonMain/kotlin/com/squareup/workflow1/HandlerBox.kt:10-413`).
- `STABLE_EVENT_HANDLERS` controls default remember behavior (`workflow-core/src/commonMain/kotlin/com/squareup/workflow1/RuntimeConfig.kt:76-80`, `StatefulWorkflow.kt:167-178`).

Implication: as stable handlers are used more heavily, the remembered staging identity path gets hotter.

### 8. Tracing And Benchmarking Surface

Existing observability/perf infrastructure is already strong:

- `WorkflowRuntimeMonitor` tracks action causes and render pass behavior (`workflow-tracing/src/main/java/com/squareup/workflow1/tracing/WorkflowRuntimeMonitor.kt`).
- `WorkflowRenderPassTracker` records render causes + durations (`workflow-tracing/src/main/java/com/squareup/workflow1/tracing/WorkflowRenderPassTracker.kt`).
- `benchmarks/runtime-microbenchmark` has targeted runtime microbenchmarks for tree updates and state/props churn (`benchmarks/runtime-microbenchmark/src/androidTest/kotlin/com/squareup/benchmark/runtime/benchmark/WorkflowRuntimeMicrobenchmark.kt`).
- `benchmarks/performance-poetry` includes integration-style render-pass efficiency checks (`RenderPassTest.kt`, `RenderPassCountingInterceptor.kt`).

## Slack Thread Context (Summary)

The referenced thread centers on one specific hot section in child rendering:

- Duplicate sibling key checking (`CheckingUniqueMatches`) scans staging children each `renderChild`.
- Concern is that this pattern is also relevant to `remember`, which is hit by event handler creation.
- Proposed direction in thread: treat identity as top-level abstraction and add set-backed lookup (potentially `LinkedHashSet` or sidecar set) while preserving ordering/reconciliation semantics.

## Optimization Project Candidates

### Project 1: Set-Backed Identity Index For Active/Staging

Scope:

- Keep `InlineLinkedList` for ordering and swap semantics.
- Add optional sidecar identity indexes for active and/or staging (e.g. `MutableSet` / `MutableMap<Identity, Node>`).

Targeted wins:

- O(1)-ish duplicate detection for sibling keys/remember keys/side-effect keys.
- O(1)-ish node lookup during `retainOrCreate` for indexed identities.

Key files:

- `workflow-runtime/.../ActiveStagingList.kt`
- `workflow-runtime/.../SubtreeManager.kt`
- `workflow-runtime/.../WorkflowNode.kt`

Validation:

- Extend runtime microbenchmarks with key-heavy sibling and remember-heavy scenarios.
- Confirm no regressions in `ActiveStagingListTest`, `SubtreeManagerTest`, and `WorkflowNodeTest`.

### Project 2: Insert-Time Uniqueness API

Scope:

- Move duplicate checking into insertion path (`retainOrCreate`-like API), removing separate pre-scan + insert phases.

Targeted wins:

- Remove one full staging traversal in child/remember/side-effect paths.
- Simplify call-site logic and reduce repeated predicate work.

Rationale from Slack thread:

- The check and insertion happen adjacently today and can be unified.

### Project 3: Adaptive Hybrid Collection (Small-N List, Larger-N Indexed)

Scope:

- Preserve current linear path for tiny sibling counts.
- Promote to indexed mode once count exceeds threshold, demote when shrinking.

Targeted wins:

- Preserve low overhead for common single-digit sibling lists.
- Protect against pathological larger sibling counts or high-frequency remember/eventHandler usage.

### Project 4: Remember Identity Key Object Fast Path

Scope:

- Replace repeated tuple comparisons (`key`, `KType`, `inputs.contentEquals`) with a cached identity token or precomputed key object for remembered entries.

Targeted wins:

- Fewer repeated array comparisons in `WorkflowNode.remember`.
- Better cache locality if identity is represented by compact key struct.

Key files:

- `workflow-runtime/.../RememberedNode.kt`
- `workflow-runtime/.../WorkflowNode.kt`
- `workflow-core/.../HandlerBox.kt`

### Project 5: Dedicated Benchmarks For Uniqueness/Identity Hot Paths

Scope:

- Add microbenchmarks that intentionally stress:
  - high sibling counts with unique keys,
  - many remembered entries per render,
  - stable event handler heavy renders.

Targeted wins:

- Quantify tradeoffs of list vs set/indexed structures.
- Provide objective gates for runtime changes before/after.

Suggested location:

- `benchmarks/runtime-microbenchmark/.../WorkflowRuntimeMicrobenchmark.kt`

### Project 6: Runtime-Integrated Perf Counters For Internal Collection Ops

Scope:

- Add optional internal counters (debug-only or interceptor-backed) for:
  - active scan length,
  - staging uniqueness check counts,
  - retain hit/miss ratio.

Targeted wins:

- Faster diagnosis of real-world hot spots in production-like scenarios.
- Better prioritization of which identity paths matter most.

Potential integration:

- `WorkflowRuntimeMonitor`/`WorkflowRuntimeTracer` reporting hooks.

### Project 7: Evaluate Multiplatform Set Implementations For Runtime CommonMain

Scope:

- Evaluate candidate set/map implementations for KMP runtime internals.
- Compare stdlib structures against AndroidX collection options where compatible with current module constraints.

Targeted wins:

- Potentially lower overhead than default stdlib structures for small-object identity sets.
- Better understanding of dependency and binary-size tradeoffs before deep refactors.

### Project 8: Action Drain/Conflation Heuristics Experiments

Scope:

- Use existing options (`DRAIN_EXCLUSIVE_ACTIONS`, `CONFLATE_STALE_RENDERINGS`, `WORK_STEALING_DISPATCHER`) to evaluate if additional heuristics should govern draining depth or render emission timing.

Targeted wins:

- Reduce stale intermediate render work in high-throughput action cascades.
- Improve throughput without changing workflow semantics.

Key files:

- `workflow-runtime/.../RenderWorkflow.kt`
- `workflow-runtime/.../WorkflowRunner.kt`

## Recommended Execution Order

1. Start with Project 5 (benchmark scenarios) so every following project has measurable baselines.
2. Prototype Project 1 (set-backed identity index) behind internal flag or branch.
3. Fold in Project 2 (insert-time uniqueness) if benchmark data supports simplification.
4. Evaluate Projects 3/4 based on measured wins and complexity.
5. Run broader perf + trace validation with Project 6 and optionally 8.

## Notes On Correctness Constraints

Any collection/index refactor must preserve:

- Child lifecycle behavior (retain existing node when identity matches, cancel dropped nodes on commit).
- Deterministic active ordering assumptions used by child action selector traversal.
- Duplicate-key failure behavior and error messages.
- Remember semantics: stable identity based on `(key, resultType, inputs)`.
- Side effect semantics: start-after-render, retain-by-key, cancel-when-not-rendered.
