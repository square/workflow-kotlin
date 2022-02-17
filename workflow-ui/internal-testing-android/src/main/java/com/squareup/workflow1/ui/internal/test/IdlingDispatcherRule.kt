package com.squareup.workflow1.ui.internal.test

import androidx.test.espresso.IdlingRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
public object IdlingDispatcherRule : TestWatcher() {

  private lateinit var dispatcher: IdlingDispatcher

  override fun starting(description: Description?) {
    dispatcher = IdlingDispatcher(Dispatchers.Main.immediate)
    Dispatchers.setMain(dispatcher)

    IdlingRegistry.getInstance().register(dispatcher.counter)
  }

  override fun finished(description: Description?) {
    Dispatchers.resetMain()
    IdlingRegistry.getInstance().unregister(dispatcher.counter)
  }
}
