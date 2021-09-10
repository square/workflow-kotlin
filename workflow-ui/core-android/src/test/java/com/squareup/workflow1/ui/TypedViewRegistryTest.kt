package com.squareup.workflow1.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(WorkflowUiExperimentalApi::class)
internal class TypedViewRegistryTest {

  @Test fun `keys from bindings`() {
    val factory1 = TestViewFactory(FooRendering::class)
    val factory2 = TestViewFactory(BarRendering::class)
    val registry = TypedViewRegistry(factory1, factory2)

    assertThat(registry.keys).containsExactly(factory1.type, factory2.type)
  }

  @Test fun `constructor throws on duplicates`() {
    val factory1 = TestViewFactory(FooRendering::class)
    val factory2 = TestViewFactory(FooRendering::class)

    val error = assertFailsWith<IllegalStateException> {
      TypedViewRegistry(factory1, factory2)
    }
    assertThat(error).hasMessageThat()
      .endsWith("must not have duplicate entries.")
    assertThat(error).hasMessageThat()
      .contains(FooRendering::class.java.name)
  }

  @Test fun `getFactoryFor works`() {
    val fooFactory = TestViewFactory(FooRendering::class)
    val registry = TypedViewRegistry(fooFactory)

    val factory = registry.getFactoryFor(FooRendering::class)
    assertThat(factory).isSameInstanceAs(fooFactory)
  }

  @Test fun `getFactoryFor returns null on missing binding`() {
    val fooFactory = TestViewFactory(FooRendering::class)
    val registry = TypedViewRegistry(fooFactory)

    assertThat(registry.getFactoryFor(BarRendering::class)).isNull()
  }

  @Test fun `buildView honors AndroidViewRendering`() {
    val registry = TypedViewRegistry()
    registry.buildView(ViewRendering)
    assertThat(ViewRendering.viewFactory.called).isTrue()
  }

  @Test fun `buildView prefers registry entries to AndroidViewRendering`() {
    val registry = TypedViewRegistry(overrideViewRenderingFactory)
    registry.buildView(ViewRendering)
    assertThat(ViewRendering.viewFactory.called).isFalse()
    assertThat(overrideViewRenderingFactory.called).isTrue()
  }

  @Test fun `ViewRegistry with no arguments infers type`() {
    val registry = ViewRegistry()
    assertTrue(registry.keys.isEmpty())
  }

  private object FooRendering
  private object BarRendering

  private object ViewRendering : AndroidViewRendering<ViewRendering> {
    override val viewFactory: TestViewFactory<ViewRendering> = TestViewFactory(ViewRendering::class)
  }
  private val overrideViewRenderingFactory = TestViewFactory(ViewRendering::class)
}
