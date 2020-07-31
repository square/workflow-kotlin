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
package com.squareup.workflow1.testing

import com.squareup.workflow1.Workflow
import kotlin.reflect.KType

/**
 * If this [Workflow] is of type [WorkerWorkflow], defined in `workflow-core`, then returns the
 * value of the [WorkerWorkflow.workerType] property. The reason this is a separate function and in
 * its own file is that it relies on the kotlin compiler's friend paths to access the type.
 * Unfortunately, the IntelliJ Kotlin plugin has no knowledge of friend paths, so the IDE will
 * complain that this class and the property are inaccessible. This is wrong, and this code will
 * actually compile and work fine. It's in a separate file so that no other code is affected by this
 * false IDE compiler error.
 *
 * See https://youtrack.jetbrains.com/issue/KT-20760.
 */
internal fun Workflow<*, *, *>.workerWorkflowWorkerTypeOrNull(): KType? =
  (this as? com.squareup.workflow1.WorkerWorkflow)?.workerType
