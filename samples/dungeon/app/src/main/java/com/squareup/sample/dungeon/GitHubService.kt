package com.squareup.sample.dungeon

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path

interface GitHubService {
  @GET("users/{user}/repos")
  suspend fun listRepos(
    @Path(
        "user"
    ) user: String?
  ): ResponseBody
}