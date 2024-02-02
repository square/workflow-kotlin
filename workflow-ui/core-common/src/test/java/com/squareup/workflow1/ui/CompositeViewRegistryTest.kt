package com.squareup.workflow1.ui

import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.ViewRegistry.Entry
import com.squareup.workflow1.ui.ViewRegistry.Key
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.test.assertFailsWith

@OptIn(WorkflowUiExperimentalApi::class)
internal class CompositeViewRegistryTest {

  @Test fun `constructor throws on duplicates`() {
    val fooBarRegistry = TestRegistry(setOf(FooRendering::class, BarRendering::class))
    val barBazRegistry = TestRegistry(setOf(BarRendering::class, BazRendering::class))

    val error = assertFailsWith<IllegalArgumentException> {
      fooBarRegistry + barBazRegistry
    }
    assertThat(error).hasMessageThat()
      .startsWith("Must not have duplicate entries: ")
    assertThat(error).hasMessageThat()
      .contains(BarRendering::class.java.name)
  }

  @Test fun `getFactoryFor delegates to composite registries`() {
    val fooFactory = TestEntry(FooRendering::class)
    val barFactory = TestEntry(BarRendering::class)
    val bazFactory = TestEntry(BazRendering::class)
    val fooBarRegistry = TestRegistry(
      mapOf(
        fooFactory.key to fooFactory,
        barFactory.key to barFactory
      )
    )
    val bazRegistry = TestRegistry(factories = mapOf(bazFactory.key to bazFactory))
    val registry = fooBarRegistry + bazRegistry

    assertThat(registry.getEntryFor(Key(FooRendering::class, TestEntry::class)))
      .isSameInstanceAs(fooFactory)
    assertThat(registry.getEntryFor(Key(BarRendering::class, TestEntry::class)))
      .isSameInstanceAs(barFactory)
    assertThat(registry.getEntryFor(Key(BazRendering::class, TestEntry::class)))
      .isSameInstanceAs(bazFactory)
  }

  @Test fun `getFactoryFor returns null on missing registry`() {
    val fooRegistry = TestRegistry(setOf(FooRendering::class))
    val registry = CompositeViewRegistry(ViewRegistry(), fooRegistry)

    assertThat(registry.getEntryFor(Key(BarRendering::class, TestEntry::class))).isNull()
  }

  @Test fun `keys includes all composite registries' keys`() {
    val fooBarRegistry = TestRegistry(setOf(FooRendering::class, BarRendering::class))
    val bazRegistry = TestRegistry(setOf(BazRendering::class))
    val registry = CompositeViewRegistry(fooBarRegistry, bazRegistry)

    assertThat(registry.keys).containsExactly(
      Key(FooRendering::class, TestEntry::class),
      Key(BarRendering::class, TestEntry::class),
      Key(BazRendering::class, TestEntry::class)
    )
  }

  private class TestEntry<T : Any>(type: KClass<in T>) : Entry<T> {
    override val key = Key(type, TestEntry::class)
  }

  private object FooRendering
  private object BarRendering
  private object BazRendering

  private class TestRegistry(private val factories: Map<Key<*, *>, Entry<*>>) : ViewRegistry {
    constructor(keys: Set<KClass<*>>) : this(
      keys.associate {
        val entry = TestEntry(it)
        entry.key to entry
      }
    )

    override val keys: Set<Key<*, *>> get() = factories.keys

    @Suppress("UNCHECKED_CAST")
    override fun <RenderingT : Any, FactoryT : Any> getEntryFor(
      key: Key<RenderingT, FactoryT>
    ): Entry<RenderingT> = factories.getValue(key) as Entry<RenderingT>
  }
}
