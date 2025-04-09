package com.squareup.sample.stubvisibility

import android.view.Gravity.CENTER
import android.view.View
import android.view.View.GONE
import android.view.View.OnClickListener
import android.view.View.VISIBLE
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.TextView
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder

data class ClickyTextRendering(
  val message: String,
  val visible: Boolean = true,
  val onClick: (() -> Unit)? = null
) : AndroidScreen<ClickyTextRendering> {
  override val viewFactory = ScreenViewFactory.fromCode<ClickyTextRendering>(
    buildView = { _, initialEnvironment, context, _ ->
      val view = TextView(context).also { textView ->
        textView.layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        textView.gravity = CENTER
      }
      ScreenViewHolder(initialEnvironment, view) { rendering, _ ->
        val textView = view
        textView.text = rendering.message
        textView.isVisible = rendering.visible
        textView.setOnClickListener(
          rendering.onClick?.let { oc -> OnClickListener { oc() } }
        )
      }
    }
  )
}

private var View.isVisible: Boolean
  get() = visibility == VISIBLE
  set(value) {
    visibility = if (value) VISIBLE else GONE
  }
