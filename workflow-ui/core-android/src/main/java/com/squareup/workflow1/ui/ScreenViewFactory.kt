package com.squareup.workflow1.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.forBuiltView

@WorkflowUiExperimentalApi
public typealias ViewBindingInflater<BindingT> = (LayoutInflater, ViewGroup?, Boolean) -> BindingT

/**
 * A [ViewRegistry.Entry] that can build Android [View] instances and functions that can update
 * them to display [Screen] renderings of a particular [type].
 *
 * It is most common to create instances via [forViewBinding], [forLayoutResource],
 * [forStaticLayoutResource] or [forBuiltView].
 */
@WorkflowUiExperimentalApi
public interface ScreenViewFactory<in ScreenT : Screen> : ViewRegistry.Entry<ScreenT> {
  public fun buildView(
    initialRendering: ScreenT,
    initialEnvironment: ViewEnvironment,
    context: Context,
    container: ViewGroup? = null
  ): ScreenViewHolder<ScreenT>

  public companion object {
    /**
     * Creates a [ScreenViewFactory] that inflates [layoutId] to "show" renderings of type
     * [ScreenT], but never updates the created view. Handy for showing static displays,
     * e.g. when prototyping.
     */
    @WorkflowUiExperimentalApi
    public inline fun <reified ScreenT : Screen> forBuiltView(
      crossinline buildView: (
        initialRendering: ScreenT,
        initialEnvironment: ViewEnvironment,
        context: Context,
        container: ViewGroup?
      ) -> ScreenViewHolder<ScreenT>,
    ): ScreenViewFactory<ScreenT> {
      return object : ScreenViewFactory<ScreenT> {
        override val type = ScreenT::class

        override fun buildView(
          initialRendering: ScreenT,
          initialEnvironment: ViewEnvironment,
          context: Context,
          container: ViewGroup?
        ): ScreenViewHolder<ScreenT> =
          buildView(initialRendering, initialEnvironment, context, container)
      }
    }
  }
}

/**
 * Use the [ScreenViewFactoryFinder] in [environment] to return the [ScreenViewFactory]
 * bound to the type of the receiving [Screen].
 *
 * - It is more common to use [WorkflowViewStub.show] than to call this method directly
 * - Call [ScreenViewFactory.start] to create and initialize a new [View]
 * - If you don't particularly need to mess with the [ScreenViewFactory] before creating
 *   a view, use [Screen.buildView] instead of this method.
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenT.toViewFactory(
  environment: ViewEnvironment
): ScreenViewFactory<ScreenT> {
  return environment[ScreenViewFactoryFinder].getViewFactoryForRendering(environment, this)
}

/**
 * Creates a [ScreenViewHolder] wrapping a [View] able to display [initialRendering],
 * and initializes the view.
 *
 * By default "initialize" means making the first call to [ScreenViewHolder.show].
 * To add more initialization behavior (typically a call to [WorkflowLifecycleOwner.installOn]),
 * provide a [viewStarter].
 */
@Suppress("DEPRECATION")
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenViewFactory<ScreenT>.start(
  initialRendering: ScreenT,
  initialEnvironment: ViewEnvironment,
  contextForNewView: Context,
  container: ViewGroup? = null,
  viewStarter: ViewStarter? = null
): ScreenViewHolder<ScreenT> {
  return buildView(
    initialRendering,
    initialEnvironment,
    contextForNewView,
    container
  ).also { holder ->
    val resolvedStarter = viewStarter ?: ViewStarter { _, doStart -> doStart() }

    val legacyStarter: ((View) -> Unit)? = holder.view.starterOrNull

    if (legacyStarter != null) {
      var shown = false
      // This View was built by a legacy ViewFactory, and so it needs to be
      // started in just the right way.
      //
      // The tricky bit is the old starter's default value, a function that calls
      // View.showRendering(). Odds are it's wrapped and wrapped again deep inside
      // legacyStarter. To ensure it gets called at the right time, and that we don't
      // update the view redundantly, we use bindShowRendering to replace View.showRendering()
      // with a call to our own holder.show(). (No need to call the original showRendering(),
      // AsScreenViewFactory blanked it.)
      //
      // This same call to bindShowRendering will also update View.getRendering() and
      // View.environment() to return what was passed in here, as expected.
      holder.view.bindShowRendering(
        initialRendering, initialEnvironment
      ) { rendering, environment ->
        holder.show(rendering, environment)
        shown = true
      }
      holder.view.starter = { startingView ->
        resolvedStarter.startView(startingView) { legacyStarter(startingView) }
      }
      // We have to call View.start() to fire this off rather than calling the starter directly,
      // to keep the rest of the legacy machinery happy.
      holder.view.start()
      check(shown) {
        "A ViewStarter provided to ViewRegistry.buildView or a DecorativeViewFactory " +
          "neglected to call the given doStart() function"
      }
    } else {
      var shown = false
      resolvedStarter.startView(holder.view) {
        holder.show(initialRendering, initialEnvironment)
        shown = true
      }
      check(shown) {
        "A ViewStarter provided to Screen.toView or ScreenViewFactory.start " +
          "neglected to call the given doStart() function"
      }
    }
  }
}

/**
 * Creates a [View] able to display [initialRendering], and initializes it. By
 * default "initialize" means making the first call to [ScreenViewFactory.updateView].
 * To add more initialization behavior (typically a call to [WorkflowLifecycleOwner.installOn]),
 * provide a [viewStarter].
 *
 * This method is purely shorthand for calling [Screen.toViewFactory] and then
 * [ScreenViewFactory.start]. You might wish to make those calls separately if you
 * need to treat the [ScreenViewFactory] before using it.
 */
@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenT.buildView(
  initialViewEnvironment: ViewEnvironment,
  contextForNewView: Context,
  container: ViewGroup? = null,
  viewStarter: ViewStarter? = null
): ScreenViewHolder<ScreenT> {
  return toViewFactory(initialViewEnvironment)
    .start(this, initialViewEnvironment, contextForNewView, container, viewStarter)
}

/**
 * A wrapper for the function invoked when [ScreenViewFactory.start] or
 * [Screen.buildView] is called, allowing for custom initialization of
 * a newly built [View] before or after the first call to [ScreenViewFactory.updateView].
 */
@WorkflowUiExperimentalApi
public fun interface ViewStarter {
  /** Called from [View.start]. [doStart] must be invoked. */
  public fun startView(
    view: View,
    doStart: () -> Unit
  )
}

@WorkflowUiExperimentalApi
public inline
fun <reified WrapperT : Screen, WrappedT : Screen> ScreenViewFactory<WrappedT>.unwrapping(
  crossinline unwrap: (wrapperScreen: WrapperT) -> WrappedT,
): ScreenViewFactory<WrapperT> = unwrapping(unwrap) { _, ws, e, su -> su(unwrap(ws), e) }

@WorkflowUiExperimentalApi
public inline
fun <reified WrapperT : Screen, WrappedT : Screen> ScreenViewFactory<WrappedT>.unwrapping(
  crossinline unwrap: (wrapperScreen: WrapperT) -> WrappedT,
  crossinline showWrapperScreen: (
    view: View,
    wrapperScreen: WrapperT,
    environment: ViewEnvironment,
    showUnwrappedScreen: (WrappedT, ViewEnvironment) -> Unit
  ) -> Unit
): ScreenViewFactory<WrapperT> {
  val wrappedFactory = this

  return object : ScreenViewFactory<WrapperT>
  by forBuiltView(
    buildView = { initialRendering, initialEnvironment, context, container ->
      val wrappedHolder = wrappedFactory.buildView(
        unwrap(initialRendering), initialEnvironment, context, container
      )

      object : ScreenViewHolder<WrapperT> {
        override val view = wrappedHolder.view
        override val environment: ViewEnvironment get() = wrappedHolder.environment

        override val runner: ScreenViewRunner<WrapperT> =
          ScreenViewRunner { wrapperScreen, newEnvironment ->
            showWrapperScreen(view, wrapperScreen, newEnvironment) { unwrappedScreen, env ->
              wrappedHolder.runner.showRendering(unwrappedScreen, env)
            }
          }
      }
    }
  ) {
  }
}

internal fun Context.viewBindingLayoutInflater(container: ViewGroup?) =
  LayoutInflater.from(container?.context ?: this)
    .cloneInContext(this)
