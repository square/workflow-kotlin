package com.squareup.workflow1.ui

import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.ViewRegistry.Entry
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.test.assertFailsWith

@OptIn(WorkflowUiExperimentalApi::class)
internal class CompositeViewRegistryTest {

  @Test fun `constructor throws on duplicates`() {
    val fooBarRegistry = TestRegistry(setOf(FooRendering::class, BarRendering::class))
    val barBazRegistry = TestRegistry(setOf(BarRendering::class, BazRendering::class))

    val error = assertFailsWith<IllegalStateException> {
      CompositeViewRegistry(fooBarRegistry, barBazRegistry)
    }
    assertThat(error).hasMessageThat()
        .startsWith("Must not have duplicate entries: ")
    assertThat(error).hasMessageThat()
        .contains(BarRendering::class.java.name)
  }

  @Test fun `getFactoryFor delegates to composite registries`() {
    val fooFactory = TestViewFactory(FooRendering::class)
    val barFactory = TestViewFactory(BarRendering::class)
    val bazFactory = TestViewFactory(BazRendering::class)
    val fooBarRegistry = TestRegistry(
        mapOf(
            FooRendering::class to fooFactory,
            BarRendering::class to barFactory
        )
    )
    val bazRegistry = TestRegistry(factories = mapOf(BazRendering::class to bazFactory))
    val registry = CompositeViewRegistry(fooBarRegistry, bazRegistry)

    assertThat(registry.getEntryFor(FooRendering::class))
        .isSameInstanceAs(fooFactory)
    assertThat(registry.getEntryFor(BarRendering::class))
        .isSameInstanceAs(barFactory)
    assertThat(registry.getEntryFor(BazRendering::class))
        .isSameInstanceAs(bazFactory)
  }

  @Test fun `getFactoryFor returns null on missing registry`() {
    val fooRegistry = TestRegistry(setOf(FooRendering::class))
    val registry = CompositeViewRegistry(fooRegistry)

    assertThat(registry.getEntryFor(BarRendering::class)).isNull()
  }

  @Test fun `keys includes all composite registries' keys`() {
    val fooBarRegistry = TestRegistry(setOf(FooRendering::class, BarRendering::class))
    val bazRegistry = TestRegistry(setOf(BazRendering::class))
    val registry = CompositeViewRegistry(fooBarRegistry, bazRegistry)

    assertThat(registry.keys).containsExactly(
        FooRendering::class,
        BarRendering::class,
        BazRendering::class
    )
  }

  private object FooRendering
  private object BarRendering
  private object BazRendering

  private class TestRegistry(private val factories: Map<KClass<*>, ViewFactory<*>>) : ViewRegistry {
    constructor(keys: Set<KClass<*>>) : this(keys.associateWith { TestViewFactory(it) })

    override val keys: Set<KClass<*>> get() = factories.keys

    @Suppress("UNCHECKED_CAST")
    override fun <RenderingT : Any> getEntryFor(
      renderingType: KClass<out RenderingT>
    ): Entry<RenderingT> = factories.getValue(renderingType) as Entry<RenderingT>
  }
}
