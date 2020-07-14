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
@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1

/**
 * Deprecated, legacy version of [WorkflowAction]. Kept around for migration.
 */
@Deprecated("Use WorkflowAction")
@Suppress("DEPRECATION")
abstract class MutatorWorkflowAction<in PropsT, StateT, out OutputT> :
    WorkflowAction<PropsT, StateT, OutputT>() {

  @Deprecated("Use WorkflowAction.Updater")
  class Mutator<S>(var state: S)

  @Deprecated("Implement WorkflowAction.apply")
  abstract fun Mutator<StateT>.apply(): OutputT?

  final override fun Updater.apply() {
    val mutator = Mutator(state)
    mutator.apply()
        ?.let { setOutput(it) }
    state = mutator.state
  }
}
