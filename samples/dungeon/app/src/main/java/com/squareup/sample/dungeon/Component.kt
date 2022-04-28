@file:Suppress("DEPRECATION")

package com.squareup.sample.dungeon

import android.content.Context.VIBRATOR_SERVICE
import android.os.Vibrator
import androidx.appcompat.app.AppCompatActivity
import com.squareup.sample.dungeon.DungeonAppWorkflow.State.LoadingBoardList
import com.squareup.sample.dungeon.GameSessionWorkflow.State.Loading
import com.squareup.sample.timemachine.shakeable.ShakeableTimeMachineLayoutRunner
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.modal.AlertContainer
import kotlinx.coroutines.Dispatchers
import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource.Monotonic

private const val AI_COUNT = 4

/** Fake Dagger. */
@OptIn(ExperimentalTime::class)
@Suppress("MemberVisibilityCanBePrivate")
class Component(context: AppCompatActivity) {

  @OptIn(WorkflowUiExperimentalApi::class)
  val viewRegistry = ViewRegistry(
    ShakeableTimeMachineLayoutRunner,
    LoadingScreenViewFactory<LoadingBoardList>(R.string.loading_boards_list),
    BoardsListLayoutRunner,
    LoadingScreenViewFactory<Loading>(R.string.loading_board),
    GameLayoutRunner,
    BoardView,
    AlertContainer
  )

  val random = Random(System.currentTimeMillis())

  val clock = Monotonic

  @Suppress("DEPRECATION")
  val vibrator = context.getSystemService(VIBRATOR_SERVICE) as Vibrator

  val boardLoader = BoardLoader(
    ioDispatcher = Dispatchers.IO,
    assets = context.assets,
    boardsAssetPath = "boards",
    delayForFakeLoad = context::delayForFakeLoad
  )

  val playerWorkflow = PlayerWorkflow()

  val aiWorkflows = List(AI_COUNT) { AiWorkflow(random = random) }

  val gameWorkflow = GameWorkflow(playerWorkflow, aiWorkflows, random)

  val gameSessionWorkflow = GameSessionWorkflow(gameWorkflow, vibrator, boardLoader)

  val appWorkflow = DungeonAppWorkflow(gameSessionWorkflow, boardLoader)

  val timeMachineWorkflow = TimeMachineAppWorkflow(appWorkflow, clock, context)

  val timeMachineModelFactory = TimeMachineModel.Factory(
    context, timeMachineWorkflow, traceFilesDir = context.filesDir
  )
}
