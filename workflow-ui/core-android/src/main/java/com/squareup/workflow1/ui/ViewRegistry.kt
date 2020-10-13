@file:Suppress("FunctionName")

package com.squareup.workflow1.ui

import android.app.Dialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.squareup.workflow1.ui.ViewRegistry.Entry
import kotlin.reflect.KClass

/**
 * [Entry]s that are always available.
 */
@WorkflowUiExperimentalApi
internal val defaultViewRegistry = ViewRegistry(
    NamedViewFactory, ModalContainerView, AlertDialogBuilder()
)
// TODO: deprecate Named for NamedViewRendering and NamedModalRendering.
// TODO: Allow ViewRegistry overrides so that customization is practical, e.g.
// to change the dialogThemeResId on AlertDialogBuilder()
// TODO: Update BackStack machinery and move it back into core.

@WorkflowUiExperimentalApi
interface ViewRegistry {
  interface Entry< in RenderingT : Any> {
    val type: KClass<in RenderingT>
  }

  val keys: Set<KClass<*>>

  fun <RenderingT : Any> getEntryFor(
    renderingType: KClass<out RenderingT>
  ): Entry<RenderingT>
  companion object : ViewEnvironmentKey<ViewRegistry>(ViewRegistry::class) {
    override val default: ViewRegistry
      get() = error("There should always be a ViewRegistry hint, this is bug in Workflow.")
  }

  @Deprecated("Use getEntryFor")
  fun <RenderingT : Any> getFactoryFor(
    renderingType: KClass<out RenderingT>
  ): ViewFactory<RenderingT>
}

@WorkflowUiExperimentalApi
fun ViewRegistry(vararg bindings: Entry<*>): ViewRegistry = TypedViewRegistry(*bindings)

/**
 * Returns a [ViewRegistry] that merges all the given [registries].
 */
@WorkflowUiExperimentalApi
fun ViewRegistry(vararg registries: ViewRegistry): ViewRegistry = CompositeViewRegistry(*registries)

/**
 * Returns a [ViewRegistry] that contains no bindings.
 *
 * Exists as a separate overload from the other two functions to disambiguate between them.
 */
@WorkflowUiExperimentalApi
fun ViewRegistry(): ViewRegistry = TypedViewRegistry()

@Deprecated(
    "Use ViewBuilder.buildView",
    ReplaceWith(
        "initialRendering.buildView(, initialViewEnvironment, contextForNewView, container)",
        "com.squareup.workflow1.ui.buildView"
    )
)
@WorkflowUiExperimentalApi
fun <RenderingT : Any> ViewRegistry.buildView(
  initialRendering: RenderingT,
  initialViewEnvironment: ViewEnvironment,
  contextForNewView: Context,
  container: ViewGroup? = null
): View {
  return getFactoryFor(initialRendering::class)
      .buildView(
          initialRendering,
          initialViewEnvironment,
          contextForNewView,
          container
      )
      .apply {
        check(this.getRendering<Any>() != null) {
          "View.bindShowRendering should have been called for $this, typically by the " +
              "${ViewFactory::class.java.name} that created it."
        }
      }
}

@Deprecated(
    "Use ViewBuilder.buildView",
    ReplaceWith(
        "initialRendering.buildView(, initialViewEnvironment, container)",
        "com.squareup.workflow1.ui.buildView"
    )
)
@WorkflowUiExperimentalApi
fun <RenderingT : Any> ViewRegistry.buildView(
  initialRendering: RenderingT,
  initialViewEnvironment: ViewEnvironment,
  container: ViewGroup
): View = buildView(initialRendering, initialViewEnvironment, container.context, container)

@WorkflowUiExperimentalApi
operator fun ViewRegistry.plus(binding: Entry<*>): ViewRegistry =
  this + ViewRegistry(binding)

@WorkflowUiExperimentalApi
operator fun ViewRegistry.plus(other: ViewRegistry): ViewRegistry = ViewRegistry(this, other)

@WorkflowUiExperimentalApi
fun <RenderingT : ViewRendering> RenderingT.buildView(
  initialViewEnvironment: ViewEnvironment,
  contextForNewView: Context,
  container: ViewGroup? = null
): View {
  @Suppress("UNCHECKED_CAST")
  val builder = (this as? ViewBuilder<RenderingT>)
      ?:  initialViewEnvironment[ViewRegistry].getEntryFor(this::class)
  require(builder is ViewBuilder<RenderingT>) {
    "A ${ViewBuilder::class.java.name} should have been registered " +
        "to display a ${this::class}, instead found $builder."
  }

  return builder
      .buildView(
          this,
          initialViewEnvironment,
          contextForNewView,
          container
      )
      .apply {
        check(this.getRendering<Any>() != null) {
          "View.bindShowRendering should have been called for $this, typically by the " +
              "${ViewBuilder::class.java.name} that created it."
        }
      }
}

@WorkflowUiExperimentalApi
fun <RenderingT : ViewRendering> RenderingT.buildView(
  initialViewEnvironment: ViewEnvironment,
  container: ViewGroup
): View = buildView(initialViewEnvironment, container.context, container)

@WorkflowUiExperimentalApi
fun <RenderingT: ModalRendering> RenderingT.buildDialog(
  initialViewEnvironment: ViewEnvironment,
  context: Context
): Dialog {
  val builder = initialViewEnvironment[ViewRegistry].getEntryFor(this::class)
  require(builder is DialogBuilder<RenderingT>) {
    "A ${DialogBuilder::class.java.name} should have been registered " +
        "to display a ${this::class}, instead found $builder."
  }

  return builder
      .buildDialog(
          this,
          initialViewEnvironment,
          context
      )
      .apply {
        check(this.getDisplaying<ModalRendering>() != null) {
          "Dialog.bindShowRendering should have been called for $this, typically by the " +
              "${DialogBuilder::class.java.name} that created it."
        }
      }
}
