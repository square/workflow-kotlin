package com.squareup.workflow1.mocks.workflows2

import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow

// This is purposely duplicated to be like com.squareup.workflow1.mocks.workflows1.JsMockWorkflow1,
// but with a slightly different Output.
public class JsMockWorkflow1 : Workflow<Nothing, String, Nothing> {
  override fun asStatefulWorkflow(): StatefulWorkflow<Nothing, *, String, Nothing> =
    throw NotImplementedError()
}
