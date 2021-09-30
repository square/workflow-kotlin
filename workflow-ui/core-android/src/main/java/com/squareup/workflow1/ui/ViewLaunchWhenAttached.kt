package com.squareup.workflow1.ui

import android.view.View
import android.view.View.NO_ID
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Launches a coroutine when the view is attached, and cancels it when detached. The [block] is passed
 * the current [CoroutineScope] as its receiver.
 *
 * ## Usage
 *
 * Use this if you need to do scoped work while a view is attached, for example update the UI with an
 * expensive-to-compute image at regular intervals. Don’t use this if you expect the work to continue /
 * not be interrupted after a configuration change that lands on the same screen (e.g. for network
 * calls).
 *
 * ## Lifecycle
 *
 * If the view is attached when this function is called, the coroutine will be started synchronously
 * with the [CoroutineStart.UNDISPATCHED] mode, which means the suspend block will execute until its
 * first suspension point. If the view is not attached when this function is called, the [block] will
 * be queued to be executed as soon as the view is attached.
 *
 * When the view becomes detached (regardless of its initial attach state), the [CoroutineScope] that
 * the [block] is running in will be canceled. The scope will remain cancelled _even if the view is
 * later reattached_. The [block] _will not_ be re-started if the view is re-attached.
 *
 * The coroutine will be a child of the scope of the receiver's
 * [LifecycleOwner][ViewTreeLifecycleOwner.get].
 *
 * @param context The [CoroutineContext] in which to run the returned [CoroutineScope].
 * This context _must not_ contain a [Job] – if it does, an [IllegalArgumentException]
 * will be thrown.
 *
 * @return The [Job] that will be cancelled when the view is detached. The job hierarchy created by
 * this function is: `ViewTreeLifecycleOwner -> returned Job -> launched coroutine block`
 * (where `->` means "is the parent of"). Both the returned job and the launched coroutine
 * will be cancelled if the `ViewTreeLifecycleOwner` ends. If the returned job is cancelled,
 * the launched coroutine will be cancelled. The returned job is meant to provide a way to
 * cancel the launched coroutine before the view is detached.
 */
internal fun View.launchWhenAttached(
  context: CoroutineContext = EmptyCoroutineContext,
  block: suspend CoroutineScope.() -> Unit
): Job {
  require(context[Job] == null) { "Expected custom CoroutineContext to not contain a Job." }
  val attachedScope = ensureAttachedScope()

  val launch = fun() {
    @OptIn(ExperimentalCoroutinesApi::class)
    attachedScope.coroutineScope.launch(context, CoroutineStart.UNDISPATCHED, block)
  }

  if (isAttachedToWindow) {
    launch()
  } else {
    attachedScope.runWhenAttached(launch)
  }

  return attachedScope.coroutineScope.coroutineContext.job
}

private fun View.ensureAttachedScope(): AttachedScope {
  // Makes for clearer code below.
  val view = this
  return (view.getTag(R.id.view_attached_coroutine_scope) as? AttachedScope)
    // If the scope is already cancelled, then this view was detached at some point while that scope
    // was active.
    ?.takeIf { it.coroutineScope.isActive }
    ?: run {
      // Create a new scope if the previous one is used up, or there wasn't one in the first place.
      val lifecycleOwner = checkNotNull(ViewTreeLifecycleOwner.get(this)) {
        "ViewTreeLifecycleOwner is required by View.ensureAttachedScope"
      }
      val parentCoroutineScope = lifecycleOwner.lifecycleScope

      // Include basic diagnostic information about the view in the coroutine name.
      val coroutineName = buildString {
        append("${view::class.java.name}@${view.hashCode()}")
        if (view.id != NO_ID) {
          append('-')
          append(resources.getResourceEntryName(view.id))
        }
      }.let(::CoroutineName)

      AttachedScope(parentCoroutineScope + coroutineName)
        .also { scope ->
          view.setTag(R.id.view_attached_coroutine_scope, scope)
          view.addOnAttachStateChangeListener(scope)
        }
    }
}

private class AttachedScope(
  parentCoroutineScope: CoroutineScope
) : View.OnAttachStateChangeListener {
  private val attachHandlers = mutableListOf<() -> Unit>()
  private val attachedJob = Job(parent = parentCoroutineScope.coroutineContext.job)
    .apply {
      invokeOnCompletion {
        // Clear all attach handlers once we know they will never run.
        attachHandlers.clear()
      }
    }

  /**
   * The [CoroutineScope] owned by this instance and that will be canceled by
   * [onViewDetachedFromWindow].
   */
  val coroutineScope = parentCoroutineScope + attachedJob

  /**
   * If this scope's view is currently attached, or has not yet been attached, registers [block] to
   * be invoked when the view is attached.
   *
   * If this scope has already been detached, throws [IllegalStateException].
   */
  fun runWhenAttached(block: () -> Unit) {
    check(coroutineScope.isActive) {
      "Expected AttachedScope to be active when adding attach handler."
    }
    attachHandlers += block
  }

  override fun onViewAttachedToWindow(v: View) {
    attachHandlers.apply {
      forEach { it.invoke() }
      clear()
    }
  }

  override fun onViewDetachedFromWindow(v: View) {
    coroutineScope.cancel("View detached")
    v.removeOnAttachStateChangeListener(this)
  }
}
