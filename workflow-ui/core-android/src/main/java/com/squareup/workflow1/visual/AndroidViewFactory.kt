package com.squareup.workflow1.visual

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.IdRes
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@WorkflowUiExperimentalApi
public object AndroidViewFactoryKey : VisualEnvironmentKey<AnyVisualFactory<Context, View>>() {
  override val default: AnyVisualFactory<Context, View>
    get() = ExactTypeVisualFactory()
}


/**
 * Convenience to access any Android view Holder's output as `androidView`.
 */
@WorkflowUiExperimentalApi
public val <ViewT : View> VisualHolder<*, ViewT>.androidView: ViewT
  get() = visual

@WorkflowUiExperimentalApi
public fun <RenderingT, ViewT : View> androidViewFactoryFromCode(
  block: AndroidViewFactoryScope<RenderingT, ViewT>.() -> Unit,
): VisualFactory<Context, RenderingT, ViewT> =
  object : SimpleVisualFactory<Context, RenderingT, ViewT>() {
    override fun create(
      context: Context,
      environment: VisualEnvironment
    ): VisualHolder<RenderingT, ViewT> {
      val scope = AndroidViewFactoryScope<RenderingT, ViewT>(context, environment)
        .apply(block)
      val view = scope.view
      val updateBlock = scope.updateBlock
      return object : VisualHolder<RenderingT, ViewT> {
        override val visual: ViewT = view
        override fun update(rendering: RenderingT): Boolean {
          updateBlock(rendering)
          return true
        }
      }
    }
  }

@WorkflowUiExperimentalApi
public inline fun <RenderingT, reified ViewT : View> androidViewFactoryFromLayout(
  @IdRes resId: Int,
  crossinline block: AndroidViewFactoryScope<RenderingT, ViewT>.() -> Unit
): VisualFactory<Context, RenderingT, ViewT> = androidViewFactoryFromCode {
  view = LayoutInflater.from(context).inflate(resId, null, false) as ViewT
  block()
}

/**
 * This receiver is for the main block in [androidViewFactoryFromCode].
 * It helps to define an update block inside it in an ergonomic way. -- helios
 *
 * I'm dubious of this, seems obfuscatory, and a source of runtime errors.
 * I'm more inclined to create a AndroidViewHolder factory function, similar to ScreenViewHolder()
 * -- rjrjr
 */
@WorkflowUiExperimentalApi
public class AndroidViewFactoryScope<RenderingT, ViewT : View>
@PublishedApi
internal constructor(
  public val context: Context,
  public val environment: VisualEnvironment
) {
  public lateinit var view: ViewT
  public lateinit var updateBlock: (RenderingT) -> Unit
  public fun update(block: (rendering: RenderingT) -> Unit) {
    updateBlock = block
  }
}
