package com.squareup.sample.hellocomposerendering

import androidx.compose.Composable
import com.squareup.sample.hellocomposerendering.Concrete.Foo
import com.squareup.workflow.Sink

interface Interface<T, out U> {
  fun interfaceMethod(): Pair<T, U>
}

/**
 * TODO write documentation
 */
abstract class AbstractClass<T, U> : Interface<T, U> {
  @Composable abstract fun render(
    value: T,
    sink: Sink<U>
  )

  override fun interfaceMethod() = TODO()
}

object Concrete : AbstractClass<String, Foo>() {

  object Foo

  @Composable override fun render(
    value: String,
    sink: Sink<Foo>
  ) {
  }
}
