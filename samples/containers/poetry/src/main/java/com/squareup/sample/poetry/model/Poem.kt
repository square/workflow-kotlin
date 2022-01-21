package com.squareup.sample.poetry.model

class Poem(
  val title: String,
  val poet: Poet,
  val stanzas: List<List<String>>
) {
  val initialStanzas = stanzas.map { lines -> lines[0].trim() }

  override fun toString(): String {
    return "Poem($title)"
  }

  companion object {
    val allPoems: List<Poem> = listOf(
      TheConquerorWorm,
      ToHelen,
      Raven,
      TheTyger
    )
  }
}
