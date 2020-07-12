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
package com.squareup.workflow1

/**
 * Coordinates mutations to a Map initialized with [withNewTransaction], providing access to a
 * [MutableMap] via [Mutator.withExistingTransaction].
 *
 * The owner of the source map should call [withNewTransaction] and pass a block in which code
 * running on the same thread can access the mutable map by calling
 * [Mutator.withExistingTransaction]. Once the owner's block returns, [withNewTransaction] will
 * return with the return value of the block as well as an immutable copy of the map with all
 * modifications applied.
 *
 * The transaction is only visible on the same thread as it was created. Different threads will not
 * see the transaction.
 *
 * Example:
 * ```
 * val transactor = MapTransactor<String, Int>()
 *
 * val (values, _) = transactor.withNewTransaction(emptyMap()) {
 *   doStuff()
 *   doMoreStuff()
 * }
 * failToDoStuff()
 *
 * assertEquals(
 *   mapOf(
 *     "zero" to 0,
 *     "one" to 1,
 *     "the answer to everything" to 42
 *   ),
 *   values
 * )
 *
 * fun doStuff() {
 *   transactor.withExistingTransaction {
 *     it["zero"] = 0
 *   }
 * }
 *
 * fun doMoreStuff() {
 *   transactor.withExistingTransaction {
 *     it["the answer to everything"] = 42
 *     it["one"] = it["zero"] + 1
 *   }
 * }
 *
 * fun failToDoStuff() {
 *   transactor.withExistingTransaction {
 *     // Not executed since not invoked inside transaction.
 *   }
 * }
 * ```
 */
internal class MapTransactor<K, V> {
  private val currentTransaction = ThreadLocal<MutableMap<K, V>>()

  val mutator: Mutator = Mutator()

  /**
   * This _MUST_ only be called either from the render method or from a WorkflowAction's apply
   * method.
   */
  fun <R> withNewTransaction(
    initialValues: Map<K, V>,
    block: () -> R
  ): Pair<Map<K, V>, R> {
    check(currentTransaction.get() == null) {
      "Nested transactions are not currently supported."
    }

    val transaction = initialValues.toMutableMap()
    currentTransaction.set(transaction)
    val returnValue = try {
      block()
    } finally {
      currentTransaction.remove()
    }

    return Pair(transaction.toMap(), returnValue)
  }

  inner class Mutator {
    val isInTransaction: Boolean get() = currentTransaction.get() != null

    /**
     * If currently in a transaction, calls [block] with the values from the current transaction.
     */
    inline fun withExistingTransaction(block: (MutableMap<K, V>) -> Unit) =
      currentTransaction.get()
          ?.let(block)
  }
}
