package com.squareup.workflow1.compose.internal

@Suppress("ConstPropertyName")
internal object TraceLabels {
  const val InterceptRenderWorkflow = "ComposeInterceptRenderWorkflow"
  const val RenderWorkflow = "ComposeRenderWorkflow"
  const val InitialState = "ComposeInitialState"
  const val OnPropsChanged = "ComposeOnPropsChanged"
  const val SendAction = "ComposeSendAction"
}
