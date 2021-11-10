package com.squareup.sample.helloworkflowfragment

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stub test to avoid failing b/c no tests when suppressing [HelloWorkflowFragmentAppTest]
 * on API 21.
 *
 * https://github.com/square/workflow-kotlin/issues/582
 */
@RunWith(AndroidJUnit4::class)
@OptIn(WorkflowUiExperimentalApi::class)
class StubTest {
  @Test fun fml() {
  }
}
