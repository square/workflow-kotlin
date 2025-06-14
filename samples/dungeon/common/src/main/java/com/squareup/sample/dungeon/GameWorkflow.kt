package com.squareup.sample.dungeon

import com.squareup.sample.dungeon.ActorWorkflow.ActorProps
import com.squareup.sample.dungeon.ActorWorkflow.ActorRendering
import com.squareup.sample.dungeon.Direction.DOWN
import com.squareup.sample.dungeon.Direction.LEFT
import com.squareup.sample.dungeon.Direction.RIGHT
import com.squareup.sample.dungeon.Direction.UP
import com.squareup.sample.dungeon.GameWorkflow.GameRendering
import com.squareup.sample.dungeon.GameWorkflow.Output
import com.squareup.sample.dungeon.GameWorkflow.Output.PlayerWasEaten
import com.squareup.sample.dungeon.GameWorkflow.Output.Vibrate
import com.squareup.sample.dungeon.GameWorkflow.Props
import com.squareup.sample.dungeon.GameWorkflow.State
import com.squareup.sample.dungeon.PlayerWorkflow.Rendering
import com.squareup.sample.dungeon.board.Board
import com.squareup.sample.dungeon.board.Board.Location
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Worker
import com.squareup.workflow1.action
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.ui.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.roundToLong
import kotlin.random.Random

private val ignoreInput: (Direction) -> Unit = {}

class GameWorkflow(
  private val playerWorkflow: PlayerWorkflow,
  private val aiWorkflows: List<ActorWorkflow>,
  private val random: Random
) : StatefulWorkflow<Props, State, Output, GameRendering>() {

  /**
   * @param board Should not change while the game is running.
   * @param paused If true, the game will still be rendered but none of the AI will tick and no
   * input events will be accepted. After the game is finished, this flag has no effect (this
   * workflow will effectively operate as "paused" until it's torn down).
   */
  data class Props(
    val board: Board,
    val ticksPerSecond: Int = 15,
    val paused: Boolean = false
  )

  data class State(
    val game: Game
  )

  sealed class Output {
    /**
     * Emitted by [GameWorkflow] if the controller should be vibrated.
     */
    object Vibrate : Output()

    object PlayerWasEaten : Output()
  }

  data class GameRendering(
    val board: Board,
    val gameOver: Boolean = false,
    val onStartMoving: (Direction) -> Unit,
    val onStopMoving: (Direction) -> Unit
  ) : Screen

  override fun initialState(
    props: Props,
    snapshot: Snapshot?
  ): State {
    val board = props.board
    return State(
      game = Game(
        playerLocation = random.nextEmptyLocation(board),
        aiLocations = aiWorkflows.map { random.nextEmptyLocation(board) }
      )
    )
  }

  override fun onPropsChanged(
    old: Props,
    new: Props,
    state: State
  ): State {
    check(old.board == new.board) { "Expected board to not change during the game." }
    return state
  }

  override fun render(
    renderProps: Props,
    renderState: State,
    context: RenderContext<Props, State, Output>
  ): GameRendering {
    val running = !renderProps.paused && !renderState.game.isPlayerEaten
    // Stop actors from ticking if the game is paused or finished.
    val ticker: Worker<Long> =
      if (running) TickerWorker(renderProps.ticksPerSecond) else Worker.finished()
    val game = renderState.game
    val board = renderProps.board

    // Render the player.
    val playerInput = ActorProps(board, game.playerLocation, ticker)
    val playerRendering = context.renderChild(playerWorkflow, playerInput)

    // Render all the other actors.
    val aiRenderings = aiWorkflows.zip(game.aiLocations)
      .mapIndexed { index, (aiWorkflow, aiLocation) ->
        val aiInput = ActorProps(board, aiLocation, ticker)
        aiLocation to context.renderChild(aiWorkflow, aiInput, key = index.toString())
      }

    // If the game is paused or finished, just render the board without ticking.
    if (running) {
      context.runningWorker(ticker) { tick ->
        return@runningWorker updateGame(
          renderProps.ticksPerSecond,
          tick,
          playerRendering,
          aiRenderings
        )
      }
    }

    val aiOverlay = aiRenderings.map { (a, b) -> a to b.avatar }
      .toMap()
    val renderedBoard = board.withOverlay(
      aiOverlay + (game.playerLocation to playerRendering.actorRendering.avatar)
    )
    return GameRendering(
      board = renderedBoard,
      onStartMoving = if (running) playerRendering.onStartMoving else ignoreInput,
      onStopMoving = if (running) playerRendering.onStopMoving else ignoreInput
    )
  }

  override fun snapshotState(state: State): Snapshot? = null

  /**
   * Calculate new locations for player and other actors.
   */
  private fun updateGame(
    ticksPerSecond: Int,
    tick: Long,
    playerRendering: Rendering,
    aiRenderings: List<Pair<Location, ActorRendering>>
  ) = action("updateGame") {
    // Calculate if this tick should result in movement based on the movement's speed.
    fun Movement.isTimeToMove(): Boolean {
      val ticksPerCell = (ticksPerSecond / cellsPerSecond).roundToLong()
      return tick % ticksPerCell == 0L
    }

    // Execute player movement.
    var output: Output? = null
    var newPlayerLocation: Location = state.game.playerLocation
    if (playerRendering.actorRendering.movement.isTimeToMove()) {
      val moveResult = state.game.playerLocation.move(
        playerRendering.actorRendering.movement,
        props.board
      )
      newPlayerLocation = moveResult.newLocation
      if (moveResult.collisionDetected) output = Vibrate
    }

    // Execute AI movement.
    val newAiLocations = aiRenderings.map { (location, rendering) ->
      return@map if (rendering.movement.isTimeToMove()) {
        location.move(rendering.movement, props.board)
          // Don't care about collisions.
          .newLocation
      } else {
        location
      }
    }

    val newGame = state.game.copy(
      playerLocation = newPlayerLocation,
      aiLocations = newAiLocations
    )

    // Check if AI captured player.
    if (newGame.isPlayerEaten) {
      state = state.copy(game = newGame)
      setOutput(PlayerWasEaten)
    } else {
      state = state.copy(game = newGame)
      output?.let { setOutput(it) }
    }
  }
}

private fun Random.nextEmptyLocation(board: Board): Location =
  generateSequence { nextLocation(board.width, board.height) }
    .first { (x, y) -> board[x, y].isEmpty }

private fun Random.nextLocation(
  width: Int,
  height: Int
) = Location(nextInt(width), nextInt(height))

/**
 * Creates a [Worker] that emits [ticksPerSecond] ticks every second.
 *
 * The emitted value is a monotonically-increasing integer.
 * Workers that have the same [ticksPerSecond] value will be considered equivalent.
 */
private class TickerWorker(private val ticksPerSecond: Int) : Worker<Long> {

  override fun doesSameWorkAs(otherWorker: Worker<*>): Boolean =
    otherWorker is TickerWorker && ticksPerSecond == otherWorker.ticksPerSecond

  override fun run(): Flow<Long> = flow {
    val periodMs = 1000L / ticksPerSecond
    var count = 0L
    while (true) {
      emit(count++)
      delay(periodMs)
    }
  }

  override fun toString(): String = "TickerWorker(ticksPerSecond=$ticksPerSecond)"
}

private data class MoveResult(
  val newLocation: Location,
  val collisionDetected: Boolean
)

private fun Location.move(
  movement: Movement,
  board: Board
): MoveResult {
  var collisionDetected = false
  var (x, y) = this
  if (LEFT in movement) x -= 1
  if (RIGHT in movement) x += 1
  // Don't let the player leave the board.
  x = x.coerceIn(0 until board.width)
  // Don't allow collisions with obstacles on the board.
  if (!board[x, y].isEmpty) {
    collisionDetected = true
    x = this.x
  }

  if (UP in movement) y -= 1
  if (DOWN in movement) y += 1
  y = y.coerceIn(0 until board.height)
  if (!board[x, y].isEmpty) {
    collisionDetected = true
    y = this.y
  }

  return MoveResult(Location(x, y), collisionDetected)
}
