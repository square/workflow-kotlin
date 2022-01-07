package com.squareup.sample.dungeon

import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.squareup.cycler.Recycler
import com.squareup.cycler.toDataSource
import com.squareup.sample.dungeon.DungeonAppWorkflow.DisplayBoardsListScreen
import com.squareup.sample.dungeon.board.Board
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewUpdater
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.WorkflowViewStub

/**
 * Renders a list of boards in a grid, along with previews of the boards.
 *
 * Notably, this runner uses the [Cycler](https://github.com/square/cycler) library to configure
 * a `RecyclerView`.
 */
@OptIn(WorkflowUiExperimentalApi::class)
class BoardsListLayoutUpdater(rootView: View) : ScreenViewUpdater<DisplayBoardsListScreen> {

  /**
   * Used to associate a single [ViewEnvironment] and [DisplayBoardsListScreen.onBoardSelected]
   * event handler with every item of a [DisplayBoardsListScreen].
   *
   * @see toDataSource
   */
  private data class BoardItem(
    val board: Board,
    val viewEnvironment: ViewEnvironment,
    val onClicked: () -> Unit
  )

  private val recycler =
    Recycler.adopt<BoardItem>(rootView.findViewById(R.id.boards_list_recycler)) {
      // This defines how to instantiate and bind a row of a particular item type.
      row<BoardItem, ViewGroup> {
        // This defines how to inflate and bind the layout for this row type.
        create(R.layout.boards_list_item) {
          // And this is the actual binding logic.
          bind { _, item ->
            val card: CardView = view.findViewById(R.id.board_card)
            val boardNameView: TextView = view.findViewById(R.id.board_name)
            // The board preview is actually rendered using the same ScreenViewUpdater as the actual
            // live game. It's easy to delegate to it by just putting a WorkflowViewStub in our
            // layout and giving it the Board.
            val boardPreviewView: WorkflowViewStub = view.findViewById(R.id.board_preview_stub)

            boardNameView.text = item.board.metadata.name
            boardPreviewView.show(item.board, item.viewEnvironment)

            // Gratuitous, hacky, inline test of WorkflowViewStub features.
            check(boardPreviewView.delegateHolder.view.visibility == INVISIBLE) {
              "Expected swizzled board to be INVISIBLE"
            }
            boardPreviewView.visibility = VISIBLE
            check(view.findViewById<View>(R.id.board_preview).visibility == VISIBLE) {
              "Expected swizzled board to be VISIBLE"
            }

            card.setOnClickListener { item.onClicked() }
          }
        }
      }
    }

  override fun showRendering(
    rendering: DisplayBoardsListScreen,
    viewEnvironment: ViewEnvironment
  ) {
    // Associate the viewEnvironment and event handler to each item because it needs to be used when
    // binding the RecyclerView item above.
    // Recycler is configured with a DataSource, which effectively (and often in practice) a simple
    // wrapper around a List.
    recycler.update {
      data = rendering.toDataSource(viewEnvironment)
    }
  }

  /**
   * Converts this [DisplayBoardsListScreen] into a [List] by lazily wrapping it in a
   * [BoardItem] to associate it with the [ViewEnvironment] and selection event handler from the
   * rendering.
   */
  private fun DisplayBoardsListScreen.toDataSource(
    viewEnvironment: ViewEnvironment
  ): List<BoardItem> = object : AbstractList<BoardItem>() {
    override val size: Int get() = boards.size

    override fun get(index: Int): BoardItem = BoardItem(
        board = boards[index],
        viewEnvironment = viewEnvironment,
        onClicked = { onBoardSelected(index) }
    )
  }

  companion object : ScreenViewFactory<DisplayBoardsListScreen> by ScreenViewFactory.ofLayout(
    R.layout.boards_list_layout,
    { it: View -> BoardsListLayoutUpdater(it) }
  )
}
