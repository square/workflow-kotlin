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

import com.squareup.workflow.internal.InlineLinkedList.InlineListNode
import kotlinx.coroutines.Job

/**
 * Holds a [Job] that represents a running [side effect][RealRenderContext.runningSideEffect], as
 * well as the key used to identify that side effect.
 */
internal class SideEffectNode(
  val key: String,
  val job: Job
) : InlineListNode<SideEffectNode> {

  override var nextListNode: SideEffectNode? = null
}
