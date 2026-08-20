package com.squareup.sample.dungeon.board

import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import okio.Buffer
import org.junit.Test

class ParserTest {

  @Test
  fun `parseBoardMetadata throws when empty`() {
    val board = ""

    val error =
      assertFailsWith<IllegalArgumentException> { board.toBufferedSource().parseBoardMetadata() }
    assertThat(error).hasMessageThat().contains("No board metadata found in stream")
  }

  @Test
  fun `parseBoardMetadata throws when missing`() {
    val board =
      """
      🌳🌳
      🌳🌳
      """
        .trimIndent()

    val error =
      assertFailsWith<IllegalArgumentException> { board.toBufferedSource().parseBoardMetadata() }
    assertThat(error).hasMessageThat().contains("No board metadata found in stream")
  }

  @Test
  fun `parseBoardMetadata throws when header not closed`() {
    val board =
      """
      ---
      🌳🌳
      🌳🌳
      """
        .trimIndent()

    val error =
      assertFailsWith<IllegalArgumentException> { board.toBufferedSource().parseBoardMetadata() }
    assertThat(error).hasMessageThat().isEqualTo("Expected --- but found EOF.")
  }

  @Test
  fun `parseBoardMetadata throws when document is empty`() {
    val board =
      """
      ---
      ---
      """
        .trimIndent()

    val error =
      assertFailsWith<IllegalArgumentException> { board.toBufferedSource().parseBoardMetadata() }
    assertThat(error).hasMessageThat().isEqualTo("Error parsing board metadata.")
    assertThat(error).hasCauseThat().hasMessageThat().contains("Expected start of the object '{")
  }

  @Test
  fun `parseBoardMetadata parses valid header`() {
    val board =
      """
      ---
      {name: "Såm ✅"}
      ---
      """
        .trimIndent()

    val metadata = board.toBufferedSource().parseBoardMetadata()
    assertThat(metadata).isEqualTo(BoardMetadata(name = "Såm ✅"))
  }

  @Test
  fun `parse parses metadata and board`() {
    val board =
      """
      ---
      {name: Foo}
      ---
      🌳🌳
      🌳🌳
      """
        .trimIndent()
        // Don't call parseBoard on the string directly, since that fakes the metadata.
        .toBufferedSource()
        .parseBoard()

    assertThat(board.cells).isNotEmpty()
    assertThat(board.metadata).isEqualTo(BoardMetadata(name = "Foo"))
  }

  @Test
  fun square() {
    val board =
      """
      🌳🌳
      🌳🌳
      """
        .trimIndent()
        .parseBoard()

    assertThat(board.width).isEqualTo(2)
    assertThat(board.height).isEqualTo(2)
  }

  @Test
  fun `pads width centered when tall odd`() {
    val board =
      """
      🌳🌳
      🌳🌳
      🌳🌳
      """
        .trimIndent()
        .parseBoard()

    assertThat(board.width).isEqualTo(3)
    assertThat(board.height).isEqualTo(3)
    assertThat(board)
      .isEqualTo(
        """
        |🌳🌳 
        |🌳🌳 
        |🌳🌳 
        """
          .trimMargin()
          .parseBoard()
      )
  }

  @Test
  fun `pads width centered when tall even`() {
    val board =
      """
      🌳🌳
      🌳🌳
      🌳🌳
      🌳🌳
      """
        .trimIndent()
        .parseBoard()

    assertThat(board.width).isEqualTo(4)
    assertThat(board.height).isEqualTo(4)
    assertThat(board)
      .isEqualTo(
        """
        | 🌳🌳 
        | 🌳🌳 
        | 🌳🌳 
        | 🌳🌳 
        """
          .trimMargin()
          .parseBoard()
      )
  }

  @Test
  fun `pads height centered when wide odd`() {
    val board =
      """
      🌳🌳🌳
      🌳🌳🌳
      """
        .trimIndent()
        .parseBoard()

    assertThat(board.width).isEqualTo(3)
    assertThat(board.height).isEqualTo(3)
    assertThat(board)
      .isEqualTo(
        """
        |🌳🌳🌳
        |🌳🌳🌳
        |   
        """
          .trimMargin()
          .parseBoard()
      )
  }

  @Test
  fun `pads height centered when wide even`() {
    val board =
      """
      🌳🌳🌳🌳
      🌳🌳🌳🌳
      """
        .trimIndent()
        .parseBoard()

    assertThat(board.width).isEqualTo(4)
    assertThat(board.height).isEqualTo(4)
    assertThat(board)
      .isEqualTo(
        """
        |    
        |🌳🌳🌳🌳
        |🌳🌳🌳🌳
        |    
        """
          .trimMargin()
          .parseBoard()
      )
  }

  @Suppress("BlockingMethodInNonBlockingContext")
  private fun String.parseBoard(): Board =
    toBufferedSource().parseBoard(metadata = BoardMetadata("test"))

  private fun String.toBufferedSource() = Buffer().writeUtf8(this)
}
