# Workflow Runtime Internals And Optimization Opportunities

Date: 2026-03-11

## Goal

Document how the `workflow-runtime` internals currently work, then identify practical optimization projects that can be prototyped and benchmarked.

## Sources Used

- Previous discussions on performance around `ActiveStagingList` identity checks and `forEachStaging` hot paths.
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

This matches previous discussions on performance: repeated `forEachStaging` checks in hot render paths.

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

## Previous Performance Discussions (Summary)

Previous discussions on performance centered on one specific hot section in child rendering:

- Duplicate sibling key checking (`CheckingUniqueMatches`) scans staging children each `renderChild`.
- Concern is that this pattern is also relevant to `remember`, which is hit by event handler creation.
- Proposed direction in those discussions: treat identity as top-level abstraction and add set-backed lookup (potentially `LinkedHashSet` or sidecar set) while preserving ordering/reconciliation semantics.

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

Rationale from previous performance discussions:

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

### Project 7 Deep Dive: Collection Strategy Research (2026-03-12)

This section captures focused research on collection alternatives after Project 1 landed (indexed
active/staging behavior behind `INDEXED_ACTIVE_STAGING_LISTS`).

#### Runtime-Specific Constraints To Preserve

- `ActiveStagingList` uses two intrusive linked lists (`active`, `staging`) for ordering + swap-on-
  commit semantics.
- Sidecar index operations are currently membership check + key lookup + insert/remove (not full map
  iteration) in `SubtreeManager` and `WorkflowNode`.
- Deterministic traversal order still comes from the list, not from sidecar map iteration.
- Current sidecar index type accepts `Any?` identity values.

#### Candidate Families And Tradeoffs

| Candidate | Fit For Runtime Internals | Benefits | Tradeoffs |
| --- | --- | --- | --- |
| Kotlin stdlib `MutableMap`/`MutableSet` (current Project 1 shape) | High | No new dependency, straightforward semantics across KMP targets, easy debugging and testability. | Higher per-entry/object overhead vs flatter hash tables in some runtimes. |
| Kotlin stdlib `LinkedHashMap`/`LinkedHashSet` | Low-Medium | Deterministic map/set iteration order. | Extra per-entry link overhead with little value here because ordering is already owned by `InlineLinkedList`. |
| AndroidX `MutableScatterMap` / `MutableScatterSet` | Medium-High | Flat hash-table design explicitly optimized for low allocation and cache-friendly operations; AndroidX release notes call out ongoing perf work for scatter collections. | New transitive dependency for `workflow-runtime` commonMain, non-`MutableMap` API surface, and potential null-key adaptation concerns depending on call site assumptions. |
| AndroidX `SimpleArrayMap` / `ArraySet` | Low | Memory-efficient for tiny collections and no per-entry node objects. | Not ideal for larger or churn-heavy paths; AndroidX docs now steer toward scatter collections for better performance characteristics. |
| AndroidX `OrderedScatterSet` | Low-Medium (set-only niche) | Preserves insertion order while staying in scatter family. | Does not solve indexed map lookup by itself, and adds ordering metadata we do not currently need for sidecar indexes. |
| Compose-style specialized internal containers (`MutableVector`, wrappers) | Medium (pattern source) | Compose demonstrates pragmatic use of internal specialized collections + wrappers to keep hot paths fast. | `MutableVector`/array-backed structures are poor direct replacements for `InlineLinkedList` removal/swap behavior used by `ActiveStagingList`. |

#### Upstream Evidence From Compose Multiplatform / AndroidX

- Compose runtime commonMain already depends on AndroidX collection (`implementation("androidx.collection:collection:1.5.0")`) and uses scatter/object-list primitives in internals.
- Compose runtime collection layer uses `MutableScatterMap`, `MutableScatterSet`, and
  `MutableObjectList` in internal utilities such as `ScopeMap`, `MultiValueMap`, and
  `ScatterSetWrapper`.
- Compose continues to keep fast-path internals specialized while exposing standard collection
  interfaces only at boundaries (wrapper/adaptor pattern).
- AndroidX collection release notes confirm KMP support and ongoing scatter-collection performance
  optimization; release notes also describe scatter collections as high-efficiency, low-allocation
  alternatives.

#### Recommendation

1. Keep stdlib sidecar indexes as the default backend in `workflow-runtime` for now.
2. Treat AndroidX scatter collections as the primary alternative worth prototyping (and the only
   one with meaningful upside signal in this code path).
3. Do not pursue `SimpleArrayMap`/`ArraySet` or `LinkedHashMap` as global replacements for current
   sidecar indexes.
4. Borrow Compose's pattern, not just its types: if we prototype scatter collections, keep them
   internal and hide them behind a tiny backend interface so we can swap implementations without
   touching `SubtreeManager`/`WorkflowNode` call sites.

#### Suggested Prototype Shape (Project 7)

1. Add an internal index-backend abstraction used by `ActiveStagingList` in indexed mode:
   `contains`, `put`, `remove`, `clear`.
2. Implement two backends:
   - stdlib backend (`MutableMap<Any?, T>`) as control.
   - AndroidX scatter backend (`MutableScatterMap<Any, T>`) with explicit null-identity handling
     strategy if needed.
3. Compare backends using Project 5 microbenchmarks already in repo (`wideSiblingKeys*`,
   `rememberManyEntries*`, `stableHandlers_manyCallbacks*`).
4. Add low-cardinality scenarios (1-8 identities) to catch regressions where constant factors
   dominate.

#### Go/No-Go Criteria For Adopting AndroidX Scatter Backend

- Go if high-cardinality scenarios show meaningful median wins (target >=10-15%) with no low-N
  regression above 5%.
- Go only if behavior parity is maintained for duplicate detection, lifecycle cancellation, and
  commit semantics.
- No-go if wins are narrow to synthetic high-cardinality scenarios and add noticeable dependency/
  complexity cost for mainstream workflow shapes.

#### Benchmark Results (Pixel 6, 2026-03-12, 500 Iterations)

Measured using targeted indexed scenarios for both tree shapes and both runtime configs:

- `IndexedStdlib` and `IndexedScatter`
- `wideSiblingKeys*`, `rememberManyEntries*`, `stableHandlers_manyCallbacks_propChange`
- `androidx.benchmark.iterations=500`

Key findings:

- Category-level mean deltas (Scatter vs Stdlib):
  - `wideSiblingKeys*`: **+10.43%** (scatter slower)
  - `rememberManyEntries*`: **-4.15%** (scatter faster)
  - `stableHandlers_manyCallbacks_propChange`: **-4.33%** (scatter faster)
- Stability/variance was similar across backends:
  - `IndexedStdlib`: CV mean **4.87%** (max 7.14%)
  - `IndexedScatter`: CV mean **4.91%** (max 6.73%)
- Most scenarios are mildly non-normal by Jarque-Bera at this sample size, but Q-Q fit CoD
  (`R²`) stayed high (roughly 0.96-0.99), indicating near-normal shape with tail/skew effects.

Interpretation:

- The backend tradeoff is workload dependent, not noise: remember/stable-handler paths benefited
  from scatter indexes, while wide sibling-key paths regressed.
- Since CV is comparable and confidence intervals are tight at 500 iterations, these directional
  differences are statistically robust enough to guide further design work.

#### Extended Benchmark Results: SimpleArrayMap Candidate (Pixel 6, 2026-03-12)

Measured with one dedicated 500-iteration targeted pass per indexed backend for both tree shapes:

- `IndexedStdlib`
- `IndexedScatter`
- `IndexedSimpleArrayMap`

Scenarios matched prior methodology:

- `wideSiblingKeys*`
- `rememberManyEntries*`
- `stableHandlers_manyCallbacks_propChange`

Key findings (category-level mean delta vs `IndexedStdlib`):

- `IndexedScatter`:
  - `wideSiblingKeys*`: **+17.04%**
  - `rememberManyEntries*`: **+0.79%**
  - `stableHandlers_manyCallbacks_propChange`: **+1.74%**
- `IndexedSimpleArrayMap`:
  - `wideSiblingKeys*`: **+9.07%**
  - `rememberManyEntries*`: **+43.03%**
  - `stableHandlers_manyCallbacks_propChange`: **+58.34%**

Scenario-level highlights:

- `IndexedSimpleArrayMap` only improved `wideSiblingKeys_initialRenderAllChildren` (roughly
  **-4.75%** shallow / **-1.50%** squareish), and regressed all other targeted scenarios.
- `IndexedScatter` remained the strongest non-stdlib backend, but still showed substantial wide-
  sibling regressions (**+11.29%** to **+21.93%**) and only near-parity on remember/stable paths.

Stability/normality checks stayed consistent with earlier methodology:

- CV means were tightly clustered:
  - `IndexedStdlib`: **4.67%** (max 5.54%)
  - `IndexedScatter`: **4.72%** (max 6.60%)
  - `IndexedSimpleArrayMap`: **4.34%** (max 5.63%)
- Q-Q CoD (`R²`) remained high (roughly 0.96-0.99), with many scenarios still mildly non-normal
  by Jarque-Bera.

Conclusion from this expanded run:

- `SimpleArrayMap` is a clear **no-go** as a general sidecar index backend for current runtime hot
  paths.
- `ScatterMap` remains a potentially useful specialized option, but does not currently beat stdlib
  broadly enough to become the default.

#### Linked-List Removal Assessment (Project 7 Follow-Up)

Question: should we prototype removing intrusive linked lists from `ActiveStagingList` entirely?

Candidate designs considered:

1. **Dual array/object-list staging with identity→index maps**
   - Keep active/staging swap semantics, but store nodes in dense arrays and remove retained nodes
     via index swap-remove.
   - Expected upside: removes the current post-index linear unlink (`active.removeFirst { it ===
     retained }`) from indexed retain paths.
   - Cost/risk: significantly more index bookkeeping (index updates on swap-remove, commit-time
     consistency), plus new failure modes around stale index entries.
2. **Single identity map with generation stamps**
   - Mark nodes seen in current render, then sweep stale nodes at commit.
   - Expected upside: no separate active/staging containers.
   - Cost/risk: harder to preserve deterministic traversal order and current swap semantics without
     reintroducing order side-structures.
3. **Hybrid ordered container abstraction (array/list order + sidecar identity map)**
   - Similar runtime behavior to today, but replaces intrusive node links with explicit container
     internals.
   - Expected upside: simpler node types, possible cache-locality improvements.
   - Cost/risk: likely higher constant-factor overhead for small-N workflows unless carefully tuned.

Worth prototyping?

- **Not as a broad replacement project right now.** Current data does not show backend-only wins
  large enough to justify a full structure rewrite risk.
- **Potentially yes as a narrow spike** if we specifically target the indexed retain unlink hotspot
  (the O(n) identity-hit removal step) behind a runtime flag and benchmark gate.
- Any such spike must preserve existing correctness constraints: duplicate detection behavior,
  deterministic child action traversal, and commit/cancellation semantics.

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
