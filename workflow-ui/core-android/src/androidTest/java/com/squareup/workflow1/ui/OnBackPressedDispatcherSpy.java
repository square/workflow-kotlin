package androidx.activity;

import java.util.ArrayDeque;

public class OnBackPressedDispatcherSpy {
  private final OnBackPressedDispatcher dispatcher;

  public OnBackPressedDispatcherSpy(OnBackPressedDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  public ArrayDeque<OnBackPressedCallback> callbacks() {
    return dispatcher.mOnBackPressedCallbacks;
  }
}
