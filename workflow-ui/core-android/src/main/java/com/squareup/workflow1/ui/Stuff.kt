package com.squareup.workflow1.ui

import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot.Companion.registerApplyObserver
import androidx.compose.runtime.snapshots.Snapshot.Companion.sendApplyNotifications
import androidx.compose.runtime.snapshots.takeSnapshot
import androidx.compose.runtime.snapshots.withMutableSnapshot
import androidx.compose.runtime.structuralEqualityPolicy
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Worker
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.action
import com.squareup.workflow1.parse
import com.squareup.workflow1.readByteStringWithLength
import com.squareup.workflow1.writeByteStringWithLength
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import okio.Buffer
import okio.ByteString
import java.util.ArrayDeque
import java.util.WeakHashMap
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@DslMarker
annotation class MagicWorkflowDsl

/**
 * A property delegate provider that provides access to and mutation of an [ImplicitWorkflow] state
 * value over time.
 */
interface WorkflowState<T> : ReadWriteProperty<Any?, T> {
  val value: T
}

private class WorkflowStateImpl<T>(private val mutableState: MutableState<T>) : WorkflowState<T> {
  override val value: T get() = mutableState.value
  override fun getValue(
    thisRef: Any?,
    property: KProperty<*>
  ): T = mutableState.value

  @OptIn(ExperimentalComposeApi::class)
  override fun setValue(
    thisRef: Any?,
    property: KProperty<*>,
    value: T
  ) {
    mutableState.value = value
    // If this write is not occuring within a MutableSnapshot, then we need to call this method so
    // the global snapshot will be applied and trigger a re-render.
    sendApplyNotifications()
  }
}

/**
 * Defines how to serialize and deserialize a [WorkflowState].
 */
interface StateSaver<T> {
  fun toByteString(value: T): ByteString
  fun fromByteString(bytes: ByteString): T

  object IntSaver : StateSaver<Int> {
    override fun toByteString(value: Int): ByteString =
      Buffer().apply { writeInt(value) }.readByteString()

    override fun fromByteString(bytes: ByteString): Int = bytes.parse { it.readInt() }
  }
}

@MagicWorkflowDsl
interface InitializationScope<P, O, R> {
  val snapshot: Snapshot?

  fun <T> state(
    saver: StateSaver<T>? = null,
    policy: SnapshotMutationPolicy<T> = structuralEqualityPolicy(),
    init: () -> T
  ): WorkflowState<T>

  fun rendering(render: RenderingScope<O>.(P) -> R): Renderer<P, O, R>
}

@MagicWorkflowDsl
interface RenderingScope<O> {
  fun sendOutput(output: O)
  fun <CP, CO, CR> Workflow<CP, CO, CR>.render(
    props: CP,
    key: String = "",
    onOutput: (CO) -> Unit
  ): CR

  fun runningSideEffect(
    key: String,
    block: suspend () -> Unit
  )
}

fun <O> RenderingScope<O>.runningWorker(
  worker: Worker<O>,
  key: String = "",
  onOutput: (O) -> Unit
) {
  TODO()
}

class Renderer<P, O, R> internal constructor(
  private val appliedChanges: ReceiveChannel<Set<Any>>,
  private val unregisterApplyObserver: () -> Unit,
  private val render: RenderingScope<O>.(P) -> R,
  val stateSavers: Map<MutableState<*>, StateSaver<*>>
) {

  // Objects read the last time workflow was rendered
  private val readSet = mutableSetOf<Any>()
  private val readObserver: (Any) -> Unit = { readSet.add(it) }

  @OptIn(ExperimentalComposeApi::class)
  internal fun doRender(
    context: BaseRenderContext<P, Renderer<P, O, R>, O>,
    props: P
  ): R {
    context.runningSideEffect("watch-for-changes") {
      collectSnapshotReads {
        // Trigger a re-render without emitting any output.
        context.actionSink.send(WorkflowAction.noAction())
      }
    }

    val renderingScope = object : RenderingScope<O> {
      override fun sendOutput(output: O) {
        context.actionSink.send(action { setOutput(output) })
      }

      override fun <CP, CO, CR> Workflow<CP, CO, CR>.render(
        props: CP,
        key: String,
        onOutput: (CO) -> Unit
      ): CR = context.renderChild(this, props, key) { output ->
        // Execute the output handler in a snapshot so that any state changes are all grouped
        // together.
        withMutableSnapshot {
        // TODO capture calls to sendOutput and emit here.
          onOutput(output)
        }
        WorkflowAction.noAction()
      }

      override fun runningSideEffect(
        key: String,
        block: suspend () -> Unit
      ) {
        // Append a key prefix to prevent collisions with our internal workers.
        context.runningSideEffect("-$key", block)
      }
    }

    readSet.clear()
    return takeSnapshot(readObserver).run {
      try {
        enter { render(renderingScope, props) }
      } finally {
        dispose()
      }
    }
  }

  private suspend fun collectSnapshotReads(onChange: () -> Unit) {
    try {
      while (true) {
        var found = false
        var changedObjects = appliedChanges.receive()

        // Poll for any other changes before running block to minimize the number of
        // additional times it runs for the same data
        while (true) {
          // Assumption: readSet will typically be smaller than changed
          found = found || readSet.intersects(changedObjects)
          changedObjects = appliedChanges.poll() ?: break
        }

        if (found) {
          onChange()
        }
      }
    } finally {
      unregisterApplyObserver()
    }
  }
}

/**
 * Return `true` if there are any elements shared between `this` and [other]
 */
private fun <T> Set<T>.intersects(other: Set<T>): Boolean =
  if (size < other.size) any { it in other } else other.any { it in this }

@OptIn(ExperimentalComposeApi::class)
fun <P, O, R> magicWorkflow(
  initializer: InitializationScope<P, O, R>.() -> Renderer<P, O, R>
): Workflow<P, O, R> = object : StatefulWorkflow<P, Renderer<P, O, R>, O, R>() {
  override fun initialState(
    props: P,
    snapshot: Snapshot?
  ): Renderer<P, O, R> {
    // This channel may not block or lose data on an offer call.
    val appliedChanges = Channel<Set<Any>>(Channel.UNLIMITED)

    // Register the apply observer before running for the first time
    // so that we don't miss updates.
    val unregisterApplyObserver = registerApplyObserver { changed, _ ->
      appliedChanges.offer(changed)
    }

    val statesToRestore = ArrayDeque<ByteString>()
    snapshot?.bytes?.parse { source ->
      while (!source.exhausted()) {
        val bytes = source.readByteStringWithLength()
        statesToRestore += bytes
      }
    }

    val statesToSave = WeakHashMap<MutableState<*>, StateSaver<*>>()

    val scope = object : InitializationScope<P, O, R> {
      override val snapshot: Snapshot? get() = snapshot

      override fun <T> state(
        saver: StateSaver<T>?,
        policy: SnapshotMutationPolicy<T>,
        init: () -> T
      ): WorkflowState<T> {
        val bytes = statesToRestore.poll()

        // Don't use elvis, since fromByteString might return null but we don't want to init in
        // that case.
        val initialState = if (bytes == null || saver == null) {
          init()
        } else {
          saver.fromByteString(bytes)
        }
        val mutableState = mutableStateOf(initialState, policy)

        statesToSave[mutableState] = saver
        return WorkflowStateImpl(mutableState)
      }

      override fun rendering(render: RenderingScope<O>.(P) -> R): Renderer<P, O, R> =
        Renderer(appliedChanges, unregisterApplyObserver, render, statesToSave)
    }

    return scope.initializer()
  }

  override fun render(
    props: P,
    state: Renderer<P, O, R>,
    context: RenderContext
  ): R = state.doRender(context, props)

  override fun snapshotState(state: Renderer<P, O, R>): Snapshot? =
    if (state.stateSavers.isEmpty()) null else Snapshot.write { sink ->
      state.stateSavers.forEach { (state, saver) ->
        @Suppress("UNCHECKED_CAST")
        val bytes = (saver as StateSaver<Any?>).toByteString(state.value)
        sink.writeByteStringWithLength(bytes)
      }
    }
}
