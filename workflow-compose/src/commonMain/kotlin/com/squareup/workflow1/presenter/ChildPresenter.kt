package com.squareup.workflow1.presenter

/**
 * Represents a child node composed by a [NavigationPresenter]'s content composable to a [PresenterPolicy].
 * The policy can read view models published by the slot via the methods on [SlotReader].
 */
interface ChildPresenter : SlotReader
