package com.squareup.workflow1.ui.internal.test

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub
import com.squareup.workflow1.ui.plus
import kotlin.reflect.KClass

/**
 * Base activity class to help test container view implementations' [LifecycleOwner] behaviors.
 *
 * Create an `ActivityScenarioRule` in your test that launches your subclass of this activity, and
 * then have your subclass expose a method that calls [setRendering] with whatever rendering type your
 * test wants to use. Then call [consumeLifecycleEvents] to get a list of strings back that describe
 * what lifecycle-related events occurred since the last call.
 *
 * Subclasses must override [viewRegistry] to specify the [ScreenViewFactory]s they require.
 * All views will be hosted inside a [WorkflowViewStub].
 */
@WorkflowUiExperimentalApi
public abstract class AbstractLifecycleTestActivity : WorkflowUiTestActivity() {

  private val lifecycleEvents = mutableListOf<String>()

  protected abstract val viewRegistry: ViewRegistry

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

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    logEvent("activity onCreate")

    // This will override WorkflowUiTestActivity's retention of the environment across config
    // changes. This is intentional, since our ViewRegistry probably contains a leafBinding which
    // captures the events list.
    viewEnvironment = ViewEnvironment.EMPTY + viewRegistry
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

  protected fun <R : Screen> leafViewBinding(
    type: KClass<R>,
    viewObserver: ViewObserver<R>,
    viewConstructor: (Context) -> LeafView<R> = ::LeafView
  ): ScreenViewFactory<R> = object : ScreenViewFactory<R> {
    override val type = type
    override fun buildView(
      environment: ViewEnvironment,
      context: Context,
      container: ViewGroup?
    ): View {
      return viewConstructor(context).apply {
        this.viewObserver = viewObserver
        viewObserver.onViewCreated(this)
      }
    }

    override fun updateView(
      view: View,
      rendering: R,
      environment: ViewEnvironment
    ) {
      @Suppress("UNCHECKED_CAST")
      (view as LeafView<R>).rendering = rendering
      viewObserver.onShowRendering(view, rendering)
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
      view: View
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
