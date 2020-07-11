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

package com.squareup.workflow1.ui

import kotlin.RequiresOptIn.Level.ERROR
import kotlin.annotation.AnnotationRetention.BINARY

/**
 * Marks Workflow user interface APIs which are still in flux. Annotated code SHOULD NOT be used
 * in library code or app code that you are not prepared to update when changing even minor
 * workflow versions. Proceed with caution, and be ready to have the rug pulled out from under you.
 */
@Target(
    AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION,
    AnnotationTarget.TYPEALIAS
)
@MustBeDocumented
@Retention(value = BINARY)
@RequiresOptIn(level = ERROR)
annotation class WorkflowUiExperimentalApi
