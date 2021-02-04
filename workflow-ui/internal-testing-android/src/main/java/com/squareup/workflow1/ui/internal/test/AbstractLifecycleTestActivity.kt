package com.squareup.workflow1.ui.internal.test

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import com.squareup.workflow1.ui.BuilderViewFactory
import com.squareup.workflow1.ui.Named
import com.squareup.workflow1.ui.NamedViewFactory
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.bindShowRendering
import com.squareup.workflow1.ui.plus
import kotlin.reflect.KClass

/**
 * Base activity class to help test container view implementations' [LifecycleOwner] behaviors.
 *
 * Create an `ActivityScenarioRule` in your test that launches your subclass of this activity, and
 * then have your subclass expose a method that calls [update] with whatever rendering type your
 * test wants to use. Then call [consumeLifecycleEvents] to get a list of strings back that describe
 * what lifecycle-related events occurred since the last call.
 *
 * Subclasses must override [viewRegistry] to specify the [ViewFactory]s they require. All views
 * will be hosted inside a [WorkflowViewStub].
 */
@WorkflowUiExperimentalApi
public abstract class AbstractLifecycleTestActivity : AppCompatActivity() {

  protected abstract val viewRegistry: ViewRegistry

  private val lifecycleEvents = mutableListOf<String>()

  private val viewEnvironment by lazy {
    ViewEnvironment(mapOf(ViewRegistry to (viewRegistry + NamedViewFactory)))
  }

  private val rootStub by lazy { WorkflowViewStub(this) }

  private var renderingCounter = 0
  private lateinit var lastRendering: Any

  /**
   * Returns a list of strings describing what lifecycle-related events occurred since the last
   * call to this method. Use this list to validate the ordering of lifecycle events in your tests.
   *
   * Hint: Start by expecting this list to be empty, then copy-paste the actual strings from the
   * test failure into your test and making sure they look reasonable.
   */
  public fun consumeLifecycleEvents(): List<String> = lifecycleEvents.toList().also {
    lifecycleEvents.clear()
  }

  /**
   * Causes the next [update] call to force a new view to be created, even if it otherwise wouldn't
   * be (i.e. because the rendering is compatible with the previous one).
   */
  public fun recreateRenderingOnNextUpdate() {
    renderingCounter++
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    logEvent("activity onCreate")
    setContentView(rootStub)
  }

  override fun onStart() {
    super.onStart()
    logEvent("activity onStart")
  }

  override fun onResume() {
    super.onResume()
    logEvent("activity onResume")
  }

  override fun onPause() {
    logEvent("activity onPause")
    super.onPause()
  }

  override fun onStop() {
    logEvent("activity onStop")
    super.onStop()
  }

  override fun onDestroy() {
    logEvent("activity onDestroy")
    super.onDestroy()
  }

  protected fun logEvent(message: String) {
    lifecycleEvents += message
  }

  protected fun update(rendering: Any): View {
    lastRendering = rendering
    val named = Named(
      wrapped = rendering,
      name = renderingCounter.toString()
    )
    return rootStub.update(named, viewEnvironment)
  }

  public fun <R : Any> leafViewBinding(
    type: KClass<R>,
    describe: (R) -> String
  ): ViewFactory<R> =
    BuilderViewFactory(type) { initialRendering, initialViewEnvironment, contextForNewView, _ ->
      LeafView(contextForNewView, describe).apply {
        bindShowRendering(initialRendering, initialViewEnvironment) { rendering, _ ->
          this.rendering = rendering
        }
      }
    }

  private inner class LeafView<R : Any>(
    context: Context,
    private val describeRendering: (R) -> String
  ) : View(context), LifecycleEventObserver {

    // We can't rely on getRendering() in case it's wrapped with Named.
    lateinit var rendering: R

    override fun onAttachedToWindow() {
      super.onAttachedToWindow()
      logEvent("LeafView ${describeRendering(rendering)} onAttached")

      ViewTreeLifecycleOwner.get(this)!!.lifecycle.removeObserver(this)
      ViewTreeLifecycleOwner.get(this)!!.lifecycle.addObserver(this)
    }

    override fun onDetachedFromWindow() {
      // Don't remove the lifecycle observer here, since we need to observe events after detach.
      logEvent("LeafView ${describeRendering(rendering)} onDetached")
      super.onDetachedFromWindow()
    }

    override fun onStateChanged(
      source: LifecycleOwner,
      event: Event
    ) {
      logEvent("LeafView ${describeRendering(rendering)} $event")
    }
  }
}
