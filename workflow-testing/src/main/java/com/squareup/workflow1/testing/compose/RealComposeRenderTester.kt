package com.squareup.workflow1.testing.compose

import com.squareup.workflow1.WorkflowExperimentalApi

@OptIn(WorkflowExperimentalApi::class)
internal class RealComposeRenderTester<OutputT, RenderingT>(

) : ComposeRenderTester<OutputT, RenderingT> {
}
