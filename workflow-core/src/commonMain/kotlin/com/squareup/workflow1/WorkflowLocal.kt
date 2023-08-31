package com.squareup.workflow1

public typealias WorkflowLocalKey<T> = LocalMapKey<T>

/**
 * Immutable map of non-state values that a parent Workflow can pass down to
 * its children. Allows Workflows to give descendants some signals that are established for the
 * lifetime of the ancestor who added it.
 *
 * Why note use the Workflow's state? These are dependencies - or computations built off
 * dependencies (such as a StateFlow tied to the Workflow node's CoroutineScope) - that are used
 * by the Workflow over the course of its rendering, but that are not part of the 'state' of the
 * Workflow, especially because they are likely hard to compare for equality.
 *
 * @see LocalMap for implementation
 */
public typealias WorkflowLocal = LocalMap

public val EmptyWorkflowLocal: WorkflowLocal = LocalMap.Companion.EMPTY
