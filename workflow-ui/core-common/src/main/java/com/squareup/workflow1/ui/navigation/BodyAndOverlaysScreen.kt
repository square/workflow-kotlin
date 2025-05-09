package com.squareup.workflow1.ui.navigation

import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Compatible.Companion.keyFor
import com.squareup.workflow1.ui.Composite
import com.squareup.workflow1.ui.Screen

/**
 * A screen that may stack a number of [Overlay]s over a body.
 * If any members of [overlays] are [ModalOverlay], the body and
 * lower-indexed members of that list are expected to ignore input
 * events -- touch, keyboard, etc.
 *
 * UI kits are expected to provide handling for this class by default.
 *
 * Any [overlays] shown are expected to have their bounds restricted
 * to the area above the [body]. For example, consider a layout where
 * we want the option to show a tutorial bar below the main UI:
 *
 *    +-------------------------+
 *    |  MyMainScreen           |
 *    |                         |
 *    |                         |
 *    +-------------------------+
 *    | MyTutorialScreen        |
 *    +-------------------------+
 *
 * And we want to ensure that any modal windows do not obscure the tutorial, if
 * it's showing:
 *
 *    +----+=============+------+
 *    |  My|             |      |
 *    |    | MyEditModal |      |
 *    |    |             |      |
 *    +----+=============+------+
 *    | MyTutorialScreen        |
 *    +-------------------------+
 *
 * We could model that this way:
 *
 *     MyBodyAndBottomBarScreen(
 *       body = BodyAndOverlaysScreen(
 *         body = mainScreen,
 *         overlays = listOfNotNull(editModalOrNull)
 *       ),
 *       bar = tutorialScreenOrNull,
 *     )
 *
 * It is also possible to nest [BodyAndOverlaysScreen] instances. For example,
 * to show a higher priority modal that covers both `MyMainScreen` and `MyTutorialScreen`,
 * we could render this:
 *
 *     BodyAndOverlaysScreen(
 *       overlays = listOfNotNull(fullScreenModalOrNull),
 *       body = MyBodyAndBottomBarScreen(
 *         body = BodyAndOverlaysScreen(
 *           body = mainScreen,
 *           overlays = listOfNotNull(editModalOrNull)
 *         ),
 *         bar = tutorialScreenOrNull,
 *       )
 *     )
 *
 * Whatever structure you settle on for your root rendering, it is important
 * to render the same structure every time. If your app will ever want to show
 * an [Overlay], it should always render [BodyAndOverlaysScreen], even when
 * there is no [Overlay] to show. Otherwise your entire view tree will be rebuilt,
 * since the view built for a `MyBodyAndBottomBarScreen` cannot be updated to show
 * a [BodyAndOverlaysScreen] rendering.
 *
 * @param name included in the [compatibilityKey] of this screen, for ease
 * of nesting -- in classic Android views, view state persistence support requires each
 * BodyAndOverlaysScreen in a hierarchy to have a unique key.
 */
public class BodyAndOverlaysScreen<B : Screen, O : Overlay>(
  public val body: B,
  public val overlays: List<O> = emptyList(),
  public val name: String = ""
) : Screen, Compatible, Composite<Any> {
  override val compatibilityKey: String = keyFor(this, name)

  override fun asSequence(): Sequence<Any> = sequenceOf(body) + overlays.asSequence()

  public fun <S : Screen> mapBody(transform: (B) -> S): BodyAndOverlaysScreen<S, O> {
    return BodyAndOverlaysScreen(transform(body), overlays, name)
  }

  public fun <N : Overlay> mapOverlays(transform: (O) -> N): BodyAndOverlaysScreen<B, N> {
    return BodyAndOverlaysScreen(body, overlays.map(transform), name)
  }
}
