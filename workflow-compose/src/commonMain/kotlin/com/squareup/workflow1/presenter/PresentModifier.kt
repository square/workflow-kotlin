package com.squareup.workflow1.presenter

/**
 * The fundamental [PresenterModifier] that wraps a single presenter node with a custom
 * [PresenterPolicy].
 */
fun PresenterModifier.present(
  presenter: PresenterPolicyScope.(ChildPresenter) -> Unit
): PresenterModifier = TODO()
