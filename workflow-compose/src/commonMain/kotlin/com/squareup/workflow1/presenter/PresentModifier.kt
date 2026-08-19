package com.squareup.workflow1.presenter

/**
 * The fundamental [PresenterModifier] that wraps a single presenter node with a custom
 * [PresenterPolicy].
 */
// TODO this modifier would be a lot cheaper to implement if it could just run in the same policy
//  scope as the modified node and just read and write whatever slots it wrote, without access to
//  its children. I.e. closer to drawModifier than layoutModifier.
fun PresenterModifier.present(
  presenter: PresenterPolicyScope.(ChildPresenter) -> Unit
): PresenterModifier = this.then(SimplePresentModifierElement(presenter))

internal interface PresentModifierElement : PresenterModifier.Element {
  val presenter: PresenterPolicyScope.(ChildPresenter) -> Unit
}

private data class SimplePresentModifierElement(
  override val presenter: PresenterPolicyScope.(ChildPresenter) -> Unit
) : PresentModifierElement
