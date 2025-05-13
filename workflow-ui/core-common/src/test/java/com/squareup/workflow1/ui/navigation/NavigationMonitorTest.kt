package com.squareup.workflow1.ui.navigation

import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Compatible.Companion.keyFor
import com.squareup.workflow1.ui.Container
import com.squareup.workflow1.ui.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class NavigationMonitorTest {
  private data class NotScreen(
    val name: String,
    val baggage: String = ""
  ) : Compatible {
    override val compatibilityKey: String = keyFor(this, name)
  }

  private data class TestScreen(
    val name: String,
    val baggage: String = ""
  ) : Screen, Compatible {
    override val compatibilityKey: String = keyFor(this, name)
  }

  private data class TestContainer<T : Any>(
    val content: List<T>
  ) : Container<Any, T> {
    override fun asSequence(): Sequence<T> = content.asSequence()

    override fun <D : Any> map(transform: (T) -> D): Container<Any, D> = error("not relevant")
  }

  private class TestOverlay<T : Screen>(
    override val content: T
  ) : ScreenOverlay<T> {
    override fun <ContentU : Screen> map(transform: (T) -> ContentU) = error("not relevant")
  }

  private var lastTop: Any? = null
  private var updates = 0

  private fun onUpdate(top: Any) {
    lastTop = top
    updates++
  }

  private val monitor = NavigationMonitor(onNavigate = ::onUpdate)

  @Test
  fun `reports first by default`() {
    val screen = TestScreen("first")
    assertNull(lastTop)
    monitor.update(screen)
    assertSame(screen, lastTop)
  }

  @Test
  fun `can skip first`() {
    val monitor = NavigationMonitor(skipFirstScreen = true, ::onUpdate)

    assertNull(lastTop)
    monitor.update(TestScreen("first"))
    assertNull(lastTop)

    monitor.update(TestScreen("second"))
    assertEquals(TestScreen("second"), lastTop)
  }

  @Test
  fun `reports only on compatibility change`() {
    val type1Instance1 = TestScreen("first")
    assertEquals(0, updates)

    monitor.update(type1Instance1)
    assertEquals(1, updates)

    val type1Instance2 = type1Instance1.copy(baggage = "baggage")
    assertNotEquals(type1Instance1, type1Instance2)
    monitor.update(type1Instance2)
    assertEquals(1, updates)
    assertSame(type1Instance1, lastTop)

    val type2 = TestScreen("second")
    monitor.update(type2)
    assertEquals(2, updates)
    assertSame(type2, lastTop)
  }

  @Test
  fun `handles non-Screens`() {
    val first = NotScreen("first")

    monitor.update(first)
    assertSame(first, lastTop)

    monitor.update(first.copy(baggage = "fnord"))
    assertSame(first, lastTop)
    assertEquals(1, updates)

    monitor.update(NotScreen("second", baggage = "fnord"))
    assertEquals(NotScreen("second", baggage = "fnord"), lastTop)
    assertEquals(2, updates)
  }

  @Test
  fun unwraps() {
    monitor.update(container(TestScreen("0"), TestScreen("1"), TestScreen("2")))
    assertEquals(TestScreen("2"), lastTop)
    assertEquals(1, updates)

    monitor.update(container(TestScreen("0"), TestScreen("1"), TestScreen("2")))
    assertEquals(TestScreen("2"), lastTop)
    assertEquals(1, updates)

    monitor.update(container(TestScreen("0"), TestScreen("Hidden Update"), TestScreen("2")))
    assertEquals(TestScreen("2"), lastTop)
    assertEquals(1, updates)

    monitor.update(container(TestScreen("0"), TestScreen("Hidden Update"), TestScreen("3")))
    assertEquals(TestScreen("3"), lastTop)
    assertEquals(2, updates)

    monitor.update(container(TestScreen("3", "baggage")))
    assertEquals(TestScreen("3"), lastTop)
    assertEquals(2, updates)
  }

  @Test
  fun `stock navigation types play nice`() {
    val body = TestScreen("Body")

    monitor.update(bodyAndOverlays(body))
    assertSame(body, lastTop)

    monitor.update(bodyAndOverlays(body.copy(baggage = "updated")))
    assertSame(body, lastTop)

    val firstWindowBody = TestScreen("first window")
    monitor.update(bodyAndOverlays(body, TestOverlay(firstWindowBody)))
    assertSame(firstWindowBody, lastTop)

    val wizardOne = TestScreen("wizard one")
    monitor.update(
      bodyAndOverlays(
        body,
        TestOverlay(firstWindowBody),
        TestOverlay(BackStackScreen(wizardOne))
      )
    )
    assertSame(wizardOne, lastTop)

    monitor.update(
      bodyAndOverlays(
        body,
        TestOverlay(firstWindowBody),
        TestOverlay(BackStackScreen(wizardOne.copy(baggage = "updated")))
      )
    )
    assertSame(wizardOne, lastTop)

    val wizardTwo = TestScreen("wizard two")
    monitor.update(
      bodyAndOverlays(
        body,
        TestOverlay(firstWindowBody),
        TestOverlay(
          BackStackScreen(
            wizardOne.copy(baggage = "updated"),
            wizardTwo
          )
        )
      )
    )
    assertSame(wizardTwo, lastTop)
  }

  private fun <T : Any> container(vararg elements: T): TestContainer<T> =
    TestContainer(elements.toList())

  private fun bodyAndOverlays(
    body: Screen,
    vararg overlays: Overlay
  ): BodyAndOverlaysScreen<*, *> {
    return BodyAndOverlaysScreen(body, overlays.asList())
  }
}
