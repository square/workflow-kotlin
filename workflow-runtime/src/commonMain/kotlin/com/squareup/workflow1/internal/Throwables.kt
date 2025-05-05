package com.squareup.workflow1.internal

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Like Kotlin's [require], but uses [stackTraceKey] to create a fake top element
 * on the stack trace, ensuring that crash reporter's default grouping will create unique
 * groups for unique keys.
 *
 * So far [stackTraceKey] is only effective on JVM, it has no effect in other languages.
 *
 * @see [withKey]
 *
 * @throws IllegalArgumentException if the [value] is false.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun requireWithKey(
  value: Boolean,
  stackTraceKey: Any,
  lazyMessage: () -> Any = { "Failed requirement." }
) {
  contract {
    returns() implies value
  }
  if (!value) {
    val message = lazyMessage()
    val exception: Throwable = IllegalArgumentException(message.toString())
    throw exception.withKey(stackTraceKey)
  }
}

/**
 * Like Kotlin's [check], but uses [stackTraceKey] to create a fake top element
 * on the stack trace, ensuring that crash reporter's default grouping will create unique
 * groups for unique keys.
 *
 * So far [stackTraceKey] is only effective on JVM, it has no effect in other languages.
 *
 * @see [withKey]
 *
 * @throws IllegalStateException if the [value] is false.
 */
@OptIn(ExperimentalContracts::class)
internal inline fun checkWithKey(
  value: Boolean,
  stackTraceKey: Any,
  lazyMessage: () -> Any = { "Check failed." }
) {
  contract {
    returns() implies value
  }
  if (!value) {
    val message = lazyMessage()
    val exception: Throwable = IllegalStateException(message.toString())
    throw exception.withKey(stackTraceKey)
  }
}

/**
 * Uses [stackTraceKey] to create a fake top element on the stack trace, ensuring
 * that crash reporter's default grouping will create unique groups for unique keys.
 *
 * So far only effective on JVM, this is a pass through in other languages.
 */
internal expect fun <T : Throwable> T.withKey(stackTraceKey: Any): T
