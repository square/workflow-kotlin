package com.squareup.workflow1.ui.internal.test

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.widget.FrameLayout
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
import com.squareup.workflow1.ui.internal.test.AbstractLifecycleTestActivity.LeafView
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

  private val lifecycleEvents = mutableListOf<String>()

  private val viewEnvironment by lazy {
    ViewEnvironment(mapOf(ViewRegistry to (viewRegistry + NamedViewFactory)))
  }

  private val rootStub by lazy { WorkflowViewStub(this) }

  private var renderingCounter = 0
  protected lateinit var lastRendering: Any
    private set

  protected abstract val viewRegistry: ViewRegistry

  /**
   * The [View] that was created to display the last rendering passed to [update].
   */
  protected val renderedView: View get() = rootStub.actual

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

  protected fun <R : Any> leafViewBinding(
    type: KClass<R>,
    viewObserver: ViewObserver<R>,
    viewConstructor: (Context) -> LeafView<R> = ::LeafView
  ): ViewFactory<R> =
    BuilderViewFactory(type) { initialRendering, initialViewEnvironment, contextForNewView, _ ->
      viewConstructor(contextForNewView).apply {
        this.viewObserver = viewObserver
        viewObserver.onViewCreated(this, initialRendering)

        bindShowRendering(initialRendering, initialViewEnvironment) { rendering, _ ->
          this.rendering = rendering
          viewObserver.onShowRendering(this, rendering)
        }
      }
    }

  protected fun <R : Any> lifecycleLoggingViewObserver(
    describeRendering: (R) -> String
  ): ViewObserver<R> = object : ViewObserver<R> {
    override fun onAttachedToWindow(
      view: View,
      rendering: R
    ) {
      logEvent("LeafView ${describeRendering(rendering)} onAttached")
    }

    override fun onDetachedFromWindow(
      view: View,
      rendering: R
    ) {
      logEvent("LeafView ${describeRendering(rendering)} onDetached")
    }

    override fun onViewTreeLifecycleStateChanged(
      rendering: R,
      event: Event
    ) {
      logEvent("LeafView ${describeRendering(rendering)} $event")
    }
  }

  public interface ViewObserver<R : Any> {
    public fun onViewCreated(
      view: View,
      rendering: R
    ) {
    }

    public fun onShowRendering(
      view: View,
      rendering: R
    ) {
    }

    public fun onAttachedToWindow(
      view: View,
      rendering: R
    ) {
    }

    public fun onDetachedFromWindow(
      view: View,
      rendering: R
    ) {
    }

    public fun onViewTreeLifecycleStateChanged(
      rendering: R,
      event: Event
    ) {
    }

    public fun onSaveInstanceState(
      view: View,
      rendering: R
    ) {
    }

    public fun onRestoreInstanceState(
      view: View,
      rendering: R
    ) {
    }
  }

  public open class LeafView<R : Any>(
    context: Context
  ) : FrameLayout(context) {

    internal var viewObserver: ViewObserver<R>? = null

    // We can't rely on getRendering() in case it's wrapped with Named.
    public lateinit var rendering: R
      internal set

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
      viewObserver?.onViewTreeLifecycleStateChanged(rendering, event)
    }

    override fun onAttachedToWindow() {
      super.onAttachedToWindow()
      viewObserver?.onAttachedToWindow(this, rendering)

      ViewTreeLifecycleOwner.get(this)!!.lifecycle.removeObserver(lifecycleObserver)
      ViewTreeLifecycleOwner.get(this)!!.lifecycle.addObserver(lifecycleObserver)
    }

    override fun onDetachedFromWindow() {
      // Don't remove the lifecycle observer here, since we need to observe events after detach.
      viewObserver?.onDetachedFromWindow(this, rendering)
      super.onDetachedFromWindow()
    }

    override fun onSaveInstanceState(): Parcelable? {
      return super.onSaveInstanceState().apply {
        viewObserver?.onSaveInstanceState(this@LeafView, rendering)
      }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
      super.onRestoreInstanceState(state)
      viewObserver?.onRestoreInstanceState(this@LeafView, rendering)
    }
  }
}
