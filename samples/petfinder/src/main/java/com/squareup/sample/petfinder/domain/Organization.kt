package com.squareup.sample.petfinder.domain

data class Organization (
  val id: String,
  val name: String,
  val email: String,
  val phone: String,
  val address: Address,
  val hours: Hours,
  val url: String,
  val website: String? = null,
  val missionStatement: Any? = null,
  val adoption: Adoption,
  val socialMedia: SocialMedia,
  val photos: List<Photo>,
  val distance: Double,
)
