package com.squareup.workflow1.mocks.workflows1

import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow

public class JsMockWorkflow1 : Workflow<Nothing, Nothing, Nothing> {
  override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, Nothing, Nothing> =
    throw NotImplementedError()
}

public class JsMockWorkflow2 : Workflow<Nothing, Nothing, Nothing> {
  override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, Nothing, Nothing> =
    throw NotImplementedError()
}
