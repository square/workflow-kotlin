package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.reflect.KClass

@Deprecated(
    "Use DecorativeViewBuilder",
    ReplaceWith(
        "DecorativeViewBuilder(type, map, initView, doShowRendering)",
        "com.squareup.workflow1.ui.DecorativeViewBuilder"
    )
)
@WorkflowUiExperimentalApi
class DecorativeViewFactory<OuterT : Any, InnerT : Any>(
  override val type: KClass<OuterT>,
  private val map: (OuterT, ViewEnvironment) -> Pair<InnerT, ViewEnvironment>,
  private val initView: (OuterT, View) -> Unit = { _, _ -> },
  private val doShowRendering: (
    view: View,
    innerShowRendering: DisplayRendering<InnerT>,
    outerRendering: OuterT,
    env: ViewEnvironment
  ) -> Unit = { _, innerShowRendering, outerRendering, viewEnvironment ->
    val (innerRendering, processedEnv) = map(outerRendering, viewEnvironment)
    innerShowRendering(innerRendering, processedEnv)
  }
) : ViewFactory<OuterT> {

  /**
   * Convenience constructor for cases requiring no changes to the [ViewEnvironment].
   */
  @Deprecated(
      "Use DecorativeViewBuilder",
      ReplaceWith(
          "DecorativeViewBuilder(type, map, initView, doShowRendering)",
          "com.squareup.workflow1.ui.DecorativeViewBuilder"
      )
  )
  constructor(
    type: KClass<OuterT>,
    map: (OuterT) -> InnerT,
    initView: (OuterT, View) -> Unit = { _, _ -> },
    doShowRendering: (
      view: View,
      innerShowRendering: DisplayRendering<InnerT>,
      outerRendering: OuterT,
      env: ViewEnvironment
    ) -> Unit = { _, innerShowRendering, outerRendering, viewEnvironment ->
      innerShowRendering(map(outerRendering), viewEnvironment)
    }
  ) : this(
      type,
      map = { outer, viewEnvironment -> Pair(map(outer), viewEnvironment) },
      initView = initView,
      doShowRendering = doShowRendering
  )

  override fun buildView(
    initialRendering: OuterT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ): View {
    val (innerInitialRendering, processedInitialEnv) = map(initialRendering, initialViewEnvironment)

    return processedInitialEnv[ViewRegistry]
        .buildView(
            innerInitialRendering,
            processedInitialEnv,
            contextForNewView,
            container
        )
        .also { view ->
          val innerShowRendering: DisplayRendering<InnerT> = view.getShowRendering()!!
          initView(initialRendering, view)
          view.bindShowRendering(initialRendering, processedInitialEnv) { rendering, env ->
            doShowRendering(view, innerShowRendering, rendering, env)
          }
        }
  }
}
