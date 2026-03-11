# Workflow Runtime Optimization Implementation Plan (Projects 5 And 1)

Date: 2026-03-11

## Goal

Deliver an execution-ready plan for:

1. Project 5: dedicated runtime benchmark scenarios for identity/uniqueness hot paths.
1. Project 1: `ActiveStagingList` sidecar identity index (set/map-backed) while preserving runtime semantics.

This plan is intentionally phased, with explicit gates between phases so we only take on Project 1 complexity after collecting baseline evidence from Project 5.

## Scope And Constraints

In scope:

1. Add benchmark scenarios in `benchmarks/runtime-microbenchmark` targeting sibling key, `remember`, and stable handler heavy paths.
1. Add internal runtime implementation for sidecar identity indexing in `workflow-runtime`.
1. Preserve existing behavior and error messages for child keys, side effects, and `remember` identity checks.

Out of scope:

1. Public API changes unrelated to runtime internals.
1. Large tracing/metrics system additions beyond what is needed to validate these projects.
1. Insert-time uniqueness API redesign (Project 2) unless explicitly approved after these two projects.

Correctness constraints (must remain true):

1. Child lifecycle semantics: retain existing child on identity match, cancel dropped children on commit.
1. Deterministic active ordering used by action traversal.
1. Duplicate-key failure behavior and message parity.
1. `remember` identity semantics based on `(key, resultType, inputs)`.
1. Side effect semantics: start after render, retain by key, cancel when not rendered.

## Sequencing Overview

1. Phase 0: benchmark scaffolding and reproducibility guardrails.
1. Phase 1 (Project 5): implement benchmark scenarios and collect baseline data.
1. Gate A: verify benchmark quality and signal strength.
1. Phase 2 (Project 1): implement sidecar identity index behind an internal runtime flag.
1. Gate B: correctness and performance criteria against baseline.
1. Phase 3: rollout decision (default-on or keep gated) and PR hardening.

## Phase 0: Benchmark Scaffold Setup

### Milestone 0.1: Benchmark Harness Prep

Work items:

1. Extend `BenchmarkTreeShape` (or add sibling-count parameters) so scenarios can run at small, medium, and high fan-out.
1. Reuse existing `ShallowWideWorkflowRoot` for sibling-key stress to avoid overloading deep-tree behavior.
1. Keep `BenchmarkRuntimeOptions` matrix (`NoOptimizations`, `AllOptimizations`) for apples-to-apples comparisons.

Primary files:

1. `benchmarks/runtime-microbenchmark/src/androidTest/kotlin/com/squareup/benchmark/runtime/benchmark/WorkflowRuntimeMicrobenchmark.kt`

Success gate:

1. Benchmark module still compiles and dry-runs with no behavior regressions in current tests.

Validation commands:

1. `./gradlew :benchmarks:runtime-microbenchmark:connectedReleaseAndroidTest -Pandroidx.benchmark.dryRunMode.enable=true`

## Phase 1: Project 5 Benchmark Scenarios

### Milestone 5.1: High-Sibling Uniqueness Benchmarks

Purpose:

1. Stress child duplicate checks and active child reconciliation in wide trees where `forEachStaging` and active scans are hottest.

Benchmarks to add:

1. `renderWideUniqueSiblings_initial` (cold creation with unique keys).
1. `renderWideUniqueSiblings_rerenderSameShape` (retain existing nodes with same keys).
1. `renderWideUniqueSiblings_toggleSingleChild` (exercise mixed retain/cancel paths).

Validation criteria:

1. Rendering count assertions remain deterministic (same style as existing tests).
1. Runtime curves grow with sibling count and produce stable medians across repeated runs.

### Milestone 5.2: Remember-Heavy Identity Benchmarks

Purpose:

1. Stress `WorkflowNode.remember` uniqueness and lookup paths with many entries per render.

Benchmarks to add:

1. `rememberManyEntries_sameInputs` (lookup/retain heavy).
1. `rememberManyEntries_changingInputs` (create/replace heavy).
1. `rememberManyEntries_mixedTypes` (exercise `resultType` identity dimension).

Validation criteria:

1. Render outputs remain semantically correct for remembered values.
1. Increasing remember-entry count produces measurable timing deltas.

### Milestone 5.3: Stable Event Handler Heavy Benchmark

Purpose:

1. Capture the `eventHandler` + `remember` coupling path under many handlers per render.

Benchmarks to add:

1. `stableHandlers_manyCallbacks_singleRenderPass`.
1. `stableHandlers_manyCallbacks_rerenderNoPropChange`.
1. `stableHandlers_manyCallbacks_propChange`.

Validation criteria:

1. Handler behavior remains stable and outputs still route correctly.
1. Time profile differentiates low vs high callback counts.

### Milestone 5.4: Baseline Publication

Deliverable:

1. Commit a short baseline report table (in `design-docs`) with median and spread for each new scenario by runtime option.

Gate A (must pass before Project 1):

1. New scenarios are reproducible (no flaky assertion failures).
1. At least one high-cardinality sibling scenario and one remember-heavy scenario show enough runtime spread to detect a >=10% implementation delta.
1. Small-cardinality scenarios are included to detect low-N regressions.

## Phase 2: Project 1 Sidecar Identity Index

### Milestone 1.1: Data Structure And Flag Scaffold

Work items:

1. Add optional identity-index support to `ActiveStagingList` while retaining list-based ordering and commit swap semantics.
1. Introduce an internal runtime toggle so benchmark runs can compare index on/off in the same branch.
1. Keep existing non-indexed path as fallback.

Primary files:

1. `workflow-runtime/src/commonMain/kotlin/com/squareup/workflow1/internal/ActiveStagingList.kt`
1. `workflow-runtime/src/commonMain/kotlin/com/squareup/workflow1/internal/SubtreeManager.kt`
1. `workflow-runtime/src/commonMain/kotlin/com/squareup/workflow1/internal/WorkflowNode.kt`

Success criteria:

1. Index-enabled and index-disabled paths are functionally equivalent in tests.
1. No ordering changes in active traversal.

### Milestone 1.2: Child Path Integration (`SubtreeManager`)

Work items:

1. Replace staging duplicate scan path with indexed membership checks for child identities.
1. Use indexed active lookup hints for retain/create decisions where possible, while preserving teardown behavior.
1. Keep existing error text for duplicate keys.

Validation criteria:

1. `SubtreeManagerTest` duplicate-key and lifecycle tests pass unchanged.
1. Child render ordering remains deterministic.

### Milestone 1.3: Side Effect Path Integration (`WorkflowNode.runningSideEffect`)

Work items:

1. Replace staging duplicate scans with indexed key membership for side effects.
1. Preserve lazy start and cancel-on-drop behavior in commit phase.

Validation criteria:

1. Existing side effect uniqueness message remains exactly: `Expected side effect keys to be unique: "<key>"`.
1. Existing side effect lifecycle tests in `WorkflowNodeTest` pass.

### Milestone 1.4: Remember Path Integration (`WorkflowNode.remember`)

Work items:

1. Add identity key representation for `(key, resultType, inputs)` used by sidecar index.
1. Replace staging duplicate scan with indexed uniqueness checks.
1. Keep current remember result behavior and identity semantics unchanged.

Validation criteria:

1. Existing remember behavior tests pass for key/input/return type changes.
1. Add at least one explicit duplicate-remember test to lock duplicate failure behavior.

### Milestone 1.5: Test And Perf Consolidation

Work items:

1. Add/expand focused unit tests in `ActiveStagingListTest` for indexed mode (retain, create, commit, removal).
1. Re-run Project 5 benchmarks with index disabled and enabled.
1. Publish before/after benchmark deltas.

Gate B (must pass to ship):

1. Correctness: all relevant tests green with no semantic regressions.
1. Performance: high-cardinality sibling and remember scenarios improve by target median >=15% in at least one runtime config, with no small-N regressions worse than 5%.
1. Stability: no benchmark assertion flakes across repeated runs.

## Phase 3: Rollout And Merge Criteria

### Milestone 3.1: Rollout Decision

Decision options:

1. Enable sidecar index by default if Gate B is met broadly.
1. Keep index behind internal runtime option if gains are mixed and further tuning is needed.
1. Pause rollout if correctness holds but low-N regressions exceed threshold.

### Milestone 3.2: PR Hardening

Required checks:

1. `./gradlew :workflow-runtime:jvmTest`
1. `./gradlew :benchmarks:runtime-microbenchmark:connectedReleaseAndroidTest -Pandroidx.benchmark.dryRunMode.enable=true`
1. Physical-device microbenchmark run for final numbers (per benchmark README guidance).

Optional confidence checks:

1. Run `performance-poetry` render-pass tests as integration sanity for render churn behavior.

## Implementation Notes And Risk Mitigation

1. Keep changes incremental by call-site (children, side effects, remember) instead of one large rewrite.
1. Preserve old code path until benchmark deltas and tests justify removal.
1. Ensure index state is swapped/cleared atomically with active/staging list swaps to avoid stale membership.
1. Maintain exact duplicate-key messages to avoid breaking tests and caller expectations.

## Definition Of Done

1. New benchmark suite lands with documented baseline and post-change comparison.
1. Sidecar identity index implementation lands with all correctness gates passing.
1. Performance gates are met (or an explicit decision is documented to keep feature gated).
1. Plan outcomes and benchmark deltas are recorded in-repo for future optimization projects.
