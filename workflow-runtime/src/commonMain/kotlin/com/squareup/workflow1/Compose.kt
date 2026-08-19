package com.squareup.workflow1

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.reflect.KType
import kotlin.reflect.typeOf

interface ViewModel
abstract class ViewModelRef<out T : ViewModel> internal constructor(
  val type: KType
) {
  internal abstract val viewModels: StateFlow<T>

  override fun toString(): String =
    "ViewModelRef<$type>@${hashCode().toHexString()}(currentViewModel=${viewModels.value})"
}

fun interface ViewModelProducer<out T : ViewModel> {
  context(_: ViewModelProducerContext)
  fun produce(children: List<ViewModelRef<*>>): T
}

abstract class ViewModelProducerContext internal constructor() {
}

abstract class ViewModelResolver internal constructor() {
  internal abstract fun <T : ViewModel> resolveViewModelAsStateFlow(ref: ViewModelRef<T>): StateFlow<T>
}

context(resolver: ViewModelResolver)
fun <T : ViewModel> ViewModelRef<T>.resolveAsStateFlow(): StateFlow<T> =
  resolver.resolveViewModelAsStateFlow(this)

class RootViewModel(
  val viewModels: List<ViewModelRef<*>>
) : ViewModel

private object RootViewModelProducer : ViewModelProducer<RootViewModel> {
  context(_: ViewModelProducerContext)
  override fun produce(children: List<ViewModelRef<*>>): RootViewModel = RootViewModel(children)
}

fun present(
  scope: CoroutineScope,
  presenter: @Composable () -> Unit
): Pair<ViewModelRef<RootViewModel>, ViewModelResolver> {
  val recomposer = Recomposer(effectCoroutineContext = scope.coroutineContext)
  val owner = PresenterOwnerImpl(scope)
  val rootPresenterNode = PresenterNode(
    producer = RootViewModelProducer,
    type = typeOf<RootViewModelProducer>(),
    owner = owner,
  )
  val applier = PresenterApplier(rootPresenterNode, owner)
  val composition = Composition(applier, recomposer)

  scope.launch {
    owner.start()
    try {
      recomposer.runRecomposeAndApplyChanges()
    } finally {
      owner.stop()
    }
  }

  composition.setContent(presenter)

  val resolver = object : ViewModelResolver() {
    override fun <T : ViewModel> resolveViewModelAsStateFlow(ref: ViewModelRef<T>): StateFlow<T> =
      ref.viewModels
  }

  @Suppress("UNCHECKED_CAST")
  return Pair(
    rootPresenterNode as ViewModelRef<RootViewModel>,
    resolver
  )
}

@Suppress("NOTHING_TO_INLINE")
@Composable
inline fun Present(
  viewModel: ViewModel,
  tag: Any? = null,
) {
  Presenter(viewModelProducer = { viewModel }, tag = tag)
}

@Suppress("NOTHING_TO_INLINE")
@Composable
inline fun Presenter(
  viewModelProducer: ViewModelProducer<ViewModel>,
  tag: Any? = null,
) {
  Presenter(viewModelProducer = viewModelProducer, tag = tag) {}
}

@Composable
inline fun <reified T : ViewModel> Presenter(
  viewModelProducer: ViewModelProducer<T>,
  tag: Any? = null,
  content: @Composable () -> Unit
) {
  val type = typeOf<T>()
  key(type) {
    ComposeNode<PresenterNode, PresenterApplier>(
      factory = { PresenterNode(viewModelProducer, type) },
      update = {
        update(viewModelProducer) {
          this.producer = it
          invalidate()
        }
        set(tag) {
          this.tag = it
          invalidate()
        }
      },
      content = content
    )
  }
}

/**
 * Emits a presenter node, with [content] as a child, and using the return value of [content] as the
 * node's view model.
 */
@Composable
fun ProducingPresenter(
  tag: Any? = null,
  content: @Composable () -> ViewModel
) {
  TODO()
}

/**
 * Emits a presenter node and returns a ref that can be stored in a view model and will resolve to
 * the node's view model. The emitted node will be excluded from the parent node's list of children
 * inside the [ViewModelProducer.produce] function.
 *
 * Only makes sense to call from in a [ProducingPresenter].
 */
@Composable
fun <T : ViewModel> deferPresenter(
  viewModelProducer: ViewModelProducer<T>,
  content: @Composable () -> Unit
): ViewModelRef<T> {
  TODO()
}

internal interface PresenterOwner {
  fun onInvalidateNode(node: PresenterNode)
  fun onEndChanges()
}

@PublishedApi
internal class PresenterNode(
  @PublishedApi
  internal var producer: ViewModelProducer<ViewModel>,
  type: KType,
  private var owner: PresenterOwner? = null,
) : ViewModelRef<ViewModel>(type) {
  internal var _viewModels = MutableStateFlow<ViewModel>(NoViewModel)
  var parent: PresenterNode? = null
    set(value) {
      field = value
      owner = value?.owner
    }
  var disposed = false
    private set
  var tag: Any? = null
  val children = ArrayList<PresenterNode>(initialCapacity = 3)
  override val viewModels: StateFlow<ViewModel>
    get() = _viewModels.asStateFlow()

  fun invalidate() {
    val owner = checkNotNull(owner) {
      "invalidate() called on orphan node"
    }
    owner.onInvalidateNode(this)
  }

  fun dispose() {
    disposed = true
  }

  fun disposeChildren() {
    children.forEach {
      it.disposeChildren()
      it.dispose()
      it.parent = null
    }
    children.clear()
  }
}

private data object NoViewModel : ViewModel

private class PresenterOwnerImpl(
  private val scope: CoroutineScope
) : PresenterOwner, ViewModelProducerContext() {
  private var dirtyNodes = mutableSetOf<PresenterNode>()
  private val snapshotObserver = SnapshotStateObserver(
    // This is called every time a snapshot application changes one or more states read by
    // ViewModelProducers. sendNotifications is a function that will invoke the
    // onNodeStateChanged callbacks for each node that had a state change.
    onChangedExecutor = { sendNotifications ->
      scope.launch {
        sendNotifications()
        processDirtyNodes()
      }
    }
  )
  private val onNodeStateChanged: (PresenterNode) -> Unit = { it.invalidate() }

  fun start() {
    snapshotObserver.start()
  }

  fun stop() {
    snapshotObserver.stop()
  }

  override fun onInvalidateNode(node: PresenterNode) {
    // Nodes can be invalidated in two ways:
    //  - During apply, all invalidated nodes are processed by onEndChanges.
    //  - By state change, all invalidated nodes are processed by snapshotObserver
    //    onChangedExecutor.
    dirtyNodes += node
  }

  override fun onEndChanges() {
    snapshotObserver.clearIf { (it as PresenterNode).disposed }
    processDirtyNodes()
  }

  private fun processDirtyNodes() {
    val localDirtyNodes = dirtyNodes
    dirtyNodes = mutableSetOf()
    localDirtyNodes.forEach { node ->
      if (node.disposed) return@forEach
      snapshotObserver.observeReads(
        scope = node,
        onValueChangedForScope = onNodeStateChanged
      ) {
        node._viewModels.value = node.producer.produce(node.children)
      }
    }
    localDirtyNodes.clear()
  }
}

@PublishedApi
internal class PresenterApplier(
  root: PresenterNode,
  private val owner: PresenterOwner,
) : AbstractApplier<PresenterNode>(root) {
  override fun insertTopDown(
    index: Int,
    instance: PresenterNode
  ) {
    current.children.add(index, instance)
    instance.parent = current
    // Must be called after assigning parent so it has access to owner.
    instance.invalidate()
    current.invalidate()
  }

  override fun insertBottomUp(
    index: Int,
    instance: PresenterNode
  ) = Unit

  override fun remove(
    index: Int,
    count: Int
  ) {
    val toRemove = current.children.subList(index, index + count)
    toRemove.forEach {
      it.dispose()
      it.parent = null
    }
    toRemove.clear()
    current.invalidate()
  }

  override fun move(
    from: Int,
    to: Int,
    count: Int
  ) {
    current.children.move(from, to, count)
    current.invalidate()
  }

  override fun onClear() {
    root.disposeChildren()
    root.invalidate()
  }

  override fun onEndChanges() {
    owner.onEndChanges()
  }
}
