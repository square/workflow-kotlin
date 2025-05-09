package com.squareup.workflow1.ui

import com.squareup.workflow1.ui.navigation.BackStackScreen
import com.squareup.workflow1.ui.navigation.BodyAndOverlaysScreen
import com.squareup.workflow1.ui.navigation.Overlay
import com.squareup.workflow1.ui.navigation.ScreenOverlay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ContainerTest {
  private data class TestScreen(val id: Int = 0) : Screen

  private data class TestScreenContainer<T : Screen>(
    val children: List<T>
  ) : Screen, Container<Screen, T> {
    override fun asSequence(): Sequence<T> = children.asSequence()

    override fun <D : Screen> map(transform: (T) -> D) =
      TestScreenContainer(children.map(transform))
  }

  private data class TestOverlay(val id: Int = 0) : Overlay

  private data class TestScreenOverlay<S : Screen>(
    override val content: S
  ) : ScreenOverlay<S> {
    override fun <ContentU : Screen> map(
      transform: (S) -> ContentU
    ) = TestScreenOverlay(transform(content))
  }

  @Test
  fun `unwrap returns this`() {
    val screen = TestScreen()
    assertSame(screen, screen.unwrap())
  }

  @Test
  fun `unwrap returns last`() {
    assertEquals(
      TestScreen(2),
      TestScreenContainer(listOf(TestScreen(0), TestScreen(1), TestScreen(2))).unwrap()
    )
  }

  @Test
  fun `unwrap returns deepest content from nested wrappers`() {
    val container = TestScreenContainer(
      listOf(
        TestScreen(0),
        TestScreen(1),
        TestScreenContainer(
          listOf(
            TestScreen(2),
            TestScreen(3),
            TestScreenContainer(listOf(TestScreen(4), TestScreen(5)))
          )
        ),
      )
    )
    assertEquals(TestScreen(5), container.unwrap())
  }

  @Test
  fun `unwrap prefers outer last`() {
    val container = TestScreenContainer(
      listOf(
        TestScreen(0),
        TestScreenContainer(listOf(TestScreen(1), TestScreen(2), TestScreen(3))),
        TestScreen(4),
      )
    )
    assertEquals(TestScreen(4), container.unwrap())
  }

  @Test fun `can unwrap through BodyAndOverlaysScreen to Body`() {
    val container = BodyAndOverlaysScreen(body = TestScreen(), overlays = emptyList())
    assertEquals(TestScreen(), container.unwrap())
  }

  @Test
  fun `can unwrap through BodyAndOverlaysScreen to an Overlay`() {
    val container = BodyAndOverlaysScreen(
      body = TestScreen(),
      overlays = listOf(TestOverlay(0), TestOverlay(1))
    )

    assertEquals(TestOverlay(1), container.unwrap())
  }

  @Test
  fun `can unwrap through BodyAndOverlaysScreen through ScreenOverlay and then some`() {
    val container = BodyAndOverlaysScreen(
      body = TestScreen(),
      overlays = listOf(
        TestOverlay(0),
        TestOverlay(1),
        TestScreenOverlay(BackStackScreen(TestScreen(0), TestScreen(1)))
      )
    )

    assertEquals(TestScreen(1), container.unwrap())
  }
}
