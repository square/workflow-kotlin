package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.reflect.KClass

/**
 * A [ScreenViewFactory] for [WrapperT] that delegates view construction responsibilities
 * to the factory registered for [WrappedT]. Allows [WrapperT] to wrap instances of [WrappedT]
 * to add information or behavior, without requiring wasteful wrapping in the view system.
 *
 * One general note: when creating a wrapper rendering, you're very likely to want it
 * to implement [Compatible], to ensure that checks made to update or replace a view
 * are based on the wrapped item. Each wrapper example below illustrates this.
 *
 * ## Examples
 *
 * To make one rendering type an "alias" for another -- that is, to use the same [ScreenViewFactory]
 * to display it -- provide nothing but a single-arg unwrap function:
 *
 *    class RealRendering(val data: String) : AndroidScreen<RealRendering> {
 *      ...
 *    }
 *    class AliasRendering(val similarData: String)
 *
 *    object DecorativeScreenViewFactory : ScreenViewFactory<AliasRendering>
 *    by DecorativeScreenViewFactory(
 *      type = AliasRendering::class, unwrap = { alias ->
 *        RealRendering(alias.similarData)
 *      }
 *    )
 *
 * To make a wrapper that adds information to the [ViewEnvironment]:
 *
 *    class NeutronFlowPolarity(val reversed: Boolean) {
 *      companion object : ViewEnvironmentKey<NeutronFlowPolarity>(
 *        NeutronFlowPolarity::class
 *      ) {
 *        override val default: NeutronFlowPolarity =
 *          NeutronFlowPolarity(reversed = false)
 *      }
 *    }
 *
 *    class NeutronFlowPolarityOverride<W : Screen>(
 *      val wrapped: W,
 *      val polarity: NeutronFlowPolarity
 *    ) : Screen, Compatible {
 *      override val compatibilityKey: String = Compatible.keyFor(wrapped)
 *    }
 *
 *    object NeutronFlowPolarityViewFactory :
 *      ScreenViewFactory<NeutronFlowPolarityOverride<*>>
 *    by DecorativeScreenViewFactory(
 *        type = NeutronFlowPolarityOverride::class,
 *        unwrap = { override, env ->
 *          Pair(override.wrapped, env + (NeutronFlowPolarity to override.polarity))
 *        }
 *    )
 *
 * To make a wrapper that customizes [View] initialization:
 *
 *    class WithTutorialTips<W : Screen>(val wrapped: W) : Screen, Compatible {
 *      override val compatibilityKey: String = Compatible.keyFor(wrapped)
 *    }
 *
 *    object WithTutorialTipsViewFactory : ScreenViewFactory<WithTutorialTips<*>>
 *    by DecorativeScreenViewFactory(
 *      type = WithTutorialTips::class,
 *      unwrap = { withTips -> withTips.wrapped },
 *      viewStarter = { view, doStart ->
 *        TutorialTipRunner.run(this)
 *        doStart()
 *      }
 *    )
 *
 * To make a wrapper that adds pre- or post-processing to [View] updates:
 *
 *    class BackButtonScreen<W : Screen>(
 *       val wrapped: W,
 *       val override: Boolean = false,
 *       val onBackPressed: (() -> Unit)? = null
 *    ) : Screen, Compatible {
 *      override val compatibilityKey: String = Compatible.keyFor(wrapped)
 *    }
 *
 *    object BackButtonViewFactory : ScreenViewFactory<BackButtonScreen<*>>
 *    by DecorativeScreenViewFactory(
 *      type = BackButtonScreen::class,
 *      unwrap = { wrapper -> wrapper.wrapped },
 *      doShowRendering = { view, wrappedShowRendering, wrapper, viewEnvironment ->
 *        if (!wrapper.override) {
 *          // Place our handler before invoking wrappedShowRendering, so that
 *          // its later calls to view.backPressedHandler will take precedence
 *          // over ours.
 *          view.backPressedHandler = wrapper.onBackPressed
 *        }
 *
 *        wrappedShowRendering.invoke(wrapper.wrapped, viewEnvironment)
 *
 *        if (wrapper.override) {
 *          // Place our handler after invoking wrappedShowRendering, so that ours wins.
 *          view.backPressedHandler = wrapper.onBackPressed
 *        }
 *      }
 *    )
 *
 * @param unwrap called to convert instances of [WrapperT] to [WrappedT], and to
 * allow [ViewEnvironment] to be transformed.
 *
 * @param viewStarter An optional wrapper for the function invoked when [View.start]
 * is called, allowing for last second initialization of a newly built [View].
 * See [ViewStarter] for details.
 *
 * @param doShowRendering called to apply the [ViewShowRendering] function for
 * [WrappedT], allowing pre- and post-processing. Default implementation simply
 * uses [unwrap] to extract the [WrappedT] instance from [WrapperT] and makes the function call.
 */
@WorkflowUiExperimentalApi
public class DecorativeScreenViewFactory<WrapperT : Screen, WrappedT : Screen>(
  override val type: KClass<WrapperT>,
  private val unwrap: (WrapperT, ViewEnvironment) -> Pair<WrappedT, ViewEnvironment>,
  private val viewStarter: ViewStarter? = null,
  private val doShowRendering: (
    view: View,
    wrappedShowRendering: ViewShowRendering<WrappedT>,
    wrapper: WrapperT,
    env: ViewEnvironment
  ) -> Unit = { _, wrappedShowRendering, wrapper, viewEnvironment ->
    val (unwrapped, processedEnv) = unwrap(wrapper, viewEnvironment)
    wrappedShowRendering(unwrapped, processedEnv)
  }
) : ScreenViewFactory<WrapperT> {

  /**
   * Convenience constructor for cases requiring no changes to the [ViewEnvironment].
   */
  public constructor(
    type: KClass<WrapperT>,
    unwrap: (WrapperT) -> WrappedT,
    viewStarter: ViewStarter? = null,
    doShowRendering: (
      view: View,
      wrappedShowRendering: ViewShowRendering<WrappedT>,
      wrapper: WrapperT,
      env: ViewEnvironment
    ) -> Unit = { _, wrappedShowRendering, wrapper, viewEnvironment ->
      wrappedShowRendering(unwrap(wrapper), viewEnvironment)
    }
  ) : this(
    type,
    unwrap = { wrapper, viewEnvironment -> Pair(unwrap(wrapper), viewEnvironment) },
    viewStarter = viewStarter,
    doShowRendering = doShowRendering
  )

  override fun buildView(
    initialRendering: WrapperT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ): View {
    val (unwrapped, processedEnv) = unwrap(initialRendering, initialViewEnvironment)

    return unwrapped.buildView(
      processedEnv,
      contextForNewView,
      container,
      viewStarter
    ).also { view ->
      val wrappedShowRendering: ViewShowRendering<WrappedT> = view.getShowRendering()!!

      view.bindShowRendering(
        initialRendering,
        processedEnv
      ) { rendering, env -> doShowRendering(view, wrappedShowRendering, rendering, env) }
    }
  }
}
