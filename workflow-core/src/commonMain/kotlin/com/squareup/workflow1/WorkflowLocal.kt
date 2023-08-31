package com.squareup.workflow1


public typealias WorkflowLocalKey<T> = LocalMapKey<T>

/**
 * Immutable map of values that a parent Workflow can pass down to
 * its children. Allows Workflows to give descendants some signals that are established for the
 * lifetime of the ancestor who added it.
 *
 * @see LocalMap for implementation
 */
public typealias WorkflowLocal = LocalMap

public val EmptyWorkflowLocal: WorkflowLocal = LocalMap.Companion.EMPTY
