package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.reflect.KClass

@WorkflowUiExperimentalApi
public class DecorativeScreenViewFactory<OuterT : Screen, InnerT : Screen>(
  override val type: KClass<OuterT>,
  private val map: (OuterT, ViewEnvironment) -> Pair<InnerT, ViewEnvironment>,
  private val viewStarter: ViewStarter? = null,
  private val doShowRendering: (
    view: View,
    innerShowRendering: ViewShowRendering<InnerT>,
    outerRendering: OuterT,
    env: ViewEnvironment
  ) -> Unit = { _, innerShowRendering, outerRendering, viewEnvironment ->
    val (innerRendering, processedEnv) = map(outerRendering, viewEnvironment)
    innerShowRendering(innerRendering, processedEnv)
  }
) : ScreenViewFactory<OuterT> {

  /**
   * Convenience constructor for cases requiring no changes to the [ViewEnvironment].
   */
  public constructor(
    type: KClass<OuterT>,
    map: (OuterT) -> InnerT,
    viewStarter: ViewStarter? = null,
    doShowRendering: (
      view: View,
      innerShowRendering: ViewShowRendering<InnerT>,
      outerRendering: OuterT,
      env: ViewEnvironment
    ) -> Unit = { _, innerShowRendering, outerRendering, viewEnvironment ->
      innerShowRendering(map(outerRendering), viewEnvironment)
    }
  ) : this(
    type,
    map = { outer, viewEnvironment -> Pair(map(outer), viewEnvironment) },
    viewStarter = viewStarter,
    doShowRendering = doShowRendering
  )

  override fun buildView(
    initialRendering: OuterT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ): View {
    val (innerInitialRendering, processedInitialEnv) = map(initialRendering, initialViewEnvironment)

    return innerInitialRendering.buildView(
      processedInitialEnv,
      contextForNewView,
      container,
      viewStarter
    )
      .also { view ->
        val innerShowRendering: ViewShowRendering<InnerT> = view.getShowRendering()!!

        view.bindShowRendering(
          initialRendering,
          processedInitialEnv
        ) { rendering, env -> doShowRendering(view, innerShowRendering, rendering, env) }
      }
  }
}
