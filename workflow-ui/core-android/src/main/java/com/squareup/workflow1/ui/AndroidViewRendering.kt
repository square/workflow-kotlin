package com.squareup.workflow1.ui

/**
 * Interface implemented by a rendering class to allow it to drive an Android UI
 * via an appropriate [ViewFactory] implementation.
 *
 * You will rarely, if ever, write a [ViewFactory] yourself.  Instead
 * use [LayoutRunner.bind] to work with XML layout resources, or
 * [BuilderViewFactory] to create views from code.  See [LayoutRunner] for more
 * details.
 *
 *     @OptIn(WorkflowUiExperimentalApi::class)
 *     data class HelloView(
 *       val message: String,
 *       val onClick: () -> Unit
 *     ) : AndroidViewRendering<HelloView> {
 *       override val viewFactory: ViewFactory<HelloView> =
 *         LayoutRunner.bind(HelloGoodbyeLayoutBinding::inflate) { r, _ ->
 *           helloMessage.text = r.message
 *           helloMessage.setOnClickListener { r.onClick() }
 *         }
 *     }
 *
 * This is the simplest way to bridge the gap between your workflows and the UI,
 * but using it requires your workflows code to reside in Android modules, instead
 * of pure Kotlin. If this is a problem, or you need more flexibility for any other
 * reason, you can use [ViewRegistry] to bind your renderings to [ViewFactory]
 * implementations at runtime.
 */
@WorkflowUiExperimentalApi
public interface AndroidViewRendering<V : AndroidViewRendering<V>> {
  /**
   * Used to build instances of [android.view.View] as needed to
   * display renderings of this type.
   */
  public val viewFactory: ViewFactory<V>
}
