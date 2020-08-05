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
@file:Suppress("RemoveEmptyParenthesesFromAnnotationEntry")

package com.squareup.workflow.ui.compose.internal

import androidx.compose.runtime.CompositionReference
import com.squareup.workflow.ui.ViewEnvironment
import com.squareup.workflow.ui.ViewEnvironmentKey

/**
 * Holds a [CompositionReference] that can be passed to [setContent] to create a composition that is
 * a child of another composition. Subcompositions get ambients and other compose context from their
 * parent, and propagate invalidations, which allows ambients provided around a [WorkflowRendering]
 * call to be read by nested Compose-based view factories.
 *
 * When [WorkflowRendering] is called, it will store an instance of this class in the
 * [ViewEnvironment]. `ComposeViewFactory` pulls the reference out of the environment and uses it to
 * link its composition to the outer one.
 */
internal class ParentComposition(
  var reference: CompositionReference? = null
) {
  companion object : ViewEnvironmentKey<ParentComposition>(ParentComposition::class) {
    override val default: ParentComposition get() = ParentComposition()
  }
}
