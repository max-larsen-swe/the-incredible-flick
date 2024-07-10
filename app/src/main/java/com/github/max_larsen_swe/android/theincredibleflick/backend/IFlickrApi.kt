package com.github.max_larsen_swe.android.theincredibleflick.backend

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface IFlickrApi {

    @GET("services/rest/")
    fun simpleTextSearch(
        @Query("text") text: String,
        @Query("method") method: String = API.Methods.search,
        @Query("safe_search") safe_search: Int = 1,
        @Query("api_key") api_key: String = Keys.API,
        @Query("format") format: String = API.responseFormat,
    ): Call<ResponseBody>

    @GET("services/rest/")
    fun getUserInfo(
        @Query("user_id") user_id: String,
        @Query("method") method: String = API.Methods.getUserInfo,
        @Query("api_key") api_key: String = Keys.API,
        @Query("format") format: String = API.responseFormat
    ): Call<ResponseBody>

    @GET("services/rest/")
    fun getPhotoInfo(
        @Query("photo_id") photo_id: String,
        @Query("secret") secret: String,
        @Query("method") method: String = API.Methods.getPhotoInfo,
        @Query("api_key") api_key: String = Keys.API,
        @Query("format") format: String = API.responseFormat
    ): Call<ResponseBody>

    @GET("services/rest/")
    fun searchByUserId(
        @Query("user_id") user_id: String,
        @Query("method") method: String = API.Methods.search,
        @Query("safe_search") safe_search: Int = 1,
        @Query("api_key") api_key: String = Keys.API,
        @Query("format") format: String = API.responseFormat,
    ): Call<ResponseBody>
}