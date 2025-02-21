package com.squareup.workflow1

import kotlin.reflect.typeOf

internal fun <P, S, O> BaseRenderContext<P, S, O>.handler(
  name: String,
  update: Updater<P, S, O>.() -> Unit
): () -> Unit = { actionSink.send(action("eH: $name", update)) }

internal class HandlerBox0 {
  lateinit var delegate: () -> Unit
  fun fire() = delegate()
}

@PublishedApi
internal class HandlerBox1<E> {
  lateinit var delegate: (E) -> Unit
  fun fire(e: E) = delegate(e)
}

@PublishedApi
internal inline fun <P, S, O, reified EventT> BaseRenderContext<P, S, O>.eventHandler1(
  stableEventHandlers: Boolean,
  name: String,
  noinline update: Updater<P, S, O>.(EventT) -> Unit
): (EventT) -> Unit {
  val handler = { e: EventT -> actionSink.send(action("eH: $name") { update(e) }) }
  return if (stableEventHandlers) {
    val box = remember("sH:$name", typeOf<EventT>()) {
      HandlerBox1<EventT>()
    }
    box.delegate = handler
    box::fire
  } else {
    handler
  }
}

@PublishedApi
internal class HandlerBox2<E1, E2> {
  lateinit var delegate: (E1, E2) -> Unit
  fun fire(
    e1: E1,
    e2: E2,
  ) = delegate(e1, e2)
}

@PublishedApi
internal inline fun <P, S, O, reified E1, reified E2> BaseRenderContext<P, S, O>.eventHandler2(
  stableEventHandlers: Boolean,
  name: String,
  noinline update: Updater<P, S, O>.(E1, E2) -> Unit
): (E1, E2) -> Unit {
  val handler = { e1: E1, e2: E2 -> actionSink.send(action("eH: $name") { update(e1, e2) }) }
  return if (stableEventHandlers) {
    val box =
      remember("sH:$name", typeOf<E1>(), typeOf<E2>()) { HandlerBox2<E1, E2>() }
    box.delegate = handler
    box::fire
  } else {
    handler
  }
}

@PublishedApi
internal class HandlerBox3<E1, E2, E3> {
  lateinit var delegate: (E1, E2, E3) -> Unit
  fun fire(
    e1: E1,
    e2: E2,
    e3: E3,
  ) = delegate(e1, e2, e3)
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
  stableEventHandlers: Boolean,
  name: String,
  noinline update: Updater<P, S, O>.(E1, E2, E3) -> Unit
): (E1, E2, E3) -> Unit {
  val handler =
    { e1: E1, e2: E2, e3: E3 -> actionSink.send(action("eH: $name") { update(e1, e2, e3) }) }
  return if (stableEventHandlers) {
    val box =
      remember("sH:$name", typeOf<E1>(), typeOf<E2>(), typeOf<E3>()) { HandlerBox3<E1, E2, E3>() }
    box.delegate = handler
    box::fire
  } else {
    handler
  }
}

@PublishedApi
internal class HandlerBox4<E1, E2, E3, E4> {
  lateinit var delegate: (E1, E2, E3, E4) -> Unit
  fun fire(
    e1: E1,
    e2: E2,
    e3: E3,
    e4: E4,
  ) = delegate(e1, e2, e3, e4)
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
  stableEventHandlers: Boolean,
  name: String,
  noinline update: Updater<P, S, O>.(E1, E2, E3, E4) -> Unit
): (E1, E2, E3, E4) -> Unit {
  val handler = { e1: E1, e2: E2, e3: E3, e4: E4 ->
    actionSink.send(action("eH: $name") { update(e1, e2, e3, e4) })
  }
  return if (stableEventHandlers) {
    val box = remember(
      "sH:$name",
      typeOf<E1>(),
      typeOf<E2>(),
      typeOf<E3>(),
      typeOf<E4>()
    ) { HandlerBox4<E1, E2, E3, E4>() }
    box.delegate = handler
    box::fire
  } else {
    handler
  }
}

@PublishedApi
internal class HandlerBox5<E1, E2, E3, E4, E5> {
  lateinit var delegate: (E1, E2, E3, E4, E5) -> Unit
  fun fire(
    e1: E1,
    e2: E2,
    e3: E3,
    e4: E4,
    e5: E5
  ) = delegate(e1, e2, e3, e4, e5)
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
  stableEventHandlers: Boolean,
  name: String,
  noinline update: Updater<P, S, O>.(E1, E2, E3, E4, E5) -> Unit
): (E1, E2, E3, E4, E5) -> Unit {
  val handler = { e1: E1, e2: E2, e3: E3, e4: E4, e5: E5 ->
    actionSink.send(action("eH: $name") { update(e1, e2, e3, e4, e5) })
  }
  return if (stableEventHandlers) {
    val box = remember(
      "sH:$name",
      typeOf<E1>(),
      typeOf<E2>(),
      typeOf<E3>(),
      typeOf<E4>(),
      typeOf<E5>()
    ) { HandlerBox5<E1, E2, E3, E4, E5>() }
    box.delegate = handler
    box::fire
  } else {
    handler
  }
}

@PublishedApi
internal class HandlerBox6<E1, E2, E3, E4, E5, E6> {
  lateinit var delegate: (E1, E2, E3, E4, E5, E6) -> Unit
  fun fire(
    e1: E1,
    e2: E2,
    e3: E3,
    e4: E4,
    e5: E5,
    e6: E6
  ) = delegate(e1, e2, e3, e4, e5, e6)
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
  stableEventHandlers: Boolean,
  name: String,
  noinline update: Updater<P, S, O>.(E1, E2, E3, E4, E5, E6) -> Unit
): (E1, E2, E3, E4, E5, E6) -> Unit {
  val handler = { e1: E1, e2: E2, e3: E3, e4: E4, e5: E5, e6: E6 ->
    actionSink.send(action("eH: $name") { update(e1, e2, e3, e4, e5, e6) })
  }
  return if (stableEventHandlers) {
    val box = remember(
      "sH:$name",
      typeOf<E1>(),
      typeOf<E2>(),
      typeOf<E3>(),
      typeOf<E4>(),
      typeOf<E5>(),
      typeOf<E6>()
    ) { HandlerBox6<E1, E2, E3, E4, E5, E6>() }
    box.delegate = handler
    box::fire
  } else {
    handler
  }
}

@PublishedApi
internal class HandlerBox7<E1, E2, E3, E4, E5, E6, E7> {
  lateinit var delegate: (E1, E2, E3, E4, E5, E6, E7) -> Unit
  fun fire(
    e1: E1,
    e2: E2,
    e3: E3,
    e4: E4,
    e5: E5,
    e6: E6,
    e7: E7
  ) = delegate(e1, e2, e3, e4, e5, e6, e7)
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
  stableEventHandlers: Boolean,
  name: String,
  noinline update: Updater<P, S, O>.(E1, E2, E3, E4, E5, E6, E7) -> Unit
): (E1, E2, E3, E4, E5, E6, E7) -> Unit {
  val handler = { e1: E1, e2: E2, e3: E3, e4: E4, e5: E5, e6: E6, e7: E7 ->
    actionSink.send(action("eH: $name") { update(e1, e2, e3, e4, e5, e6, e7) })
  }
  return if (stableEventHandlers) {
    val box = remember(
      "sH:$name",
      typeOf<E1>(),
      typeOf<E2>(),
      typeOf<E3>(),
      typeOf<E4>(),
      typeOf<E5>(),
      typeOf<E6>(),
      typeOf<E7>()
    ) { HandlerBox7<E1, E2, E3, E4, E5, E6, E7>() }
    box.delegate = handler
    box::fire
  } else {
    handler
  }
}

@PublishedApi
internal class HandlerBox8<E1, E2, E3, E4, E5, E6, E7, E8> {
  lateinit var delegate: (E1, E2, E3, E4, E5, E6, E7, E8) -> Unit
  fun fire(
    e1: E1,
    e2: E2,
    e3: E3,
    e4: E4,
    e5: E5,
    e6: E6,
    e7: E7,
    e8: E8
  ) = delegate(e1, e2, e3, e4, e5, e6, e7, e8)
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
  stableEventHandlers: Boolean,
  name: String,
  noinline update: Updater<P, S, O>.(E1, E2, E3, E4, E5, E6, E7, E8) -> Unit
): (E1, E2, E3, E4, E5, E6, E7, E8) -> Unit {
  val handler = { e1: E1, e2: E2, e3: E3, e4: E4, e5: E5, e6: E6, e7: E7, e8: E8 ->
    actionSink.send(action("eH: $name") { update(e1, e2, e3, e4, e5, e6, e7, e8) })
  }
  return if (stableEventHandlers) {
    val box = remember(
      "sH:$name",
      typeOf<E1>(),
      typeOf<E2>(),
      typeOf<E3>(),
      typeOf<E4>(),
      typeOf<E5>(),
      typeOf<E6>(),
      typeOf<E7>(),
      typeOf<E8>()
    ) { HandlerBox8<E1, E2, E3, E4, E5, E6, E7, E8>() }
    box.delegate = handler
    box::fire
  } else {
    handler
  }
}

@PublishedApi
internal class HandlerBox9<E1, E2, E3, E4, E5, E6, E7, E8, E9> {
  lateinit var delegate: (E1, E2, E3, E4, E5, E6, E7, E8, E9) -> Unit
  fun fire(
    e1: E1,
    e2: E2,
    e3: E3,
    e4: E4,
    e5: E5,
    e6: E6,
    e7: E7,
    e8: E8,
    e9: E9,
  ) = delegate(e1, e2, e3, e4, e5, e6, e7, e8, e9)
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
  stableEventHandlers: Boolean,
  name: String,
  noinline update: Updater<P, S, O>.(E1, E2, E3, E4, E5, E6, E7, E8, E9) -> Unit
): (E1, E2, E3, E4, E5, E6, E7, E8, E9) -> Unit {
  val handler = { e1: E1, e2: E2, e3: E3, e4: E4, e5: E5, e6: E6, e7: E7, e8: E8, e9: E9 ->
    actionSink.send(action("eH: $name") { update(e1, e2, e3, e4, e5, e6, e7, e8, e9) })
  }
  return if (stableEventHandlers) {
    val box = remember(
      "sH:$name",
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
    box.delegate = handler
    box::fire
  } else {
    handler
  }
}

@PublishedApi
internal class HandlerBox10<E1, E2, E3, E4, E5, E6, E7, E8, E9, E10> {
  lateinit var delegate: (E1, E2, E3, E4, E5, E6, E7, E8, E9, E10) -> Unit
  fun fire(
    e1: E1,
    e2: E2,
    e3: E3,
    e4: E4,
    e5: E5,
    e6: E6,
    e7: E7,
    e8: E8,
    e9: E9,
    e10: E10,
  ) = delegate(e1, e2, e3, e4, e5, e6, e7, e8, e9, e10)
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
  stableEventHandlers: Boolean,
  name: String,
  noinline update: Updater<P, S, O>.(E1, E2, E3, E4, E5, E6, E7, E8, E9, E10) -> Unit
): (E1, E2, E3, E4, E5, E6, E7, E8, E9, E10) -> Unit {
  val handler =
    { e1: E1, e2: E2, e3: E3, e4: E4, e5: E5, e6: E6, e7: E7, e8: E8, e9: E9, e10: E10 ->
      actionSink.send(action("eH: $name") { update(e1, e2, e3, e4, e5, e6, e7, e8, e9, e10) })
    }
  return if (stableEventHandlers) {
    val box = remember(
      "sH:$name",
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
    box.delegate = handler
    box::fire
  } else {
    handler
  }
}
