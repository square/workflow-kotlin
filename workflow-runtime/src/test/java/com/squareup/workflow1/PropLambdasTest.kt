package com.squareup.workflow1

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.staticProperties
import kotlin.reflect.typeOf
import kotlin.test.Test

class PropLambdasTest {

//  @Test fun `lambdas in props`() {
//    data class ChildProps(
//      val data: String,
//      val onClicked: () -> Unit
//    )
//
//    data class ChildRendering(
//      val data: String,
//      val onClicked: () -> Unit
//    )
//
//    data class ParentRendering(val childRendering: ChildRendering)
//
//    val child = Workflow.stateless<ChildProps, Nothing, ChildRendering> { props ->
//      ChildRendering(
//          data = "${props.data}: ${props.onClicked}",
//          onClicked = props.onClicked
//      )
//    }
//
//    val parent = object : StatefulWorkflow<Unit, String, Nothing, ParentRendering>() {
//      override fun initialState(
//        props: Unit,
//        snapshot: Snapshot?
//      ): String = "initial state"
//
//      override fun render(
//        props: Unit,
//        state: String,
//        context: RenderContext
//      ): ParentRendering = ParentRendering(context.renderChild(
//          child, ChildProps(
//          data = state,
//          onClicked = {
//            context.actionSink.send(
//                action { this.state = this.state + "+" })
//          }
//      )))
//
//      override fun snapshotState(state: String): Snapshot? = null
//    }
//
//    val scope = TestCoroutineScope(Job())
//    val renderings = renderWorkflowIn(parent, scope, MutableStateFlow(Unit)) {}
//
//    val firstRendering = renderings.value.rendering.childRendering
//    assertTrue("initial state" in firstRendering.data)
//    firstRendering.onClicked()
//    val secondRendering = renderings.value.rendering.childRendering
//    assertNotSame(firstRendering, secondRendering)
//    assertNotEquals(firstRendering, secondRendering)
//    assertNotSame(firstRendering.onClicked, secondRendering.onClicked)
//    assertNotEquals(firstRendering.onClicked, secondRendering.onClicked)
//
//  }

  /*
  Compiler bugs:

  - typeOf<T>() where T : Function<*> and is a suspend function
  - "${f()}" where f is a delegated property of a Function<*>
   */

  @Test fun t2() {
    val str1 by remember { "foo" }
    val str2 by remember { a: String -> "bar: $a" }
    val str3 by remember { a: String, b: String -> "baz: $a, $b" }

    println("str1: ${str1()}")
    println("str2: ${str2("one")}")
    println("str3: ${str3("one", "two")}")
  }

  @Test fun sustest() {
//    val suspendStr by remember(suspend { "suspend foo" })
    val suspendStr = rememberSuspend(suspend { delay(1000); "suspend foo" })
    print("suspendStr: ")
    println(runBlocking { suspendStr() })

//    val suspendStr1: suspend (String) -> String = rememberSuspend { delay(1000); "suspend foo" }
//    print("suspendStr1: ")
//    println(runBlocking { suspendStr1("one") })
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T : Function<R>, R> rememberSuspend(function: T): T {
    println("suspend fun: $function")
    require(function is Continuation<*>) { "Function must be suspend" }
    val wrapped = wrap(function, null) { args, invoke ->
      val cont = args.last() as Continuation<Any?>
      println("args: $args")
      cont.resume("neo")
      return@wrap COROUTINE_SUSPENDED as R
//      invoke()
//          .also {
//            println("retval: $it")
//
//            if (function is Continuation<*>) {
//              println("context: " + function.context)
//            }
//          }
    }
    return wrapped as T
  }

  private val COROUTINE_SUSPENDED: Any?
    get() {
      val className = "kotlin.coroutines.intrinsics.IntrinsicsKt"
      val klass = Class.forName(className).kotlin
      val property = klass.staticProperties.single { it.name == "COROUTINE_SUSPENDED" }
      return property.get()
    }

  @OptIn(ExperimentalStdlibApi::class)
  @Suppress("UNCHECKED_CAST")
  private inline fun <reified T : Function<R>, R> remember(function: T): ReadOnlyProperty<Nothing?, T> =
    rememberImpl(function, T::class, typeOf<T>())

  @OptIn(ExperimentalStdlibApi::class)
  @Suppress("UNCHECKED_CAST")
  private fun <T : Function<R>, R> rememberImpl(
    function: T,
    classOfT: KClass<T>,
    typeOfT: KType
  ): ReadOnlyProperty<Nothing?, T> = object : ReadOnlyProperty<Nothing?, T> {
    override fun getValue(
      thisRef: Nothing?,
      property: KProperty<*>
    ): T = wrap(function, typeOfT) { args, invoke ->
      val argsStr = args.joinToString()
      invoke().also { returnVal ->
        System.err.println("INVOKED ${property.name}($argsStr) -> $returnVal")
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T : Function<R>, R> wrap(
    function: T,
    typeOfT: KType?,
    wrapper: (args: List<*>, invoke: () -> R) -> R
  ): T = when (function) {
    is Function0<*> -> {
      { wrapper(emptyList<Nothing>()) { (function as Function0<R>)() } } as T
    }
    is Function1<*, *> -> {
      { a: Any? -> wrapper(listOf(a)) { (function as Function1<Any?, R>)(a) } } as T
    }
    is Function2<*, *, *> -> {
      { a: Any?, b: Any? ->
        wrapper(listOf(a, b)) { (function as Function2<Any?, Any?, R>)(a, b) }
      } as T
    }
    is Function3<*, *, *, *> -> {
      { a: Any?, b: Any?, c: Any? ->
        wrapper(listOf(a, b, c)) { (function as Function3<Any?, Any?, Any?, R>)(a, b, c) }
      } as T
    }
    else -> throw IllegalArgumentException("Functions of type $typeOfT are not supported")
  }
//  private fun <T> remember(value: Function1<*, T>) = value
//  private fun <T> remember(value: Function2<*, *, T>) = value

//  private fun <T> remember(value: () -> T): T {
//
//  }
}


























