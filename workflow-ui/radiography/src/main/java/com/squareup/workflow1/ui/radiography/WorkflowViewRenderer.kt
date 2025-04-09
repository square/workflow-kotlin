package com.squareup.workflow1.ui.radiography

import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.NamedScreen
import com.squareup.workflow1.ui.screenOrNull
import radiography.AttributeAppendable
import radiography.ScannableView
import radiography.ScannableView.AndroidView
import radiography.ViewStateRenderer

public val WorkflowViewStateRenderer: ViewStateRenderer
  get() = WorkflowViewRendererImpl

private object WorkflowViewRendererImpl : ViewStateRenderer {

  override fun AttributeAppendable.render(view: ScannableView) {
    val androidView = (view as? AndroidView)?.view ?: return
    val rendering = androidView.screenOrNull ?: return
    renderRendering(rendering)
  }

  private fun AttributeAppendable.renderRendering(rendering: Any) {
    val actualRendering = (rendering as? NamedScreen<*>)?.content ?: rendering
    append("workflow-rendering-type:${actualRendering::class.java.name}")

    if (rendering is Compatible) {
      append("workflow-compatibility-key:${rendering.compatibilityKey}")
    }
  }
}
