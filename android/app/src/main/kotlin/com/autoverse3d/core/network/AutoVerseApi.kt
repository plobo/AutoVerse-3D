package com.autoverse3d.core.network

import com.autoverse3d.domain.model.AssetManifest
import com.autoverse3d.domain.model.AssistantRequest
import com.autoverse3d.domain.model.AssistantResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Retrofit client for the AutoVerse backend (docs/API_DOCUMENTATION.md). No API keys live in the app. */
interface AutoVerseApi {
    @POST("v1/ai/automotive-assistant")
    suspend fun ask(@Body request: AssistantRequest): AssistantResponse

    @GET("v1/assets/{manifestId}")
    suspend fun manifest(@Path("manifestId") manifestId: String): AssetManifest
}
