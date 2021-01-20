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
 * ## Examples
 *
 * To make one rendering type an "alias" for another -- that is, to use the same [ViewFactory]
 * to display it -- provide nothing but a single-arg mapping function:
 *
 *    class OriginalRendering(val data: String)
 *    class AliasRendering(val similarData: String)
 *
 *    object DecorativeViewFactory : ViewFactory<AliasRendering>
 *    by DecorativeViewFactory(
 *      type = AliasRendering::class, map = { alias -> OriginalRendering(alias.similarData) }
 *    )
 *
 * To make a decorator type that adds information to the [ViewEnvironment]:
 *
 *    class NeutronFlowPolarity(val reversed) {
 *      companion object : ViewEnvironmentKey<NeutronFlowPolarity>(NeutronFlowPolarity::class) {
 *        override val default: NeutronFlowPolarity = NeutronFlowPolarity(reversed = false)
 *      }
 *    }
 *
 *    class NeutronFlowPolarityOverride<W>(
 *      val wrapped: W,
 *      val polarity: NeutronFlowPolarity
 *    )
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
 *    class WithTutorialTips<W>(val wrapped: W)
 *
 *    object WithTutorialTipsViewFactory : ViewFactory<WithTutorialTips<*>>
 *    by DecorativeViewFactory(
 *        type = WithTutorialTips::class,
 *        map = { withTips -> withTips.wrapped },
 *        initView = { _, view -> TutorialTipRunner.run(view) }
 *    )
 *
 * To make a decorator type that adds pre- or post-processing to [View] updates:
 *
 *    class BackButtonScreen<W : Any>(
 *       val wrapped: W,
 *       val override: Boolean = false,
 *       val onBackPressed: (() -> Unit)? = null
 *    )
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
 * @param initView called after the [ViewFactory] for [InnerT] has created a [View].
 * Defaults to a no-op. Note that the [ViewEnvironment] is accessible via [View.environment].
 *
 * @param doShowRendering called to apply the [ViewShowRendering] function for
 * [InnerT], allowing pre- and post-processing. Default implementation simply
 * applies [map] and makes the function call.
 */
@WorkflowUiExperimentalApi
public class DecorativeViewFactory<OuterT : Any, InnerT : Any>(
  override val type: KClass<OuterT>,
  private val map: (OuterT, ViewEnvironment) -> Pair<InnerT, ViewEnvironment>,
  private val initView: (OuterT, View) -> Unit = { _, _ -> },
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
    initView: (OuterT, View) -> Unit = { _, _ -> },
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
        container,
        viewInitializer = { view ->
          // At this point the ShowRenderingTag will have been set, but showRendering will not have
          // been called yet. We pull the tag out so we can wrap it with our own mapping call.
          // Note that we need to get the parameterized showRendering out of the tag instead of just
          // using the initialShowRendering nullary function passed to this lambda, because we pass
          // it to doShowRendering which requirest the parameterized version.
          val innerShowRendering = view.getShowRendering<InnerT>()!!

          initView(initialRendering, view)

          // Replace the ShowRenderingTag with one that will call our mapper function, which we
          // trust to eventually call the original innerShowRendering.
          view.bindShowRendering(initialRendering, processedInitialEnv) { outerRendering, env ->
            doShowRendering(view, innerShowRendering, outerRendering, env)
          }
        }
      )
  }
}
