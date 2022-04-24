package com.squareup.workflow1

import kotlin.reflect.KType

/**
 * So that we don't have to make [WorkerWorkflow] public. Xfriend-paths does not seem to work as a
 * compiler arg on multiplatform projects.
 */
public interface HasWorkerType {
  public val workerType: KType
}
