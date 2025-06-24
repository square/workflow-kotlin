@file:OptIn(InternalComposeApi::class)

package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.ExplicitGroupsComposable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.remember

// From https://github.com/squareup/market/pull/11218/

/**
 * Invokes [producer] as a restartable, skippable composable by caching its return value.
 *
 * The first time this composable is called it runs [producer] and remembers its return value in the
 * same way as [remember].
 *
 * When the caller is recomposed (calling this composable again), if the same [producer] instance is
 * passed and [producer] hasn't been invalidated itself (i.e. due to a state change), then [producer]
 * is skipped (i.e. state is kept in the composition but it is not recomposed) and the cached value is
 * returned. If a different [producer] instance is passed, it's composed and its return value is cached
 * before being returned.
 *
 * [producer] is _not_ treated as an implicit key: only the
 * initial [producer] instance passed to this function will ever be called. The compose compiler will
 * normally auto-remember lambdas passed to composables, but _only if they return `Unit`_. Since
 * [producer] has a non-`Unit` return type, it will never be auto-remembered. So this function always
 * remembers [producer] itself. If we treated [producer] as a key, it would always be recomposed unless
 * the caller explicitly remembered it. To update the producer logic, store it in a [MutableState]:
 *
 * **Maintainer note: If we do this as a compiler plugin, we should also implement auto-memoizing
 * lambdas for this case.**
 *
 * @see rememberSkippableAndRestartableComposable
 */
// We don't need the compiler to generate groups because the impl non-Composable function explicitly
// creates its own groups anyway.
@ExplicitGroupsComposable
@Composable
internal fun <R> rememberSkippableComposable(
  key1: Any?,
  key2: Any?,
  producer: @Composable () -> R
): R = rememberSkippableComposableImpl(
  arg1 = key1,
  arg2 = key2,
  arg3 = Unit,
  producer = producer,
  composer = currentComposer,
  changed = 0,
)

// // We don't need the compiler to generate groups because the impl non-Composable function explicitly
// // creates its own groups anyway.
// @ExplicitGroupsComposable
// @Composable
// inline fun <R> rememberSkippableComposable(
//   key1: Any?,
//   key2: Any?,
//   producer: @Composable () -> R
// ): R {
//   val composer = currentComposer
//   val runProducer = rSKC_before(
//     arg1 = key1,
//     arg2 = key2,
//     arg3 = Unit,
//     composer = composer,
//   )
//
//   val newValue = if (runProducer) {
//     producer()
//   } else {
//     Composer.Empty
//   }
//
//   return rSKC_after(newValue, composer)
// }

// // We don't need the compiler to generate groups because the impl non-Composable function explicitly
// // creates its own groups anyway.
// @ExplicitGroupsComposable
// @Composable
// inline fun <R> rememberSkippableComposable(
//   key1: Any?,
//   key2: Any?,
//   key3: Any?,
//   producer: @Composable () -> R
// ): R {
//   val composer = currentComposer
//   val changed = 0
//
//   // Outer group has two "children": The restartable group that might be skipped if no keys have
//   // changed, and the remembered return value.
//   // Key chosen "randomly" by mashing on my keyboard.
//   composer.startReplaceGroup(23975235)
//
//   // region Recompose producer
//   // Inner group is necessary to be able to skip calling producer. We need a nested group because we
//   // only want to skip calling producer, we still need to do other slot table stuff later to
//   // read the cache even if producer is skipped.
//   // Key chosen "randomly" by mashing on my keyboard.
//   composer.startReplaceGroup(-895983)
//
//   // We don't consider producer in this change detection code since we don't want to treat it as a
//   // key.
//   var dirty = changed
//   if ((changed and 0b110) == 0) {
//     dirty = changed or (if (composer.changed(key1)) 0b100 else 0b010)
//   }
//   if ((changed and 0b110_000) == 0) {
//     dirty = dirty or (if (composer.changed(key2)) 0b100_000 else 0b010_000)
//   }
//   if ((changed and 0b110_000_000) == 0) {
//     dirty = dirty or (if (composer.changed(key3)) 0b100_000_000 else 0b010_000_000)
//   }
//
//   val newValue = if ((dirty and 0b010_010_011) == 0b010_010_010 && composer.skipping) {
//     composer.skipToGroupEnd()
//     Composer.Empty
//   } else {
//     producer()
//   }
//
//   composer.endReplaceGroup()
//   // endregion
//
//   // region Update cache
//   // Cache the return value in case we skipped above. Composer APIs require always reading the value
//   // first, and then calling updateRememberedValue the first time or optionally on subsequent
//   // recompositions.
//   val oldValue = composer.rememberedValue()
//
//   @Suppress("SuspiciousEqualsCombination")
//   val returnValue = if (
//     oldValue === Composer.Empty ||
//     (newValue !== Composer.Empty && newValue != oldValue)
//   ) {
//     // Update the cache.
//     if (newValue === Composer.Empty) error("No new value was calculated.")
//     composer.updateRememberedValue(newValue)
//     newValue
//   } else {
//     // Producer was skipped (or returned the same value), return from the cache.
//     oldValue
//   }
//   // endregion
//
//   composer.endReplaceGroup()
//   @Suppress("UNCHECKED_CAST")
//   return returnValue as R
// }

/**
 * Invokes [producer] as a restartable, skippable composable by caching its return value.
 *
 * The first time this composable is called it runs [producer] and remembers its return value in the
 * same way as [remember].
 *
 * When the caller is recomposed (calling this composable again), if the same [producer] instance is
 * passed and [producer] hasn't been invalidated itself (i.e. due to a state change), then [producer]
 * is skipped (i.e. state is kept in the composition but it is not recomposed) and the cached value is
 * returned. If a different [producer] instance is passed, it's composed and its return value is cached
 * before being returned.
 *
 * When [producer] is invalidated independently (i.e. due to a state it read being changed), without
 * the caller being invalidated, then just [producer] is recomposed at first and its return value is
 * compared with the cached value: If it's different, and only if it's different, then the new value
 * is cached and the caller is invalidated, which will cause the caller to be recomposed during the
 * same composition phase of the same frame. When this happens, the same caching behavior applies: if
 * the same instance of [producer] is passed in then it will be skipped and the cached value will be
 * returned.
 *
 * [producer] is _not_ treated as an implicit key: only the
 * initial [producer] instance passed to this function will ever be called. The compose compiler will
 * normally auto-remember lambdas passed to composables, but _only if they return `Unit`_. Since
 * [producer] has a non-`Unit` return type, it will never be auto-remembered. So this function always
 * remembers [producer] itself. If we treated [producer] as a key, it would always be recomposed unless
 * the caller explicitly remembered it. To update the producer logic, store it in a [MutableState]:
 *
 * **Maintainer note: If we do this as a compiler plugin, we should also implement auto-memoizing
 * lambdas for this case.**
 *
 * @see rememberSkippableComposable
 */
// We don't need the compiler to generate groups because the impl non-Composable function has to
// explicitly create its own groups anyway.
@ExplicitGroupsComposable
@Composable
internal fun <R> rememberSkippableAndRestartableComposable(
  key1: Any?,
  key2: Any?,
  producer: @Composable () -> R
): R = rememberSkippableAndRestartableComposableImpl(
  arg1 = key1,
  arg2 = key2,
  arg3 = Unit,
  producer = producer,
  callerRecomposeScope = currentRecomposeScope,
  composer = currentComposer,
  changed = 0,
  invalidateCallerOnNewValue = false
)

@PublishedApi
internal fun rSKC_before(
  arg1: Any?,
  arg2: Any?,
  arg3: Any?,
  composer: Composer,
  changed: Int = 0,
): Boolean {
  // Outer group is restartable: This should wrap the entire body of this function (except the actual
  // return statement) and is what defines the recompose scope for producer.
  // Key chosen "randomly" by mashing on my keyboard.
  composer.startReplaceGroup(23975235)

  // Inner group is necessary to be able to skip calling producer. We need a nested group because we
  // only want to skip calling producer, we still need to do other slot table stuff later to
  // read the cache even if producer is skipped.
  // Key chosen "randomly" by mashing on my keyboard.
  composer.startReplaceGroup(-895983)

  // We don't consider producer in this change detection code since we don't want to treat it as a key.
  var dirty = changed
  if ((changed and 0b110) == 0) {
    dirty = changed or (if (composer.changed(arg1)) 0b100 else 0b010)
  }
  if ((changed and 0b110_000) == 0) {
    dirty = dirty or (if (composer.changed(arg2)) 0b100_000 else 0b010_000)
  }
  if ((changed and 0b110_000_000) == 0) {
    dirty = dirty or (if (composer.changed(arg3)) 0b100_000_000 else 0b010_000_000)
  }
  if ((dirty and 0b010_010_011) == 0b010_010_010 && composer.skipping) {
    composer.skipToGroupEnd()
    return false
  } else {
    return true
  }
}

@PublishedApi
internal fun <R> rSKC_after(
  newValue: Any?,
  composer: Composer,
): R {
  composer.endReplaceGroup()

  // Cache the return value in case we skipped above. Composer APIs require always reading the value
  // first, and then calling updateRememberedValue the first time or optionally on subsequent
  // recompositions.
  val oldValue = composer.rememberedValue()

  @Suppress("SuspiciousEqualsCombination")
  val returnValue = if (
    oldValue === Composer.Empty ||
    (newValue !== Composer.Empty && newValue != oldValue)
  ) {
    // Update the cache.
    if (newValue === Composer.Empty) error("No new value was calculated.")
    composer.updateRememberedValue(newValue)
    newValue
  } else {
    // Producer was skipped (or returned the same value), return from the cache.
    oldValue
  }

  composer.endReplaceGroup()
  @Suppress("UNCHECKED_CAST")
  return returnValue as R
}

@Suppress("SuspiciousEqualsCombination", "UNCHECKED_CAST")
@PublishedApi
internal fun <R> rememberSkippableComposableImpl(
  arg1: Any?,
  arg2: Any?,
  arg3: Any?,
  producer: @Composable () -> R,
  composer: Composer,
  changed: Int,
): R {
  // Outer group is restartable: This should wrap the entire body of this function (except the actual
  // return statement) and is what defines the recompose scope for producer.
  // Key chosen "randomly" by mashing on my keyboard.
  composer.startReplaceGroup(23975235)

  // region Recompose producer
  // Inner group is necessary to be able to skip calling producer. We need a nested group because we
  // only want to skip calling producer, we still need to do other slot table stuff later to
  // read the cache even if producer is skipped.
  // Key chosen "randomly" by mashing on my keyboard.
  composer.startReplaceGroup(-895983)

  // We don't consider producer in this change detection code since we don't want to treat it as a key.
  var dirty = changed
  if ((changed and 0b110) == 0) {
    dirty = changed or (if (composer.changed(arg1)) 0b100 else 0b010)
  }
  if ((changed and 0b110_000) == 0) {
    dirty = dirty or (if (composer.changed(arg2)) 0b100_000 else 0b010_000)
  }
  if ((changed and 0b110_000_000) == 0) {
    dirty = dirty or (if (composer.changed(arg3)) 0b100_000_000 else 0b010_000_000)
  }

  val newValue = if ((dirty and 0b010_010_011) == 0b010_010_010 && composer.skipping) {
    composer.skipToGroupEnd()
    Composer.Empty
  } else {
    (producer as (Composer, Int) -> R).invoke(composer, 0)
  }

  composer.endReplaceGroup()
  // endregion

  // region Update cache
  // Cache the return value in case we skipped above. Composer APIs require always reading the value
  // first, and then calling updateRememberedValue the first time or optionally on subsequent
  // recompositions.
  val oldValue = composer.rememberedValue()
  val returnValue = if (
    oldValue === Composer.Empty ||
    (newValue !== Composer.Empty && newValue != oldValue)
  ) {
    // Update the cache.
    if (newValue === Composer.Empty) error("No new value was calculated.")
    composer.updateRememberedValue(newValue)
    newValue
  } else {
    // Producer was skipped (or returned the same value), return from the cache.
    oldValue
  }
  // endregion

  composer.endReplaceGroup()
  return returnValue as R
}

@Suppress("SuspiciousEqualsCombination", "UNCHECKED_CAST", "NAME_SHADOWING")
@PublishedApi
internal fun <R> rememberSkippableAndRestartableComposableImpl(
  arg1: Any?,
  arg2: Any?,
  arg3: Any?,
  producer: @Composable () -> R,
  callerRecomposeScope: RecomposeScope,
  composer: Composer,
  changed: Int,
  invalidateCallerOnNewValue: Boolean
): R {
  // Outer group is restartable: This should wrap the entire body of this function (except the actual
  // return statement) and is what defines the recompose scope for producer.
  // Key chosen "randomly" by mashing on my keyboard.
  composer.startRestartGroup(23975234)

  // Only gets set if we end up composing producer this invocation.
  var newValue: Any? = Composer.Empty

  // region Recompose producer
  // Inner group is necessary to be able to skip calling producer. We need a nested group because we
  // only want to skip calling producer, we still need to do other slot table stuff later to
  // read the cache even if producer is skipped.
  // Key chosen "randomly" by mashing on my keyboard.
  composer.startReplaceGroup(-895982)

  // We don't consider producer in this change detection code since we don't want to treat it as a key.
  var dirty = changed
  if ((changed and 0b110) == 0) {
    dirty = changed or (if (composer.changed(arg1)) 0b100 else 0b010)
  }
  if ((changed and 0b110_000) == 0) {
    dirty = dirty or (if (composer.changed(arg2)) 0b100_000 else 0b010_000)
  }
  if ((changed and 0b110_000_000) == 0) {
    dirty = dirty or (if (composer.changed(arg3)) 0b100_000_000 else 0b010_000_000)
  }
  if ((dirty and 0b010_010_011) == 0b010_010_010 && composer.skipping) {
    composer.skipToGroupEnd()
  } else {
    newValue = (producer as (Composer, Int) -> R).invoke(composer, 0)
  }

  composer.endReplaceGroup()
  // endregion

  // region Update cache
  // Cache the return value in case we skipped above. Composer APIs require always reading the value
  // first, and then calling updateRememberedValue the first time or optionally on subsequent
  // recompositions.
  val oldValue = composer.rememberedValue()
  val returnValue = if (
    oldValue === Composer.Empty ||
    (newValue !== Composer.Empty && newValue != oldValue)
  ) {
    // Update the cache.
    if (newValue === Composer.Empty) error("No new value was calculated.")
    composer.updateRememberedValue(newValue)

    // When we're recomposed directly, we obviously can't return returnValue to the original caller, so
    // just invalidate it instead. It will eventually recompose after we're done in the same frame, and
    // when it does so it should hit the cache (unless the caller passes a new producer).
    if (invalidateCallerOnNewValue) {
      callerRecomposeScope.invalidate()
    }
    newValue
  } else {
    // Producer was skipped (or returned the same value), return from the cache.
    oldValue
  }
  // endregion

  composer.endRestartGroup()?.updateScope { composer, changed ->
    // This lambda is called when producer is invalidated. The lambda must create a restartable group
    // with the same key to preserve positional identity.
    rememberSkippableAndRestartableComposableImpl(
      arg1 = arg1,
      arg2 = arg2,
      arg3 = arg3,
      producer = producer,
      callerRecomposeScope = callerRecomposeScope,
      composer = composer,
      changed = changed,
      invalidateCallerOnNewValue = true,
    )
  }
  return returnValue as R
}
