package com.github.max_larsen_swe.android.theincredibleflick.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

data class PhotoList(
    val photos: List<Photo> = listOf(),
    override val type: DCType = DCType.PhotoList
) : DC(type) {
    companion object {
        val TAG: String = this::class.java.simpleName
        fun fromJSONResponseString(responseString: String): PhotoList {
            val ret: MutableList<Photo> = mutableListOf()
            val cleanedResponse: String =
                responseString.drop("jsonFlickrApi(".length).dropLast(1)
            if (cleanedResponse.isEmpty())
                return PhotoList()

            try {
                val responseObject: JSONObject =
                    JSONTokener(cleanedResponse).nextValue() as JSONObject
                val photoList: JSONArray = responseObject
                    .getJSONObject("photos")
                    .getJSONArray("photo")

                for (i in 0 until photoList.length()) {
                    try {
                        ret.add(Photo.singleFromJSONObject(photoList.getJSONObject(i)))
                    } catch (e: JSONException) {
                        Log.e(
                            TAG,
                            "Failed to parse record ${photoList.getJSONObject(i)} ${e.stackTraceToString()}"
                        )
                    }
                }
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
            return PhotoList(photos = ret)
        }
    }
}