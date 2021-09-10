package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.reflect.KClass

/**
 * A [ViewFactory] for [OuterT] that delegates view construction responsibilities
 * to the factory registered for [InnerT]. Makes it convenient for [OuterT] to wrap
 * instances of [InnerT] to add information or behavior, without requiring wasteful wrapping
 * in the view system.
 *
 * One general note: when creating a wrapper rendering, you're very likely to want it
 * to implement [Compatible], to ensure that checks made to update or replace a view
 * are based on the wrapped item. Each example below illustrates this.
 *
 * ## Examples
 *
 * To make one rendering type an "alias" for another -- that is, to use the same [ViewFactory]
 * to display it -- provide nothing but a single-arg mapping function:
 *
 *    class OriginalRendering(val data: String)
 *    class AliasRendering(val similarData: String) : Compatible {
 *      override val compatibilityKey: String = Compatible.keyFor(wrapped)
 *    }
 *
 *    object DecorativeViewFactory : ViewFactory<AliasRendering>
 *    by DecorativeViewFactory(
 *      type = AliasRendering::class, map = { alias -> OriginalRendering(alias.similarData) }
 *    )
 *
 * To make a decorator type that adds information to the [ViewEnvironment]:
 *
 *    class NeutronFlowPolarity(val reversed: Boolean) {
 *      companion object : ViewEnvironmentKey<NeutronFlowPolarity>(NeutronFlowPolarity::class) {
 *        override val default: NeutronFlowPolarity = NeutronFlowPolarity(reversed = false)
 *      }
 *    }
 *
 *    class NeutronFlowPolarityOverride<W>(
 *      val wrapped: W,
 *      val polarity: NeutronFlowPolarity
 *    ) : Compatible {
 *      override val compatibilityKey: String = Compatible.keyFor(wrapped)
 *    }
 *
 *    object NeutronFlowPolarityViewFactory : ViewFactory<NeutronFlowPolarityOverride<*>>
 *    by DecorativeViewFactory(
 *        type = NeutronFlowPolarityOverride::class,
 *        map = { override, env ->
 *          Pair(override.wrapped, env + (NeutronFlowPolarity to override.polarity))
 *        }
 *    )
 *
 * To make a decorator type that customizes [View] initialization:
 *
 *    class WithTutorialTips<W>(val wrapped: W) : Compatible {
 *      override val compatibilityKey: String = Compatible.keyFor(wrapped)
 *    }
 *
 *    object WithTutorialTipsViewFactory : ViewFactory<WithTutorialTips<*>>
 *    by DecorativeViewFactory(
 *        type = WithTutorialTips::class,
 *        map = { withTips -> withTips.wrapped },
 *        initializeView = {
 *          TutorialTipRunner.run(this)
 *          showFirstRendering<WithTutorialTips<*>>()
 *        }
 *    )
 *
 * To make a decorator type that adds pre- or post-processing to [View] updates:
 *
 *    class BackButtonScreen<W : Any>(
 *       val wrapped: W,
 *       val override: Boolean = false,
 *       val onBackPressed: (() -> Unit)? = null
 *    ) : Compatible {
 *      override val compatibilityKey: String = Compatible.keyFor(wrapped)
 *    }
 *
 *    object BackButtonViewFactory : ViewFactory<BackButtonScreen<*>>
 *    by DecorativeViewFactory(
 *        type = BackButtonScreen::class,
 *        map = { outer -> outer.wrapped },
 *        doShowRendering = { view, innerShowRendering, outerRendering, viewEnvironment ->
 *          if (!outerRendering.override) {
 *            // Place our handler before invoking innerShowRendering, so that
 *            // its later calls to view.backPressedHandler will take precedence
 *            // over ours.
 *            view.backPressedHandler = outerRendering.onBackPressed
 *          }
 *
 *          innerShowRendering.invoke(outerRendering.wrapped, viewEnvironment)
 *
 *          if (outerRendering.override) {
 *            // Place our handler after invoking innerShowRendering, so that ours wins.
 *            view.backPressedHandler = outerRendering.onBackPressed
 *          }
 *        })
 *
 * @param map called to convert instances of [OuterT] to [InnerT], and to
 * allow [ViewEnvironment] to be transformed.
 *
 * @param initializeView Optional function invoked immediately after the [View] is
 * created (that is, immediately after the call to [ViewFactory.buildView]).
 * [showRendering], [getRendering] and [environment] are all available when this is called.
 * Defaults to a call to [View.showFirstRendering].
 *
 * @param doShowRendering called to apply the [ViewShowRendering] function for
 * [InnerT], allowing pre- and post-processing. Default implementation simply
 * uses [map] to extract the [InnerT] instance from [OuterT] and makes the function call.
 */
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

    return processedInitialEnv
      .buildView(
        innerInitialRendering,
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
