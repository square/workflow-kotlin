package com.squareup.workflow2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeCompilerApi
import androidx.compose.runtime.Composer
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLifecycleObserver
import androidx.compose.runtime.CompositionReference
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionFor
import androidx.compose.runtime.compositionReference
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.emit
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.ui.tooling.preview.Preview
import com.squareup.workflow2.RecordNode.Op.Insert
import com.squareup.workflow2.RecordNode.Op.Move
import com.squareup.workflow2.RecordNode.Op.Remove

@DslMarker
annotation class DeferredEmitDsl

@Preview
@Composable private fun DeferredEmitsPreview() {
  EnableDeferredEmits {
    val deferred = recordEmits {
      CounterForPreview()
    }

    Column {
      BasicText("Child:")
      ReplayEmits(deferred)
    }
  }
}

@Composable private fun CounterForPreview(): Int {
  val state = remember { mutableStateOf(0) }
  Row {
    BasicText("Counter: ${state.value}")
    // Simple button.
    BasicText(
        "+", Modifier
        .background(Color.LightGray)
        .clickable(onClick = { state.value += 1 })
    )
  }
  return state.value
}

/**
 * This composable composes its children as normal, but has one special superpower: within its
 * lambda, [DeferredEmitContext.recordEmits] can be used to compose a composition in-place but not
 * actually emit any of its calls to [emit][androidx.compose.runtime.emit] to the underlying
 * [Applier][androidx.compose.runtime.Applier]. Instead, a [DeferredEmission] is returned, which can
 * be emitted to the underlying node by [DeferredEmitContext.ReplayEmits].
 */
// TODO detect when this is called with an already-wrapped applier, and reuse it.
@Composable fun EnableDeferredEmits(children: @Composable DeferredEmitContext.() -> Unit) {
  val composer = currentComposer
  val reference = compositionReference()
  val context = remember { DeferredEmitContext(composer) }
  context.wrapComposition(reference, children)
}

@DeferredEmitDsl
class DeferredEmitContext internal constructor(
  composer: Composer<*>,
) : CompositionLifecycleObserver {

  @OptIn(ComposeCompilerApi::class)
  private val deferredApplier = DeferredApplier(composer.applier)
  private var composition: Composition? = null

  override fun onLeave() {
    composition?.dispose()
  }

  /**
   * Composes [children], but saves any calls to [emit][androidx.compose.runtime.emit] instead of
   * actually emitting them. The saved calls can be later emitted by calling [ReplayEmits] and passing
   * the returned [DeferredEmission].
   *
   * Inside of [children] a new [DeferredEmitContext] is active.
   */
  @Composable
  fun <R> recordEmits(children: @Composable DeferredEmitContext.() -> R): DeferredEmission<R> {
    val deferredEmission = remember { DeferredEmission<R>() }

    @OptIn(ExperimentalComposeApi::class)
    emit<RecordNode, Applier<Any>>(
        ctor = { RecordNode() },
        update = {
          set(Unit) { deferredEmission.recordNode = this }
        },
        children = {
          deferredEmission.value = children()
        }
    )

    return deferredEmission
  }

  /**
   * Emits any saved emissions in [deferredEmission]. The [DeferredEmission] can only be [ReplayEmits]ted
   * once - multiple attempts to emit will throw an exception.
   */
  @Composable fun ReplayEmits(deferredEmission: DeferredEmission<*>) {
    @OptIn(ExperimentalComposeApi::class)
    emit<ReplayNode, Applier<Any>>(
        ctor = { ReplayNode() },
        update = {
          set(deferredEmission.recordNode) { source = it }
        }
    )
  }

  @OptIn(ExperimentalComposeApi::class)
  internal fun wrapComposition(
    reference: CompositionReference,
    children: @Composable (DeferredEmitContext.() -> Unit)
  ) {
    composition = compositionFor(
        key = this,
        applier = deferredApplier,
        parent = reference
    ).apply {
      // This ensures that the first call to the DeferredApplier will be insert(0, RecordNode).
      // TODO might need another layer of indirection here to prevent infinite looping?
      setContent { captureAndEmitRoot(children) }
    }
  }

  @Composable private fun captureAndEmitRoot(children: @Composable DeferredEmitContext.() -> Unit) {
    val emits = recordEmits(children)
    ReplayEmits(emits)
  }
}

/**
 * Represents the value returned from the lambda passed to [DeferredEmitContext.recordEmits] and
 * the emissions performed by that lambda. The value can be read by anyone, any number of times,
 * but the emissions can be replayed only once, by a call to [DeferredEmitContext.ReplayEmits].
 */
@Stable
class DeferredEmission<R> internal constructor() : State<R> {

  private var valueState: MutableState<@UnsafeVariance R>? = null
  internal var recordNode: RecordNode? = null

  override var value: R
    get() = valueState!!.value
    internal set(value) {
      if (valueState == null) {
        valueState = mutableStateOf(value)
      } else {
        valueState!!.value = value
      }
    }
}

internal interface SpecialNode

internal class RecordNode : SpecialNode {

  private data class Segment(
    val isRealChildren: Boolean,
    val children: MutableList<Any?>
  )

  private val children = mutableListOf<Any?>()

  // This will be built by this class, but read and consumed by ReplayNode.
  private val recordedOps = ArrayDeque<Op>()

  sealed class Op {
    data class Insert(
      val index: Int,
      val instance: Any?
    ) : Op()

    data class Move(
      val from: Int,
      val to: Int,
      val count: Int
    ) : Op()

    data class Remove(
      val index: Int,
      val count: Int
    ) : Op()
  }

  fun startRecord() {
    check(recordedOps.isEmpty()) { "Recording already in progress" }
  }

  fun recordInsert(
    index: Int,
    instance: Any?
  ) {
    recordedOps += Insert(index, instance)
  }

  fun recordMove(
    from: Int,
    to: Int,
    count: Int
  ) {
    recordedOps += Move(from, to, count)
  }

  fun recordRemove(
    index: Int,
    count: Int
  ) {
    recordedOps += Remove(index, count)
  }

  /**
   * Returns all [recordedOps], saves them to this node's local cache, and clears the list.
   * This should only be called when the node is replayed.
   *
   * The returned [Op]s' indices will be in the "index space" of the underlying applier
   * (i.e. not include record or replay nodes).
   */
  fun consumeRecordedOps(): List<Op> {
    var lastRealIndex = 0
    val committedOps = mutableListOf<Op>()

    recordedOps.forEach { op ->
      when (op) {
        is Insert -> {
          children.add(op.index, op.instance)

          if (op.instance !is SpecialNode) {
            val realIndex = children.subList(0, op.index)
                .count { it !is SpecialNode }
            committedOps += Insert(realIndex, op.instance)
          }
        }
        is Move -> {
          children.move(op.from, op.to, op.count)
        }
        is Remove -> {
          repeat(op.count) { op.index }
        }
      }
    }

    return committedOps
  }

  private fun <T> List<T>.move(
    from: Int,
    to: Int,
    count: Int
  ) {
    TODO()
  }
}

internal class ReplayNode : SpecialNode {
  var source: RecordNode? = null
}

internal class WrapperNode {

  fun insert(
    index: Int,

      )
}

@OptIn(ExperimentalComposeApi::class)
private class DeferredApplier(
  private val delegate: Applier<out Any?>
) : Applier<Any?> {

  private val root = RecordNode()

  override var current: Any? = root
    private set

  override fun down(node: Any?) {
    TODO("not implemented")
  }

  override fun up() {
    TODO("not implemented")
  }

  override fun insert(
    index: Int,
    instance: Any?
  ) {
    when (val current = current) {
      // Inserting into a recording.
      is RecordNode -> {
        current.insert(index, instance)
      }
      is ReplayNode -> {
        error("Cannot insert into a replay node")
      }
      else -> {

      }
    }

    when (instance) {
      // Recording another nested level down.
      is RecordNode -> {
        TODO()
      }
      // Replaying a RecordNode at this position.
      is ReplayNode -> {
        TODO()
      }
      // Inserting a real node.
      else -> {
      }
    }
  }

  override fun move(
    from: Int,
    to: Int,
    count: Int
  ) {
    TODO("not implemented")
  }

  override fun remove(
    index: Int,
    count: Int
  ) {
    TODO("not implemented")
  }

  override fun clear() {
    TODO("not implemented")
  }
}
