package com.squareup.workflow1.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(WorkflowUiExperimentalApi::class)
internal class NamedScreenTest {
  object Whut : Screen
  object Hey : Screen

  @Test fun same_type_same_name_matches() {
    assertTrue {
      compatible(NamedScreen(Hey, "eh"), NamedScreen(Hey, "eh"))
    }
  }

  @Test fun same_type_diff_name_matches() {
    assertFalse {
      compatible(NamedScreen(Hey, "blam"), NamedScreen(Hey, "bloom"))
    }
  }

  @Test fun diff_type_same_name_no_match() {
    assertFalse {
      compatible(NamedScreen(Hey, "a"), NamedScreen(Whut, "a"))
    }
  }

  @Test fun recursion() {
    assertTrue {
      compatible(
        NamedScreen(NamedScreen(Hey, "one"), "ho"),
        NamedScreen(NamedScreen(Hey, "one"), "ho")
      )
    }

    assertFalse {
      compatible(
        NamedScreen(NamedScreen(Hey, "one"), "ho"),
        NamedScreen(NamedScreen(Hey, "two"), "ho")
      )
    }

    assertFalse {
      compatible(
        NamedScreen(NamedScreen(Hey, "a"), "ho"),
        NamedScreen(NamedScreen(Whut, "a"), "ho")
      )
    }
  }

  @Test fun key_recursion() {
    assertEquals(
      NamedScreen(NamedScreen(Hey, "one"), "ho").compatibilityKey,
      NamedScreen(NamedScreen(Hey, "one"), "ho").compatibilityKey
    )

    assertNotEquals(
      NamedScreen(NamedScreen(Hey, "two"), "ho").compatibilityKey,
      NamedScreen(NamedScreen(Hey, "one"), "ho").compatibilityKey
    )

    assertEquals(
      NamedScreen(NamedScreen(Whut, "a"), "ho").compatibilityKey,
      NamedScreen(NamedScreen(Whut, "a"), "ho").compatibilityKey
    )
  }

  @Test fun recursive_keys_are_legible() {
    assertEquals(
      "NamedScreen:ho(NamedScreen:one(${Hey::class}))",
      NamedScreen(NamedScreen(Hey, "one"), "ho").compatibilityKey
    )
  }

  private class Foo(override val compatibilityKey: String) : Compatible, Screen

  @Test fun the_test_Compatible_class_actually_works() {
    assertTrue { compatible(Foo("bar"), Foo("bar")) }
    assertFalse { compatible(Foo("bar"), Foo("baz")) }
  }

  @Test fun wrapping_custom_Compatible_compatibility_works() {
    assertTrue {
      compatible(NamedScreen(Foo("bar"), "name"), NamedScreen(Foo("bar"), "name"))
    }
    assertFalse {
      compatible(NamedScreen(Foo("bar"), "name"), NamedScreen(Foo("baz"), "name"))
    }
  }

  @Test fun wrapping_custom_Compatible_keys_work() {
    assertEquals(
      NamedScreen(Foo("bar"), "name").compatibilityKey,
      NamedScreen(Foo("bar"), "name").compatibilityKey
    )
    assertNotEquals(
      NamedScreen(Foo("baz"), "name").compatibilityKey,
      NamedScreen(Foo("bar"), "name").compatibilityKey
    )
  }
}
