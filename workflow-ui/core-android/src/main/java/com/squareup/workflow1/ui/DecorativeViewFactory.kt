package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.reflect.KClass

@Deprecated("Use DecorativeScreenViewFactory")
@WorkflowUiExperimentalApi
public class DecorativeViewFactory<OuterT : Any, InnerT : Any>(
  override val type: KClass<OuterT>,
  private val map: (OuterT, ViewEnvironment) -> Pair<InnerT, ViewEnvironment>,
  private val initializeView: View.() -> Unit = { showFirstRendering() },
  private val doShowRendering: (
    view: View,
    innerShowRendering: ViewShowRendering<InnerT>,
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
  public constructor(
    type: KClass<OuterT>,
    map: (OuterT) -> InnerT,
    initializeView: View.() -> Unit = { showFirstRendering() },
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
    initializeView = initializeView,
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
        container,
        // Don't call showRendering yet, we need to wrap the function first.
        initializeView = { }
      )
      .also { view ->
        val innerShowRendering: ViewShowRendering<InnerT> = view.getShowRendering()!!

        view.bindShowRendering(
          initialRendering,
          processedInitialEnv
        ) { rendering, env -> doShowRendering(view, innerShowRendering, rendering, env) }

        view.initializeView()
      }
  }
}
