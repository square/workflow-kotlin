package com.squareup.workflow1.internal.compose

import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.compose.ComposeWorkflow

/**
 * Fake state object passed to [WorkflowInterceptor]s as the state for [ComposeWorkflow]s.
 *
 * If we need interceptors to be able to identify compose workflows, we can just make this public.
 */
internal object ComposeWorkflowState
