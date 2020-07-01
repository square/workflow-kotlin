/*
 * Copyright 2019 Square Inc.
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
package com.squareup.workflow.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// If you try to replace isTrue() with isTrue compilation fails.
@OptIn(WorkflowUiExperimentalApi::class)
@Suppress("UsePropertyAccessSyntax")
class CompatibleTest {
  @Test fun `Different types do not match`() {
    val able = object : Any() {}
    val baker = object : Any() {}

    assertThat(compatible(able, baker)).isFalse()
  }

  @Test fun `Same type matches`() {
    assertThat(compatible("Able", "Baker")).isTrue()
  }

  @Test fun `isCompatibleWith is honored`() {
    data class K(override val compatibilityKey: String) : Compatible

    assertThat(compatible(K("hey"), K("hey"))).isTrue()
    assertThat(compatible(K("hey"), K("ho"))).isFalse()
  }

  @Test fun `Different Compatible types do not match`() {
    abstract class A : Compatible

    class Able(override val compatibilityKey: String) : A()
    class Alpha(override val compatibilityKey: String) : A()

    assertThat(compatible(Able("Hey"), Alpha("Hey"))).isFalse()
  }
}
