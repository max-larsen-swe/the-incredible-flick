package com.github.max_larsen_swe.android.theincredibleflick.backend

import com.github.max_larsen_swe.android.theincredibleflick.data.User
import com.github.max_larsen_swe.android.theincredibleflick.data.Photo
import com.github.max_larsen_swe.android.theincredibleflick.data.PhotoList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit

object API {
    private const val endpoint = "https://www.flickr.com/"
    const val responseFormat = "json"

    object Methods {
        const val search = "flickr.photos.search"
        const val getUserInfo = "flickr.people.getInfo"
        const val getPhotoInfo = "flickr.photos.getInfo"
    }

    private val retrofit: Retrofit = Retrofit.Builder().baseUrl(endpoint).build()

    private val flickrRetrofit = retrofit.create(IFlickrApi::class.java)

    suspend fun textSearchForPhotoList(text: String): PhotoList {
        return withContext(Dispatchers.IO) {
            val response = flickrRetrofit.simpleTextSearch(text)
                .execute().body()?.string() ?: ""
            PhotoList.fromJSONResponseString(response)
        }
    }

    suspend fun getUserData(userId: String): User? {
        return withContext(Dispatchers.IO) {
            val response = flickrRetrofit.getUserInfo(userId)
                .execute().body()?.string() ?: ""
            User.fromJSONResponseString(response)
        }
    }

    suspend fun pullPhotoTags(photo: Photo): Unit {
        return withContext(Dispatchers.IO) {
            val response = flickrRetrofit.getPhotoInfo(photo.id.toString(), photo.secret)
                .execute().body()?.string() ?: ""
            Photo.parsePulledTags(photo, response)
        }
    }

    suspend fun searchForPhotosByUser(user: User): PhotoList {
        return withContext(Dispatchers.IO) {
            val response = flickrRetrofit.searchByUserId(user.id)
                .execute().body()?.string() ?: ""
            PhotoList.fromJSONResponseString(response)
        }
    }
}