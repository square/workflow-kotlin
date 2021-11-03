package com.squareup.workflow1.ui

/**
 * Interface implemented by a rendering class to allow it to drive an Android UI
 * via an appropriate [ScreenViewFactory] implementation.
 *
 * You will rarely, if ever, write a [ScreenViewFactory] yourself.  Instead
 * use [ScreenViewRunner.bind] to work with XML layout resources, or
 * [BuilderViewFactory] to create views from code.  See [ScreenViewRunner] for more
 * details.
 *
 *     @OptIn(WorkflowUiExperimentalApi::class)
 *     data class HelloScreen(
 *       val message: String,
 *       val onClick: () -> Unit
 *     ) : AndroidScreen<HelloScreen> {
 *       override val viewFactory: ScreenViewFactory<HelloScreen> =
 *         LayoutRunner.bind(HelloGoodbyeLayoutBinding::inflate) { r, _ ->
 *           helloMessage.text = r.message
 *           helloMessage.setOnClickListener { r.onClick() }
 *         }
 *     }
 *
 * This is the simplest way to bridge the gap between your workflows and the UI,
 * but using it requires your workflows code to reside in Android modules, instead
 * of pure Kotlin. If this is a problem, or you need more flexibility for any other
 * reason, you can use [ViewRegistry] to bind your renderings to [ScreenViewFactory]
 * implementations at runtime.
 */
@WorkflowUiExperimentalApi
public interface AndroidScreen<V : AndroidScreen<V>>: Screen {
  /**
   * Used to build instances of [android.view.View] as needed to
   * display renderings of this type.
   */
  public val viewFactory: ScreenViewFactory<V>
}
