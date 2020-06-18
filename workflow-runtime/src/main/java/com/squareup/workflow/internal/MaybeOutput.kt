/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.workflow.internal

/**
 * Simple Optional type to hold outputs. Create with either [NONE] or [of].
 */
@Suppress("UNCHECKED_CAST")
internal class MaybeOutput<out O> constructor(private val value: Any?) {

  inline val hasValue: Boolean get() = value !== NO_VALUE

  fun getValueOrThrow(): O {
    if (value === NO_VALUE) throw NoSuchElementException()
    return value as O
  }

  inline fun withValue(block: (O) -> Unit) {
    if (value !== NO_VALUE) block(value as O)
  }

  companion object {
    private val NO_VALUE = Any()

    fun none(): MaybeOutput<Nothing> = MaybeOutput(NO_VALUE)
    fun <O> of(value: O) = MaybeOutput<O>(value)
  }
}
