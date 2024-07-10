package com.github.max_larsen_swe.android.theincredibleflick.data

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.nio.file.Path

data class Photo(
    val id: String,
    val owner: String = "",
    val secret: String = "",
    val server: Int = 0,
    val farm: Int = 0,
    var title: String = "",
    val isPublic: Boolean = false,
    val isFriend: Boolean = false,
    val isFamily: Boolean = false,
    //internals
    val remoteUri: String = "https://farm${farm}.staticflickr.com/${server}/${id}_${secret}.jpg",
    var downloadPath: Path? = null,
    var tags: MutableList<String> = mutableListOf(),
    var description: String = "",
    var dateTaken: String = "",
    override val type: DCType = DCType.Photo
) : DC(type) {
    companion object {
        val TAG: String = this::class.java.simpleName

        fun singleFromJSONObject(json: JSONObject): Photo {
            return Photo(
                json.getString("id"),
                json.getString("owner"),
                json.getString("secret"),
                json.getInt("server"),
                json.getInt("farm"),
                json.getString("title"),
                json.getInt("ispublic") == 1,
                json.getInt("isfriend") == 1,
                json.getInt("isfamily") == 1
            )
        }

        fun parsePulledTags(photo: Photo, responseString: String) {
            val cleanedResponse: String =
                responseString.drop("jsonFlickrApi(".length).dropLast(1)
            if (cleanedResponse.isEmpty())
                return

            try {
                val responseObject: JSONObject =
                    JSONTokener(cleanedResponse).nextValue() as JSONObject
                val data: JSONObject = responseObject.getJSONObject("photo")
                val title: String = data.getJSONObject("title").getString("_content")
                val description: String =
                    data.getJSONObject("description").getString("_content")
                val dateTaken: String = data.getJSONObject("dates").getString("taken")
                val tags: MutableList<String> = mutableListOf()
                data.getJSONObject("tags").getJSONArray("tag").let {
                    for (i in 0 until it.length()) {
                        val s: String = it.getJSONObject(i).getString("raw")
                        tags.add(s)
                    }
                }
                photo.title = title
                photo.description = description
                photo.dateTaken = dateTaken
                photo.tags = tags

            } catch (e: JSONException) {
                Log.wtf(
                    TAG,
                    "FATAL: Response schema does not match parser code: ${e.stackTraceToString()}"
                )
            } catch (th: Throwable) {
                Log.e(
                    TAG,
                    "Could not parse JSON response: $cleanedResponse ${th.stackTraceToString()}"
                )
            }
        }

    }
}