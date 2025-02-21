package com.squareup.workflow1

internal fun <P, S, O> BaseRenderContext<P, S, O>.handler(
  name: String,
  update: Updater<P, S, O>.() -> Unit
): () -> Unit = { actionSink.send(action("eH: $name", update)) }

internal class HandlerBox0 {
  lateinit var delegate: () -> Unit
  fun fire() = delegate()
}

@PublishedApi
internal fun <P, S, O, E> BaseRenderContext<P, S, O>.handler(
  name: String,
  update: Updater<P, S, O>.(E) -> Unit
): (E) -> Unit = { e -> actionSink.send(action("eH: $name") { update(e) }) }

@PublishedApi
internal class HandlerBox1<E> {
  lateinit var delegate: (E) -> Unit
  fun fire(e: E) = delegate(e)
}

@PublishedApi
internal fun <P, S, O, E1, E2> BaseRenderContext<P, S, O>.handler(
  name: String,
  update: Updater<P, S, O>.(E1, E2) -> Unit
): (E1, E2) -> Unit = { e1, e2 -> actionSink.send(action("eH: $name") { update(e1, e2) }) }

@PublishedApi
internal class HandlerBox2<E1, E2> {
  lateinit var delegate: (E1, E2) -> Unit
  fun fire(
    e1: E1,
    e2: E2
  ) = delegate(e1, e2)
}

@PublishedApi
internal fun <P, S, O, E1, E2, E3> BaseRenderContext<P, S, O>.handler(
  name: String,
  update: Updater<P, S, O>.(E1, E2, E3) -> Unit
): (E1, E2, E3) -> Unit = { e1, e2, e3 ->
  actionSink.send(action("eH: $name") { update(e1, e2, e3) })
}

@PublishedApi
internal class HandlerBox3<E1, E2, E3> {
  lateinit var delegate: (E1, E2, E3) -> Unit
  fun fire(
    e1: E1,
    e2: E2,
    e3: E3
  ) = delegate(e1, e2, e3)
}

@PublishedApi
internal fun <P, S, O, E1, E2, E3, E4> BaseRenderContext<P, S, O>.handler(
  name: String,
  update: Updater<P, S, O>.(E1, E2, E3, E4) -> Unit
): (E1, E2, E3, E4) -> Unit = { e1, e2, e3, e4 ->
  actionSink.send(action("eH: $name") { update(e1, e2, e3, e4) })
}

@PublishedApi
internal class HandlerBox4<E1, E2, E3, E4> {
  lateinit var delegate: (E1, E2, E3, E4) -> Unit
  fun fire(
    e1: E1,
    e2: E2,
    e3: E3,
    e4: E4
  ) = delegate(e1, e2, e3, e4)
}

@PublishedApi
internal fun <P, S, O, E1, E2, E3, E4, E5> BaseRenderContext<P, S, O>.handler(
  name: String,
  update: Updater<P, S, O>.(E1, E2, E3, E4, E5) -> Unit
): (E1, E2, E3, E4, E5) -> Unit = { e1, e2, e3, e4, e5 ->
  actionSink.send(action("eH: $name") { update(e1, e2, e3, e4, e5) })
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
internal fun <P, S, O, E1, E2, E3, E4, E5, E6> BaseRenderContext<P, S, O>.handler(
  name: String,
  update: Updater<P, S, O>.(E1, E2, E3, E4, E5, E6) -> Unit
): (E1, E2, E3, E4, E5, E6) -> Unit = { e1, e2, e3, e4, e5, e6 ->
  actionSink.send(action("eH: $name") { update(e1, e2, e3, e4, e5, e6) })
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
internal fun <P, S, O, E1, E2, E3, E4, E5, E6, E7> BaseRenderContext<P, S, O>.handler(
  name: String,
  update: Updater<P, S, O>.(E1, E2, E3, E4, E5, E6, E7) -> Unit
): (E1, E2, E3, E4, E5, E6, E7) -> Unit = { e1, e2, e3, e4, e5, e6, e7 ->
  actionSink.send(action("eH: $name") { update(e1, e2, e3, e4, e5, e6, e7) })
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
internal fun <P, S, O, E1, E2, E3, E4, E5, E6, E7, E8> BaseRenderContext<P, S, O>.handler(
  name: String,
  update: Updater<P, S, O>.(E1, E2, E3, E4, E5, E6, E7, E8) -> Unit
): (E1, E2, E3, E4, E5, E6, E7, E8) -> Unit = { e1, e2, e3, e4, e5, e6, e7, e8 ->
  actionSink.send(action("eH: $name") { update(e1, e2, e3, e4, e5, e6, e7, e8) })
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
internal fun <P, S, O, E1, E2, E3, E4, E5, E6, E7, E8, E9> BaseRenderContext<P, S, O>.handler(
  name: String,
  update: Updater<P, S, O>.(E1, E2, E3, E4, E5, E6, E7, E8, E9) -> Unit
): (E1, E2, E3, E4, E5, E6, E7, E8, E9) -> Unit = { e1, e2, e3, e4, e5, e6, e7, e8, e9 ->
  actionSink.send(action("eH: $name") { update(e1, e2, e3, e4, e5, e6, e7, e8, e9) })
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
internal fun <P, S, O, E1, E2, E3, E4, E5, E6, E7, E8, E9, E10> BaseRenderContext<P, S, O>.handler(
  name: String,
  update: Updater<P, S, O>.(E1, E2, E3, E4, E5, E6, E7, E8, E9, E10) -> Unit
): (E1, E2, E3, E4, E5, E6, E7, E8, E9, E10) -> Unit = { e1, e2, e3, e4, e5, e6, e7, e8, e9, e10 ->
  actionSink.send(action("eH: $name") { update(e1, e2, e3, e4, e5, e6, e7, e8, e9, e10) })
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
