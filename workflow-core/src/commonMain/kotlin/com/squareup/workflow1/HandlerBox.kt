package com.squareup.workflow1

import kotlin.reflect.typeOf

internal class HandlerBox0 {
  lateinit var handler: () -> Unit
  val stableHandler: () -> Unit = { handler() }
}

internal fun <P, S, O> BaseRenderContext<P, S, O>.eventHandler0(
  name: String,
  remember: Boolean,
  update: Updater<P, S, O>.() -> Unit
): () -> Unit {
  val handler = { actionSink.send(action("eH: $name", update)) }
  return if (remember) {
    val box = remember(name) { HandlerBox0() }
    box.handler = handler
    box.stableHandler
  } else {
    handler
  }
}

@PublishedApi
internal class HandlerBox1<E> {
  lateinit var handler: (E) -> Unit
  val stableHandler: (E) -> Unit = { handler(it) }
}

@PublishedApi
internal inline fun <P, S, O, reified EventT> BaseRenderContext<P, S, O>.eventHandler1(
  name: String,
  remember: Boolean,
  noinline update: Updater<P, S, O>.(EventT) -> Unit
): (EventT) -> Unit {
  val handler = { e: EventT -> actionSink.send(action("eH: $name") { update(e) }) }
  return if (remember) {
    val box = remember(name, typeOf<EventT>()) { HandlerBox1<EventT>() }
    box.handler = handler
    box.stableHandler
  } else {
    handler
  }
}

@PublishedApi
internal class HandlerBox2<E1, E2> {
  lateinit var handler: (E1, E2) -> Unit
  val stableHandler: (E1, E2) -> Unit = { e1, e2 -> handler(e1, e2) }
}

@PublishedApi
internal inline fun <P, S, O, reified E1, reified E2> BaseRenderContext<P, S, O>.eventHandler2(
  name: String,
  remember: Boolean,
  noinline update: Updater<P, S, O>.(E1, E2) -> Unit
): (E1, E2) -> Unit {
  val handler = { e1: E1, e2: E2 -> actionSink.send(action("eH: $name") { update(e1, e2) }) }
  return if (remember) {
    val box = remember(name, typeOf<E1>(), typeOf<E2>()) { HandlerBox2<E1, E2>() }
    box.handler = handler
    box.stableHandler
  } else {
    handler
  }
}

@PublishedApi
internal class HandlerBox3<E1, E2, E3> {
  lateinit var handler: (E1, E2, E3) -> Unit
  val stableHandler: (E1, E2, E3) -> Unit = { e1, e2, e3 -> handler(e1, e2, e3) }
}

@PublishedApi
internal inline fun <
  P,
  S,
  O,
  reified E1,
  reified E2,
  reified E3,
  > BaseRenderContext<P, S, O>.eventHandler3(
  name: String,
  remember: Boolean,
  noinline update: Updater<P, S, O>.(E1, E2, E3) -> Unit
): (E1, E2, E3) -> Unit {
  val handler =
    { e1: E1, e2: E2, e3: E3 -> actionSink.send(action("eH: $name") { update(e1, e2, e3) }) }
  return if (remember) {
    val box =
      remember(name, typeOf<E1>(), typeOf<E2>(), typeOf<E3>()) { HandlerBox3<E1, E2, E3>() }
    box.handler = handler
    box.stableHandler
  } else {
    handler
  }
}

@PublishedApi
internal class HandlerBox4<E1, E2, E3, E4> {
  lateinit var handler: (E1, E2, E3, E4) -> Unit
  val stableHandler: (E1, E2, E3, E4) -> Unit = { e1, e2, e3, e4 -> handler(e1, e2, e3, e4) }
}

@PublishedApi
internal inline fun <
  P,
  S,
  O,
  reified E1,
  reified E2,
  reified E3,
  reified E4,
  > BaseRenderContext<P, S, O>.eventHandler4(
  name: String,
  remember: Boolean,
  noinline update: Updater<P, S, O>.(E1, E2, E3, E4) -> Unit
): (E1, E2, E3, E4) -> Unit {
  val handler = { e1: E1, e2: E2, e3: E3, e4: E4 ->
    actionSink.send(action("eH: $name") { update(e1, e2, e3, e4) })
  }
  return if (remember) {
    val box = remember(
      name,
      typeOf<E1>(),
      typeOf<E2>(),
      typeOf<E3>(),
      typeOf<E4>()
    ) { HandlerBox4<E1, E2, E3, E4>() }
    box.handler = handler
    box.stableHandler
  } else {
    handler
  }
}

@PublishedApi
internal class HandlerBox5<E1, E2, E3, E4, E5> {
  lateinit var handler: (E1, E2, E3, E4, E5) -> Unit
  val stableHandler: (E1, E2, E3, E4, E5) -> Unit =
    { e1, e2, e3, e4, e5 -> handler(e1, e2, e3, e4, e5) }
}

@PublishedApi
internal inline fun <
  P,
  S,
  O,
  reified E1,
  reified E2,
  reified E3,
  reified E4,
  reified E5,
  > BaseRenderContext<P, S, O>.eventHandler5(
  name: String,
  remember: Boolean,
  noinline update: Updater<P, S, O>.(E1, E2, E3, E4, E5) -> Unit
): (E1, E2, E3, E4, E5) -> Unit {
  val handler = { e1: E1, e2: E2, e3: E3, e4: E4, e5: E5 ->
    actionSink.send(action("eH: $name") { update(e1, e2, e3, e4, e5) })
  }
  return if (remember) {
    val box = remember(
      name,
      typeOf<E1>(),
      typeOf<E2>(),
      typeOf<E3>(),
      typeOf<E4>(),
      typeOf<E5>()
    ) { HandlerBox5<E1, E2, E3, E4, E5>() }
    box.handler = handler
    box.stableHandler
  } else {
    handler
  }
}

@PublishedApi
internal class HandlerBox6<E1, E2, E3, E4, E5, E6> {
  lateinit var handler: (E1, E2, E3, E4, E5, E6) -> Unit
  val stableHandler: (E1, E2, E3, E4, E5, E6) -> Unit =
    { e1, e2, e3, e4, e5, e6 -> handler(e1, e2, e3, e4, e5, e6) }
}

@PublishedApi
internal inline fun <
  P,
  S,
  O,
  reified E1,
  reified E2,
  reified E3,
  reified E4,
  reified E5,
  reified E6,
  > BaseRenderContext<P, S, O>.eventHandler6(
  name: String,
  remember: Boolean,
  noinline update: Updater<P, S, O>.(E1, E2, E3, E4, E5, E6) -> Unit
): (E1, E2, E3, E4, E5, E6) -> Unit {
  val handler = { e1: E1, e2: E2, e3: E3, e4: E4, e5: E5, e6: E6 ->
    actionSink.send(action("eH: $name") { update(e1, e2, e3, e4, e5, e6) })
  }
  return if (remember) {
    val box = remember(
      name,
      typeOf<E1>(),
      typeOf<E2>(),
      typeOf<E3>(),
      typeOf<E4>(),
      typeOf<E5>(),
      typeOf<E6>()
    ) { HandlerBox6<E1, E2, E3, E4, E5, E6>() }
    box.handler = handler
    box.stableHandler
  } else {
    handler
  }
}

@PublishedApi
internal class HandlerBox7<E1, E2, E3, E4, E5, E6, E7> {
  lateinit var handler: (E1, E2, E3, E4, E5, E6, E7) -> Unit
  val stableHandler: (E1, E2, E3, E4, E5, E6, E7) -> Unit =
    { e1, e2, e3, e4, e5, e6, e7 -> handler(e1, e2, e3, e4, e5, e6, e7) }
}

@PublishedApi
internal inline fun <
  P,
  S,
  O,
  reified E1,
  reified E2,
  reified E3,
  reified E4,
  reified E5,
  reified E6,
  reified E7,
  > BaseRenderContext<P, S, O>.eventHandler7(
  name: String,
  remember: Boolean,
  noinline update: Updater<P, S, O>.(E1, E2, E3, E4, E5, E6, E7) -> Unit
): (E1, E2, E3, E4, E5, E6, E7) -> Unit {
  val handler = { e1: E1, e2: E2, e3: E3, e4: E4, e5: E5, e6: E6, e7: E7 ->
    actionSink.send(action("eH: $name") { update(e1, e2, e3, e4, e5, e6, e7) })
  }
  return if (remember) {
    val box = remember(
      name,
      typeOf<E1>(),
      typeOf<E2>(),
      typeOf<E3>(),
      typeOf<E4>(),
      typeOf<E5>(),
      typeOf<E6>(),
      typeOf<E7>()
    ) { HandlerBox7<E1, E2, E3, E4, E5, E6, E7>() }
    box.handler = handler
    box.stableHandler
  } else {
    handler
  }
}

@PublishedApi
internal class HandlerBox8<E1, E2, E3, E4, E5, E6, E7, E8> {
  lateinit var handler: (E1, E2, E3, E4, E5, E6, E7, E8) -> Unit
  val stableHandler: (E1, E2, E3, E4, E5, E6, E7, E8) -> Unit =
    { e1, e2, e3, e4, e5, e6, e7, e8 -> handler(e1, e2, e3, e4, e5, e6, e7, e8) }
}

@PublishedApi
internal inline fun <
  P,
  S,
  O,
  reified E1,
  reified E2,
  reified E3,
  reified E4,
  reified E5,
  reified E6,
  reified E7,
  reified E8,
  > BaseRenderContext<P, S, O>.eventHandler8(
  name: String,
  remember: Boolean,
  noinline update: Updater<P, S, O>.(E1, E2, E3, E4, E5, E6, E7, E8) -> Unit
): (E1, E2, E3, E4, E5, E6, E7, E8) -> Unit {
  val handler = { e1: E1, e2: E2, e3: E3, e4: E4, e5: E5, e6: E6, e7: E7, e8: E8 ->
    actionSink.send(action("eH: $name") { update(e1, e2, e3, e4, e5, e6, e7, e8) })
  }
  return if (remember) {
    val box = remember(
      name,
      typeOf<E1>(),
      typeOf<E2>(),
      typeOf<E3>(),
      typeOf<E4>(),
      typeOf<E5>(),
      typeOf<E6>(),
      typeOf<E7>(),
      typeOf<E8>()
    ) { HandlerBox8<E1, E2, E3, E4, E5, E6, E7, E8>() }
    box.handler = handler
    box.stableHandler
  } else {
    handler
  }
}

@PublishedApi
internal class HandlerBox9<E1, E2, E3, E4, E5, E6, E7, E8, E9> {
  lateinit var handler: (E1, E2, E3, E4, E5, E6, E7, E8, E9) -> Unit
  val stableHandler: (E1, E2, E3, E4, E5, E6, E7, E8, E9) -> Unit =
    { e1, e2, e3, e4, e5, e6, e7, e8, e9 -> handler(e1, e2, e3, e4, e5, e6, e7, e8, e9) }
}

@PublishedApi
internal inline fun <
  P,
  S,
  O,
  reified E1,
  reified E2,
  reified E3,
  reified E4,
  reified E5,
  reified E6,
  reified E7,
  reified E8,
  reified E9,
  > BaseRenderContext<P, S, O>.eventHandler9(
  name: String,
  remember: Boolean,
  noinline update: Updater<P, S, O>.(E1, E2, E3, E4, E5, E6, E7, E8, E9) -> Unit
): (E1, E2, E3, E4, E5, E6, E7, E8, E9) -> Unit {
  val handler = { e1: E1, e2: E2, e3: E3, e4: E4, e5: E5, e6: E6, e7: E7, e8: E8, e9: E9 ->
    actionSink.send(action("eH: $name") { update(e1, e2, e3, e4, e5, e6, e7, e8, e9) })
  }
  return if (remember) {
    val box = remember(
      name,
      typeOf<E1>(),
      typeOf<E2>(),
      typeOf<E3>(),
      typeOf<E4>(),
      typeOf<E5>(),
      typeOf<E6>(),
      typeOf<E7>(),
      typeOf<E8>(),
      typeOf<E9>()
    ) { HandlerBox9<E1, E2, E3, E4, E5, E6, E7, E8, E9>() }
    box.handler = handler
    box.stableHandler
  } else {
    handler
  }
}

@PublishedApi
internal class HandlerBox10<E1, E2, E3, E4, E5, E6, E7, E8, E9, E10> {
  lateinit var handler: (E1, E2, E3, E4, E5, E6, E7, E8, E9, E10) -> Unit
  val stableHandler: (E1, E2, E3, E4, E5, E6, E7, E8, E9, E10) -> Unit =
    { e1, e2, e3, e4, e5, e6, e7, e8, e9, e10 -> handler(e1, e2, e3, e4, e5, e6, e7, e8, e9, e10) }
}

@PublishedApi
internal inline fun <
  P,
  S,
  O,
  reified E1,
  reified E2,
  reified E3,
  reified E4,
  reified E5,
  reified E6,
  reified E7,
  reified E8,
  reified E9,
  reified E10,
  > BaseRenderContext<P, S, O>.eventHandler10(
  name: String,
  remember: Boolean,
  noinline update: Updater<P, S, O>.(E1, E2, E3, E4, E5, E6, E7, E8, E9, E10) -> Unit
): (E1, E2, E3, E4, E5, E6, E7, E8, E9, E10) -> Unit {
  val handler =
    { e1: E1, e2: E2, e3: E3, e4: E4, e5: E5, e6: E6, e7: E7, e8: E8, e9: E9, e10: E10 ->
      actionSink.send(action("eH: $name") { update(e1, e2, e3, e4, e5, e6, e7, e8, e9, e10) })
    }
  return if (remember) {
    val box = remember(
      name,
      typeOf<E1>(),
      typeOf<E2>(),
      typeOf<E3>(),
      typeOf<E4>(),
      typeOf<E5>(),
      typeOf<E6>(),
      typeOf<E7>(),
      typeOf<E8>(),
      typeOf<E9>(),
      typeOf<E10>()
    ) { HandlerBox10<E1, E2, E3, E4, E5, E6, E7, E8, E9, E10>() }
    box.handler = handler
    box.stableHandler
  } else {
    handler
  }
}
