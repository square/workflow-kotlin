@file:Suppress("DEPRECATION")

package com.squareup.workflow1.ui.internal.test

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.squareup.workflow1.ui.NamedScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub

/**
 * Helper for testing workflow-ui code in UI tests.
 *
 * The content view of the activity is a [WorkflowViewStub], which you can control by calling
 * [setRendering].
 *
 * Typical usage:
 * 1. Create an `ActivityScenarioRule` or `AndroidComposeRule` and pass this activity type.
 * 2. In your `@Before` method, set the [viewEnvironment].
 * 3. In your tests, call [setRendering] to update the stub.
 *
 * You can also test configuration changes by calling `ActivityScenarioRule.recreate()`. By default,
 * the [viewEnvironment] and last rendering will be restored when the view is re-created. You can
 * also retain your own data by mutating [customNonConfigurationData]. If you don't want the
 * rendering to be automatically restored, set [restoreRenderingAfterConfigChange] to false before
 * calling `recreate()`.
 */
@WorkflowUiExperimentalApi
public open class WorkflowUiTestActivity : AppCompatActivity() {

  private val rootStub by lazy { WorkflowViewStub(this) }
  private var renderingCounter = 0
  private lateinit var lastRendering: Screen

  /**
   * The [ViewEnvironment] used to create views for renderings passed to [setRendering].
   * This *must* be set before the first call to [setRendering].
   * Once set, the value is retained across configuration changes.
   */
  public lateinit var viewEnvironment: ViewEnvironment

  /**
   * The [View] that was created to display the last rendering passed to [setRendering].
   */
  public val rootRenderedView: View get() = rootStub.actual

  /**
   * Key-value store for custom values that should be retained across configuration changes.
   * Use this instead of using [getLastNonConfigurationInstance] or
   * [getLastCustomNonConfigurationInstance] directly.
   */
  public val customNonConfigurationData: MutableMap<String, Any?> = mutableMapOf()

  /**
   * Simulates the effect of having the activity backed by a real workflow runtime – remembers the
   * actual render instance across recreation and will immediately set it on the new container in
   * [onCreate].
   *
   * True by default. If you need to change, do so before calling `recreate()`.
   */
  public var restoreRenderingAfterConfigChange: Boolean = true

  /**
   * Causes the next [setRendering] call to force a new view to be created, even if it otherwise wouldn't
   * be (i.e. because the rendering is compatible with the previous one).
   */
  public fun recreateViewsOnNextRendering() {
    renderingCounter++
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(rootStub)

    (lastCustomNonConfigurationInstance as NonConfigurationData?)?.let { data ->
      viewEnvironment = data.viewEnvironment
      customNonConfigurationData.apply {
        clear()
        putAll(data.customData)
      }
      // setRendering must be called last since it may consume the other values.
      data.lastRendering?.let(::setRendering)
    }
  }

  final override fun onRetainCustomNonConfigurationInstance(): Any = NonConfigurationData(
    viewEnvironment = viewEnvironment,
    lastRendering = lastRendering.takeIf { restoreRenderingAfterConfigChange },
    customData = customNonConfigurationData,
  )

  /**
   * Updates the [WorkflowViewStub] to a new rendering value.
   *
   * If [recreateViewsOnNextRendering] was previously called, the old view tree will be torn down
   * and re-created from scratch.
   */
  public fun setRendering(rendering: Screen): View {
    lastRendering = rendering
    val named = NamedScreen(
      actual = rendering,
      name = renderingCounter.toString()
    )
    return rootStub.show(named, viewEnvironment)
  }

  private class NonConfigurationData(
    val viewEnvironment: ViewEnvironment,
    val lastRendering: Screen?,
    val customData: MutableMap<String, Any?>,
  )
}
