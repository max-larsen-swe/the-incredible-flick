package com.github.max_larsen_swe.android.theincredibleflick.data

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

data class User(
    val id: String,
    val username: String,
    val iconRemoteUri: String,
    var photos: PhotoList = PhotoList(),//var allows lazy setting
    override val type: DCType = DCType.User
) :
    DC(type) {
    companion object {
        val TAG: String = this::class.java.simpleName
        fun fromJSONResponseString(responseString: String): User? {
            var ret: User? = null
            val cleanedResponse: String =
                responseString.drop("jsonFlickrApi(".length).dropLast(1)
            if (cleanedResponse.isEmpty())
                return ret
            try {
                val person: JSONObject =
                    (JSONTokener(cleanedResponse).nextValue() as JSONObject)
                        .getJSONObject("person")
                val username: String = person.getJSONObject("username")
                    .getString("_content")
                val iconFarm: Int = person.getInt("iconfarm")
                val iconServer: Int = person.getInt("iconserver")
                val nsid: String = person.getString("nsid")
                val iconRemoteUri: String =
                    "https://farm${iconFarm}.staticflickr.com/${iconServer}/buddyicons/${nsid}.jpg"
                ret = User(nsid, username, iconRemoteUri)
                person.toString()
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
            return ret
        }
    }
}