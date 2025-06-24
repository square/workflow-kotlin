package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.ExplicitGroupsComposable
import androidx.compose.runtime.currentComposer
import com.squareup.workflow1.internal.compose.Trapdoor.Companion.open
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind.EXACTLY_ONCE
import kotlin.contracts.contract
import kotlin.jvm.JvmInline

/**
 * Helper to locally break out of a composable and then re-enter composition from non-composable
 * code. Call [open] to run some non-composable code inline that can re-enter composition by calling
 * [inMovableGroup].
 *
 * **Trapdoors open into pits of danger! This is a highly dangerous API that can easily put a
 * composition into an invalid state. Use with extreme care!**
 *
 * ## Usage
 *
 * This code…
 * ```
 * @Composable fun OuterComposable() {
 *   Trapdoor.open { trapdoor ->
 *     nonComposable(trapdoor)
 *   }
 * }
 *
 * private fun nonComposable(trapdoor: Trapdoor) {
 *   trapdoor.composeReturning {
 *     InnerComposable()
 *   }
 * }
 *
 * @Composable private fun InnerComposable() {
 *   BasicText("Inside!")
 * }
 * ```
 * …is, as far as Compose is concerned, equivalent to this:
 * ```
 * @Composable fun OuterComposable() {
 *   InnerComposable()
 * }
 * ```
 * Both generate the exact same grouping calls and slot table.
 */
@Suppress("UNCHECKED_CAST")
@JvmInline
internal value class Trapdoor(private val composer: Composer) {

  /**
   * Calls [content] as if it were called directly in composition from wherever this [Trapdoor] was
   * [open]ed. This function places a "movable group" around [content] with the given [key] and
   * [dataKey]. This means that if there are multiple calls to this function from inside the same
   * parent group, and they all have the same [key], then the order of the calls can change and
   * calls can be added and removed in subsequent recompositions, and the data inside each child
   * group will be associated with the [dataKey].
   */
  @OptIn(ExperimentalContracts::class)
  fun <R> inMovableGroup(
    key: Int,
    dataKey: Any?,
    content: @Composable () -> R
  ): R {
    @Suppress("WRONG_INVOCATION_KIND")
    contract { callsInPlace(content, kind = EXACTLY_ONCE) }
    val invokableContent = content as (Composer, Int) -> R
    // TODO: Just discovered it's necessary to put a movable group here, probably because otherwise
    //  `content` always has its own internal group and so putting a `key` call in there is too
    //  late. Need to think more about implications, how to clean this up, does the hash key need
    //  to be the same for everything in a renderContext (I think so), etc.
    composer.startMovableGroup(key, dataKey)
    return invokableContent.invoke(composer, 0)
      .also { composer.endMovableGroup() }
  }

  /**
   * Calls [content] as if it were called directly in composition from wherever this [Trapdoor] was
   * [open]ed. This function places a "movable group" around [content] with the given [key] and
   * [dataKey]. This means that if there are multiple calls to this function from inside the same
   * parent group, and they all have the same [key], then the order of the calls can change and
   * calls can be added and removed in subsequent recompositions, and the data inside each child
   * group will be associated with the [dataKey].
   */
  // TODO It really is not ideal that this has to allocate a new lambda on every render pass. We
  //  can avoid this by making inMovableGroup take a few non-key params that are just forwarded to
  //  the content param, and then we can just manually cache the lambda in a field in
  //  ComposeRenderContext.
  @OptIn(ExperimentalContracts::class)
  fun <R> inMovableGroup(
    key: Int,
    dataKey1: Any?,
    dataKey2: Any?,
    content: @Composable () -> R
  ): R {
    contract { callsInPlace(content, kind = EXACTLY_ONCE) }
    return inMovableGroup(key, composer.joinKey(dataKey1, dataKey2), content)
  }

  companion object {
    /**
     * Runs [block] inline with a [Trapdoor] object that can be used to re-enter composition by
     * calling [inMovableGroup].
     */
    // No reason for the overhead of additional groups here, if Trapdoor is being used you're
    // already in Hard Mode.
    @ExplicitGroupsComposable
    @Composable
    inline fun <R> open(block: (Trapdoor) -> R): R = block(Trapdoor(currentComposer))

    @Composable
    fun open(): Trapdoor = Trapdoor(currentComposer)

    /**
     * Uses Compose's slot table to detect changes to [value] between recompositions and calls
     * [ifChanged] when a change is detected.
     */
    @Composable
    inline fun <T> runIfValueChanged(
      value: T,
      ifChanged: (oldValue: T) -> Unit
    ) {
      val composer = currentComposer
      val oldValue = composer.rememberedValue()
      val wasEmpty = oldValue === Composer.Empty
      val didChange = oldValue != value

      if (wasEmpty || didChange) {
        composer.updateRememberedValue(value)
      }

      if (!wasEmpty && didChange) {
        ifChanged(oldValue as T)
      }
    }
  }
}
