package com.squareup.workflow1.internal.compose.runtime

import com.squareup.workflow1.Snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * NOTE: at the time these tests were written, [SerializableSaveableStateRegistry.toSnapshot] is
 * effectively a no-op (its `writeTo` extension is commented out in the production source). That
 * makes the round-trip
 * `registry.toSnapshot() -> SerializableSaveableStateRegistry(snapshot)` non-functional: the
 * resulting Snapshot has empty bytes, and reconstructing from it throws while trying to read the
 * length prefix. The tests below cover everything else: the canBeSaved predicate, the
 * restoredValues constructor, the registerProvider/consumeRestored paths, and explicitly pin the
 * current broken round-trip behavior so it'll need to be updated when the impl is finished.
 */
internal class SerializableSaveableStateRegistryTest {

  @Test fun canBeSaved_accepts_serializable_value() {
    val registry = SerializableSaveableStateRegistry(restoredValues = null)
    assertTrue(registry.canBeSaved("a string"))
    assertTrue(registry.canBeSaved(42))
    assertTrue(registry.canBeSaved(arrayListOf(1, 2, 3)))
  }

  @Test fun canBeSaved_rejects_non_serializable_value() {
    class NotSerializable
    val registry = SerializableSaveableStateRegistry(restoredValues = null)
    assertFalse(registry.canBeSaved(NotSerializable()))
  }

  @Test fun canBeSaved_rejects_function_even_if_serializable() {
    // Kotlin lambdas implement Serializable, but actually serializing them tends to fail at
    // runtime, so the registry filters them out explicitly.
    val lambda: () -> Int = { 42 }
    val registry = SerializableSaveableStateRegistry(restoredValues = null)
    assertFalse(registry.canBeSaved(lambda))
  }

  @Test fun consumeRestored_returns_null_when_no_value_was_provided() {
    val registry = SerializableSaveableStateRegistry(restoredValues = null)
    assertEquals(null, registry.consumeRestored("absent"))
  }

  @Test fun consumeRestored_returns_first_provided_value_for_a_key() {
    val restored = mapOf("k" to listOf<Any?>("v1", "v2"))
    val registry = SerializableSaveableStateRegistry(restoredValues = restored)
    assertEquals("v1", registry.consumeRestored("k"))
    // Subsequent consume picks up the next value, then null when exhausted.
    assertEquals("v2", registry.consumeRestored("k"))
    assertEquals(null, registry.consumeRestored("k"))
  }

  @Test fun registered_provider_value_appears_in_performSave() {
    val registry = SerializableSaveableStateRegistry(restoredValues = null)
    val entry = registry.registerProvider(key = "k") { "value-from-provider" }
    val saved = registry.performSave()
    assertEquals(listOf<Any?>("value-from-provider"), saved["k"])
    entry.unregister()
    val savedAfterUnregister = registry.performSave()
    assertFalse(savedAfterUnregister.containsKey("k"))
  }

  @Test fun toSnapshot_returns_a_snapshot_object() {
    // The snapshot is currently empty since writeTo is not implemented, but we still verify the
    // method does not throw and returns a non-null Snapshot. This pins the invariant that
    // toSnapshot() is at least call-safe with no registered providers.
    val registry = SerializableSaveableStateRegistry(restoredValues = null)
    val snapshot = registry.toSnapshot()
    // The current broken implementation produces empty bytes. When writeTo is implemented this
    // assertion will need to be updated.
    assertEquals(0, snapshot.bytes.size)
  }

  @Test fun reconstruct_from_empty_snapshot_throws_eof() {
    // Documents the current broken round-trip. Once writeTo is implemented and writes a 0-length
    // map for the empty case (or some empty marker), this test should be inverted to verify a
    // graceful round-trip.
    val emptySnapshot = Snapshot.write { /* no bytes */ }
    assertFails {
      // Constructing from an empty snapshot tries to read a length prefix that isn't there.
      SerializableSaveableStateRegistry(emptySnapshot).also {
        // Just to be safe, exercise it.
        it.canBeSaved("anything")
      }
    }
  }

  @Test fun canBeSaved_rejects_nested_function_in_collection_is_callers_responsibility() {
    // SaveableStateRegistry's canBeSaved is per-value, not recursive. A list containing a lambda
    // will pass canBeSaved (the list itself is Serializable), even though serializing it would
    // fail. Document the contract here so anyone changing the predicate sees this expectation.
    val listWithLambda: List<Any?> = listOf({ 42 })
    val registry = SerializableSaveableStateRegistry(restoredValues = null)
    assertTrue(registry.canBeSaved(listWithLambda))
  }
}
