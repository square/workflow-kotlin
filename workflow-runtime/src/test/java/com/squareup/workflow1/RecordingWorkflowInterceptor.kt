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
@file:Suppress("SuspiciousCollectionReassignment")

package com.squareup.workflow1

import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession

/**
 * Workflow interceptor that records all received events in a list for testing.
 */
@OptIn(ExperimentalWorkflowApi::class)
class RecordingWorkflowInterceptor : SimpleLoggingWorkflowInterceptor() {

  private var events: List<String> = emptyList()

  override fun logBeforeMethod(
    name: String,
    session: WorkflowSession
  ) {
    events += "BEGIN|$name"
  }

  override fun logAfterMethod(
    name: String,
    session: WorkflowSession
  ) {
    events += "END|$name"
  }

  fun consumeEvents(): List<String> = events
      .also { events = emptyList() }

  fun consumeEventNames(): List<String> = consumeEvents().map { it.substringBefore('(') }
}
