package com.squareup.workflow1.compose.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.NonSkippableComposable
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.compose.ComposeWorkflow
import com.squareup.workflow1.compose.renderChild

/**
 * Exposes [ComposeWorkflow.produceRendering] to code outside this module.
 *
 * DO NOT CALL directly, call [renderChild] instead!
 *
 * @suppress
 */
// @InternalWorkflowApi
@OptIn(WorkflowExperimentalApi::class)
@NonRestartableComposable
@NonSkippableComposable
@Composable
fun <PropsT, OutputT, RenderingT> _DO_NOT_USE_invokeComposeWorkflowProduceRendering(
  workflow: ComposeWorkflow<PropsT, OutputT, RenderingT>,
  props: PropsT,
  emitOutput: (OutputT) -> Unit
): RenderingT = workflow.invokeProduceRendering(props, emitOutput)
