package com.squareup.workflow1.ui

import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.ViewRegistry.Entry
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(WorkflowUiExperimentalApi::class)
internal class TypedViewRegistryTest {

  @Test fun `keys from bindings`() {
    val factory1 = TestEntry(FooRendering::class)
    val factory2 = TestEntry(BarRendering::class)
    val registry = ViewRegistry(factory1, factory2)

    assertThat(registry.keys).containsExactly(factory1.type, factory2.type)
  }

  @Test fun `constructor throws on duplicates`() {
    val factory1 = TestEntry(FooRendering::class)
    val factory2 = TestEntry(FooRendering::class)

    val error = assertFailsWith<IllegalStateException> {
      ViewRegistry(factory1, factory2)
    }
    assertThat(error).hasMessageThat()
      .endsWith("must not have duplicate entries.")
    assertThat(error).hasMessageThat()
      .contains(FooRendering::class.java.name)
  }

  @Test fun `getFactoryFor works`() {
    val fooFactory = TestEntry(FooRendering::class)
    val registry = ViewRegistry(fooFactory)

    val factory = registry.getEntryFor(FooRendering::class)
    assertThat(factory).isSameInstanceAs(fooFactory)
  }

  @Test fun `getFactoryFor returns null on missing binding`() {
    val fooFactory = TestEntry(FooRendering::class)
    val registry = ViewRegistry(fooFactory)

    assertThat(registry.getEntryFor(BarRendering::class)).isNull()
  }

  @Test fun `ViewRegistry with no arguments infers type`() {
    val registry = ViewRegistry()
    assertTrue(registry.keys.isEmpty())
  }

  private class TestEntry<T : Any>(
    override val type: KClass<in T>
  ) : Entry<T>

  private object FooRendering
  private object BarRendering
}
