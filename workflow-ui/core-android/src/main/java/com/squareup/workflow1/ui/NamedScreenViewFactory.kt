// package com.squareup.workflow1.ui
//
// import android.content.Context
// import android.view.View
// import android.view.ViewGroup
// import kotlin.reflect.KClass
//
// /**
//  * [ScreenViewFactory] that allows views to display instances of [NamedScreen]. Delegates
//  * to the factory for [NamedScreen.wrapped].
//  */
// @WorkflowUiExperimentalApi
// internal object NamedScreenViewFactory : ScreenViewFactory<NamedScreen<*>> {
//   override val type = NamedScreen::class
//
//   override fun buildView(
//     contextForNewView: Context,
//     container: ViewGroup?
//   ): View {
//     TODO("ray")
//   }
//
//   override fun updateView(
//     view: View,
//     rendering: NamedScreen<*>,
//     viewEnvironment: ViewEnvironment
//   ) {
//     TODO("ray")
//   }
// }
