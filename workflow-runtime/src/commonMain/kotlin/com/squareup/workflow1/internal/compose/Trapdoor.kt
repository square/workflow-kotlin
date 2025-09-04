package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.currentComposer
import com.squareup.workflow1.internal.compose.Trapdoor.Companion.open
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind.EXACTLY_ONCE
import kotlin.contracts.contract
import kotlin.jvm.JvmInline

/**
 * Helper to locally break out of a composable and then re-enter composition from non-composable
 * code. Call [open] to run some non-composable code inline that can re-enter composition by calling
 * [composeReturning].
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
   * [open]ed. This function does not place any group around [content], so take care to ensure that
   * [content] does its own grouping if necessary.
   *
   * This function **MUST** only be called from within the function passed to [open]. Calling it
   * after [open] returns will result in undefined (but almost certainly bad) behavior.
   */
  @OptIn(ExperimentalContracts::class)
  fun <R> composeReturning(content: @Composable () -> R): R {
    contract { callsInPlace(content, kind = EXACTLY_ONCE) }
    val invokableContent = content as (Composer, Int) -> R
    return invokableContent.invoke(composer, 0)
  }

  companion object {
    /**
     * Runs [block] inline with a [Trapdoor] object that can be used to re-enter composition by
     * calling [composeReturning].
     */
    @Composable
    inline fun <R> open(block: (Trapdoor) -> R): R = block(Trapdoor(currentComposer))
  }
}
