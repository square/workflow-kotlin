// package com.squareup.workflow1.ui.container
//
// import android.view.ViewGroup.LayoutParams
// import android.view.ViewGroup.LayoutParams.MATCH_PARENT
// import com.squareup.workflow1.ui.ManualScreenViewFactory
// import com.squareup.workflow1.ui.R
// import com.squareup.workflow1.ui.ScreenViewFactory
// import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
// import com.squareup.workflow1.ui.bindShowRendering
//
// @WorkflowUiExperimentalApi
// internal object BackStackScreenViewFactory : ScreenViewFactory<BackStackScreen<*>>
// by ManualScreenViewFactory(
//   type = BackStackScreen::class,
//   viewConstructor = { initialRendering, initialEnv, context, _ ->
//     BackStackContainer(context)
//       .apply {
//         id = R.id.workflow_back_stack_container
//         layoutParams = (LayoutParams(MATCH_PARENT, MATCH_PARENT))
//         bindShowRendering(initialRendering, initialEnv, ::update)
//       }
//   }
// )
