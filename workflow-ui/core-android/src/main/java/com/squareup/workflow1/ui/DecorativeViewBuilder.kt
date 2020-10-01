package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.reflect.KClass

/**
 * A [ViewBuilder] for [OuterT] that delegates view construction responsibilities
 * to the builder registered for [InnerT]. Makes it convenient for [OuterT] to wrap
 * instances of [InnerT] to add information or behavior, without requiring wasteful wrapping
 * in the view system.
 *
 * ## Examples
 *
 * To make one rendering type an "alias" for another -- that is, to use the same [ViewBuilder]
 * to display it -- provide nothing but a single-arg mapping function:
 *
 *    class OriginalRendering(val data: String) : ViewRendering
 *    class AliasRendering(val similarData: String) : ViewRendering
 *
 *    object DecorativeViewBuilder : ViewBuilder<AliasRendering>
 *    by DecorativeViewBuilder(
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
 *    class NeutronFlowPolarityOverride<W : ViewRendering>(
 *      val wrapped: W,
 *      val polarity: NeutronFlowPolarity
 *    ) : ViewRendering
 *
 *    object NeutronFlowPolarityViewBuilder : ViewBuilder<NeutronFlowPolarityOverride<*>>
 *    by DecorativeViewBuilder(
 *        type = NeutronFlowPolarityOverride::class,
 *        map = { override, env ->
 *          Pair(override.wrapped, env + (NeutronFlowPolarity to override.polarity))
 *        }
 *    )
 *
 * To make a decorator type that customizes [View] initialization:
 *
 *    class WithTutorialTips<W : ViewRendering>(val wrapped: W) : ViewRendering
 *
 *    object WithTutorialTipsViewBuilder : ViewBuilder<WithTutorialTips<*>>
 *    by DecorativeViewBuilder(
 *        type = WithTutorialTips::class,
 *        map = { withTips -> withTips.wrapped },
 *        initView = { _, view -> TutorialTipRunner.run(view) }
 *    )
 *
 * To make a decorator type that adds pre- or post-processing to [View] updates:
 *
 *    class BackButtonScreen<W : ViewRendering>(
 *       val wrapped: W,
 *       val override: Boolean = false,
 *       val onBackPressed: (() -> Unit)? = null
 *    ) : ViewRendering
 *
 *    object BackButtonViewBuilder : ViewBuilder<BackButtonScreen<*>>
 *    by DecorativeViewBuilder(
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
 * @param initView called after the [ViewBuilder] for [InnerT] has created a [View].
 * Defaults to a no-op. Note that the [ViewEnvironment] is accessible via [View.environment].
 *
 * @param doShowRendering called to apply the [DisplayRendering] function for
 * [InnerT], allowing pre- and post-processing. Default implementation simply
 * applies [map] and makes the function call.
 */
@WorkflowUiExperimentalApi
class DecorativeViewBuilder<OuterT : ViewRendering, InnerT : ViewRendering>(
  override val type: KClass<OuterT>,
  private val map: (OuterT, ViewEnvironment) -> Pair<InnerT, ViewEnvironment>,
  private val initView: (OuterT, View) -> Unit = { _, _ -> },
  private val doShowRendering: (
    view: View,
    innerDisplayRendering: DisplayRendering<InnerT>,
    outerRendering: OuterT,
    env: ViewEnvironment
  ) -> Unit = { _, innerShowRendering, outerRendering, viewEnvironment ->
    val (innerRendering, processedEnv) = map(outerRendering, viewEnvironment)
    innerShowRendering(innerRendering, processedEnv)
  }
) : ViewBuilder<OuterT> {
  /**
   * Convenience constructor for cases requiring no changes to the [ViewEnvironment].
   */
  constructor(
    type: KClass<OuterT>,
    map: (OuterT) -> InnerT,
    initView: (OuterT, View) -> Unit = { _, _ -> },
    doShowRendering: (
      view: View,
      innerDisplayRendering: DisplayRendering<InnerT>,
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

    return innerInitialRendering
        .buildView(
            processedInitialEnv,
            contextForNewView,
            container
        )
        .also { view ->
          val innerDisplayRendering: DisplayRendering<InnerT> = view.getShowRendering()!!
          initView(initialRendering, view)
          view.bindShowRendering(initialRendering, processedInitialEnv) { rendering, env ->
            doShowRendering(view, innerDisplayRendering, rendering, env)
          }
        }
  }
}
