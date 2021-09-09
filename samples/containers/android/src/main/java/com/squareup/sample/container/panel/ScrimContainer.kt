package com.squareup.sample.container.panel

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.squareup.sample.container.R
import com.squareup.workflow1.ui.BuilderViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.bindShowRendering

/**
 * A view that renders only its first child, behind a smoke scrim if
 * [isDimmed] is true (tablets only). Other children are ignored.
 *
 * Able to [render][com.squareup.workflow1.ui.showRendering] [ScrimContainerScreen].
 */
class ScrimContainer @JvmOverloads constructor(
  context: Context,
  attributeSet: AttributeSet? = null,
  defStyle: Int = 0,
  defStyleRes: Int = 0
) : ViewGroup(context, attributeSet, defStyle, defStyleRes) {
  private val scrim = object : View(context, attributeSet, defStyle, defStyleRes) {
    init {
      setBackgroundColor(ContextCompat.getColor(context, R.color.scrim))
    }
  }

  private val child: View
    get() = getChildAt(0)
        ?: error("Child must be set immediately upon creation.")

  var isDimmed: Boolean = false
    set(value) {
      if (field == value) return
      field = value
      if (!isAttachedToWindow) updateImmediate() else updateAnimated()
    }

  override fun onAttachedToWindow() {
    updateImmediate()
    super.onAttachedToWindow()
  }

  override fun addView(child: View?) {
    if (scrim.parent != null) removeView(scrim)
    super.addView(child)
    super.addView(scrim)
  }

  override fun onLayout(
    changed: Boolean,
    l: Int,
    t: Int,
    r: Int,
    b: Int
  ) {
    child.layout(0, 0, measuredWidth, measuredHeight)
    scrim.layout(0, 0, measuredWidth, measuredHeight)
  }

  override fun onMeasure(
    widthMeasureSpec: Int,
    heightMeasureSpec: Int
  ) {
    child.measure(widthMeasureSpec, heightMeasureSpec)
    scrim.measure(widthMeasureSpec, heightMeasureSpec)
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
  }

  private fun updateImmediate() {
    if (isDimmed) scrim.alpha = 1f else scrim.alpha = 0f
  }

  private fun updateAnimated() {
    if (isDimmed) {
      ValueAnimator.ofFloat(0f, 1f)
    } else {
      ValueAnimator.ofFloat(1f, 0f)
    }.apply {
      duration = resources.getInteger(android.R.integer.config_shortAnimTime)
          .toLong()
      addUpdateListener { animation -> scrim.alpha = animation.animatedValue as Float }
      start()
    }
  }

  @OptIn(WorkflowUiExperimentalApi::class)
  companion object : ViewFactory<ScrimContainerScreen<*>> by BuilderViewFactory(
      type = ScrimContainerScreen::class,
      viewConstructor = { initialRendering, initialViewEnvironment, contextForNewView, _ ->
        val stub = WorkflowViewStub(contextForNewView)

        ScrimContainer(contextForNewView)
            .apply {
              layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
              addView(stub)

              bindShowRendering() { rendering, environment ->
                stub.update(rendering.wrapped, environment)
                isDimmed = rendering.dimmed
              }
            }
      }
  )
}
