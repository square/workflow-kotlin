package com.squareup.workflow1

import android.os.Bundle
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SnapshotParcelsTest {

  @Test fun parcelableToSnapshot_savesAndRestores() {
    val snapshot = Bundle().apply {
      putString("key", "value")
    }.toSnapshot()
    val restored = snapshot.toParcelable<Bundle>()

    assertNotNull(restored)
    assertTrue(restored.containsKey("key"))
    assertEquals("value", restored.getString("key"))
  }
}
