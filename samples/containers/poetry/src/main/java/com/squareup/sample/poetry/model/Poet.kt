package com.squareup.sample.poetry.model

private const val WIKIPEDIA = "https://en.wikipedia.org/wiki/"

@Suppress("UNUSED_PARAMETER", "unused")
enum class Poet(
  val fullName: String,
  bioUrl: String
) {
  Blake("William Blake", WIKIPEDIA + "William_Blake"),
  Poe("Edgar Allan Poe", WIKIPEDIA + "Edgar_Allan_Poe")
}
