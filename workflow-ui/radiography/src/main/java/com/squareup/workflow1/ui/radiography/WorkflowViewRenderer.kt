package com.squareup.workflow1.ui.radiography

import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.getRendering
import radiography.AttributeAppendable
import radiography.ScannableView
import radiography.ScannableView.AndroidView
import radiography.ViewStateRenderer
import radiography.ViewStateRenderers

/**
 * Renders information about views that were created by view factories, i.e. views with associated
 * rendering tags.
 */
@Suppress("unused")
public val ViewStateRenderers.WorkflowViewRenderer: ViewStateRenderer
  get() = WorkflowViewRendererImpl

@OptIn(WorkflowUiExperimentalApi::class)
private object WorkflowViewRendererImpl : ViewStateRenderer {

  override fun AttributeAppendable.render(view: ScannableView) {
    val androidView = (view as? AndroidView)?.view ?: return
    val rendering = androidView.getRendering<Any>() ?: return
    renderRendering(rendering)
  }

  private fun AttributeAppendable.renderRendering(rendering: Any) {
    val actualRendering = (rendering as? Named<*>)?.wrapped ?: rendering
    append("workflow-rendering-type:${actualRendering::class.java.name}")

    if (rendering is Compatible) {
      append("workflow-compatibility-key:${rendering.compatibilityKey}")
    }
  }
}
