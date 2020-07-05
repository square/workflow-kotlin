package com.squareup.workflow

import com.squareup.workflow.TestWorkflow.TestRendering
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.consumeEach
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private val NO_PROPS = Any()

/**
 * TODO write documentation
 */
abstract class ImplicitWorkflow<PropsT, RenderingT> : Workflow<PropsT, Nothing, RenderingT> {

  abstract class Ctx {
    abstract fun <P, O, R> renderChild(
      child: Workflow<P, O, R>,
      props: P,
      key: String = "",
      onOutput: (O) -> Unit
    ): R

    abstract fun runningSideEffect(
      key: String = "",
      sideEffect: suspend () -> Unit
    )
  }

  private var isInitialized = false

  private var _props: Any? = NO_PROPS
  protected val props: PropsT
    get() = _props.let {
      if (it === NO_PROPS) error("TODO") else {
        notifyPropsRead()
        it as PropsT
      }
    }

  private fun notifyPropsRead() {
    TODO()
  }

  private fun notifyStateRead(stateProp: KProperty<*>) {
    TODO()
  }

  private fun notifyStateWrite(stateProp: KProperty<*>) {
    trigger.offer(Unit)
  }

  protected fun <T> props(getter: (PropsT) -> T): ReadOnlyProperty<ImplicitWorkflow<PropsT, RenderingT>, T> =
    object : ReadOnlyProperty<ImplicitWorkflow<PropsT, RenderingT>, T> {
      override fun getValue(
        thisRef: ImplicitWorkflow<PropsT, RenderingT>,
        property: KProperty<*>
      ): T = getter(props)
    }

  private val trigger = Channel<Unit>(CONFLATED)
  private val states = mutableMapOf<KProperty<*>, Any?>()

  protected fun <T> state(init: () -> T): ReadWriteProperty<ImplicitWorkflow<*, *>, T> =
    object : ReadWriteProperty<Any?, T> {
      override fun getValue(
        thisRef: Any?,
        property: KProperty<*>
      ): T {
        val value = if (property !in states) {
          // TODO record prop and other state reads
          val initialState = init()
          states[property] = initialState
        } else states.getValue(property)
        notifyStateRead(property)
        return value as T
      }

      override fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: T
      ) {
        notifyStateWrite(property)
        states[property] = value
      }
    }

  abstract fun Ctx.render(): RenderingT

  private var attached = false

  @OptIn(ExperimentalCoroutinesApi::class, ExperimentalWorkflowApi::class)
  private val impl =
    object : StatefulWorkflow<PropsT, ImplicitWorkflow<PropsT, RenderingT>, Nothing, RenderingT>() {
      override fun initialState(
        props: PropsT,
        snapshot: Snapshot?
      ): ImplicitWorkflow<PropsT, RenderingT> {
        check(!attached) { "ImplicitWorkflow instances can only be used once." }
        attached = true
        _props = props
        return this@ImplicitWorkflow
      }

      override fun onPropsChanged(
        old: PropsT,
        new: PropsT,
        state: ImplicitWorkflow<PropsT, RenderingT>
      ): ImplicitWorkflow<PropsT, RenderingT> {
        _props = new
        return super.onPropsChanged(old, new, state)
      }

      override fun render(
        props: PropsT,
        state: ImplicitWorkflow<PropsT, RenderingT>,
        context: RenderContext<PropsT, ImplicitWorkflow<PropsT, RenderingT>, Nothing>
      ): RenderingT {
        check(props == _props)

        context.runningSideEffect("invalidate") {
          trigger.consumeEach {
            // Trigger a re-render.
            context.actionSink.sendAndAwaitApplication(WorkflowAction.noAction())
          }
        }

        val ctx = object : Ctx() {
          override fun <P, O, R> renderChild(
            child: Workflow<P, O, R>,
            props: P,
            key: String,
            onOutput: (O) -> Unit
          ): R = context.renderChild(child, props, key) { output ->
            onOutput(output)
            WorkflowAction.noAction()
          }

          override fun runningSideEffect(
            key: String,
            sideEffect: suspend () -> Unit
          ) = context.runningSideEffect("ctx-$key", sideEffect)
        }

        // Clear any requests to re-render.
        trigger.poll()
        return ctx.render()
      }

      override fun snapshotState(state: ImplicitWorkflow<PropsT, RenderingT>): Snapshot? {
        TODO()
      }
    }

  override fun asStatefulWorkflow(): StatefulWorkflow<PropsT, *, Nothing, RenderingT> {
    isInitialized = true
    return impl
  }
}

class TestWorkflow : ImplicitWorkflow<String, TestRendering>() {

  data class TestRendering(
    val props: String,
    val counter: String,
    val onClick: () -> Unit
  )

  private val initialProps by props { it }

  private var counter by state {
    println("props: $props")
    0
  }

  override fun Ctx.render(): TestRendering {
    return TestRendering(
        props = props,
        counter = counter.toString(),
        onClick = { counter++ }
    )
  }
}
