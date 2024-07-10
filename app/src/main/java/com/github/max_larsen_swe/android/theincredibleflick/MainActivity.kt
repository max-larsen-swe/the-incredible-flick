package com.github.max_larsen_swe.android.theincredibleflick

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.lifecycleScope
import coil.compose.rememberAsyncImagePainter
import com.github.max_larsen_swe.android.theincredibleflick.ui.theme.TheIncredibleFlickTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Keys {
    const val API = "api_key"
    const val SECRET = "secret"
}

/*
Rationale: Common token ancestor to streamline driving view composition by highlighted data type
*/
object Data {
    enum class DCType {
        Undeclared, Photo, User, PhotoList
    }

    open class DC(open val type: DCType = DCType.Undeclared)
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

    data class User(
        val id: String,
        val username: String,
        val iconRemoteUri: String,
        var photos: List<Photo> = listOf(),//var allows lazy setting
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
}

object Backend {
    val TAG: String = Backend::class.java.simpleName

    object API {
        private const val endpoint = "https://www.flickr.com/"
        private const val responseFormat = "json"

        private object Methods {
            const val search = "flickr.photos.search"
            const val getUserInfo = "flickr.people.getInfo"
            const val getPhotoInfo = "flickr.photos.getInfo"
        }

        private val retrofit: Retrofit = Retrofit.Builder().baseUrl(endpoint).build()

        interface IFlickrApi {

            @GET("services/rest/")
            fun simpleTextSearch(
                @Query("text") text: String,
                @Query("method") method: String = Methods.search,
                @Query("safe_search") safe_search: Int = 1,
                @Query("api_key") api_key: String = Keys.API,
                @Query("format") format: String = responseFormat,
            ): Call<ResponseBody>

            @GET("services/rest/")
            fun getUserInfo(
                @Query("user_id") user_id: String,
                @Query("method") method: String = Methods.getUserInfo,
                @Query("api_key") api_key: String = Keys.API,
                @Query("format") format: String = responseFormat
            ): Call<ResponseBody>

            @GET("services/rest/")
            fun getPhotoInfo(
                @Query("photo_id") photo_id: String,
                @Query("secret") secret: String,
                @Query("method") method: String = Methods.getPhotoInfo,
                @Query("api_key") api_key: String = Keys.API,
                @Query("format") format: String = responseFormat
            ): Call<ResponseBody>
        }

        private val flickrRetrofit = retrofit.create(IFlickrApi::class.java)

        suspend fun textSearchForPhotoList(text: String): Data.PhotoList {
            return withContext(Dispatchers.IO) {
                val response = flickrRetrofit.simpleTextSearch(text)
                    .execute().body()?.string() ?: ""
                Data.PhotoList.fromJSONResponseString(response)
            }
        }

        suspend fun getUserData(userId: String): Data.User? {
            return withContext(Dispatchers.IO) {
                val response = flickrRetrofit.getUserInfo(userId)
                    .execute().body()?.string() ?: ""
                Data.User.fromJSONResponseString(response)
            }
        }

        suspend fun pullPhotoTags(photo: Data.Photo): Unit {
            return withContext(Dispatchers.IO) {
                val response = flickrRetrofit.getPhotoInfo(photo.id.toString(), photo.secret)
                    .execute().body()?.string() ?: ""
                Data.Photo.parsePulledTags(photo, response)
            }
        }
    }

    object DownloadManager {
        private val mutex: Mutex = Mutex()
        private val downloader: OkHttpClient = OkHttpClient()

        private fun buildCachedUri(context: Context, fileUrl: String): String {
            return Paths.get(context.cacheDir.path.toString(), fileUrl).toString()
        }

        private fun urlToDownloadPathString(url: String, suffix: String? = null): String {
            val prefix: String = "APKML_download__"
            return prefix + (if (suffix == null) hashUrl(url) else hashUrl(url) + suffix)
        }

        private fun hashUrl(url: String): String {
            val bytes = url.toByteArray()
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }

        private suspend fun runDownload(remoteUri: String, localUri: String): Boolean {
            val request = Request.Builder()
                .url(remoteUri)
                .addHeader(
                    "Authorization",
                    "Bearer ${Keys.API}"
                ).build()
            downloader.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to download file: $response")
                    return false
                }
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(localUri).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            return true
        }

        suspend fun getCachedOrDownloadedLocalUri(context: Context, remoteUri: String): String? {
            return withContext(Dispatchers.IO) {
                var localUri: String? = buildCachedUri(context, urlToDownloadPathString(remoteUri))
                mutex.withLock { //limit to only one download at a time
                    if (Files.notExists(Paths.get(localUri))) {
                        if (!runDownload(remoteUri, localUri!!)) {
                            Files.deleteIfExists(Paths.get(localUri))
                            localUri = null
                        }
                    }
                }
                return@withContext localUri
            }
        }
    }
}

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var state: Data.DC = Data.DC()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                state = Backend.API.textSearchForPhotoList("Yorkshire")
            }
        }
        setContent { UI.App(this, state) }
    }

}

object UI {
    object Style {
        object Colors {
            val BACKGROUND = Color.hsl(120f, 0.25f, 0.1f)
            val BORDERS = Color.hsl(170f, 0.6f, 0.2f)
            val TAGS = Color.hsl(170f, 0.6f, 0.4f)
            val TEXT = Color.hsl(170f, 0.2f, 0.85f)
        }
    }

    @Composable
    fun App(context: Context, state: Data.DC) {
        var _state: SnapshotStateList<Data.DC> = remember { mutableStateListOf(state) }
        TheIncredibleFlickTheme {
            // A surface container using the 'background' color from the theme
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                when(_state.last().type){
                    Data.DCType.Photo -> Text("Photo details view to be continued")
                    Data.DCType.User -> Text("User details view to be continued")
                    Data.DCType.PhotoList -> PhotoPostList(context = context , photosList = _state as Data.PhotoList)
                    else -> {Text("L O A D I N G . . .")}
                }
            }
        }
    }

    @Composable
    fun PhotoPostList(context: Context, photosList: Data.PhotoList, modifier: Modifier = Modifier) {
        if (photosList.photos.isEmpty()) {
            Text("L O A D I N G . . .")
        }
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            items(count = photosList.photos.size) { index ->
                PhotoItem(
                    context = context,
                    photoData = photosList.photos[index]
                )
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun PhotoItem(
        context: Context, photoData: Data.Photo, modifier: Modifier = Modifier
    ) {
        var localPhotoUri: String by remember { mutableStateOf("") }
        var localIconUri: String by remember { mutableStateOf("") }
        var flickrUser: Data.User? by remember { mutableStateOf(null) }
        LaunchedEffect(localPhotoUri, localIconUri, flickrUser) {
            localPhotoUri =
                Backend.DownloadManager.getCachedOrDownloadedLocalUri(context, photoData.remoteUri)
                    ?: ""
            if (localPhotoUri.isNotEmpty()) {
                if (photoData.tags.isEmpty()) {
                    Backend.API.pullPhotoTags(photoData)
                }
                if (flickrUser == null) {
                    flickrUser = Backend.API.getUserData(photoData.owner)
                    localIconUri = Backend.DownloadManager
                        .getCachedOrDownloadedLocalUri(context, flickrUser!!.iconRemoteUri) ?: ""
                }
            }
        }
        //visuals
        if (localPhotoUri.isNotEmpty()) {
            Column(
                modifier = modifier
                    .border(
                        Dp(1f),
                        Style.Colors.BORDERS,
                        RoundedCornerShape(CornerSize(Dp(5f)))
                    )
                    .padding(Dp(5f))
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = localPhotoUri),
                    contentDescription = "${photoData.title} ${photoData.description}",
                    modifier = modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(Dp(5f))
                        .border(
                            Dp(1f),
                            Style.Colors.BORDERS,
                            RoundedCornerShape(CornerSize(Dp(0f)))
                        )
                        .clickable {},
                    contentScale = ContentScale.Crop,
                )
                if (flickrUser != null) {
                    Row(
                        modifier = Modifier
                            .padding(Dp(5f))
                            .size(Dp.Infinity, Dp(30f))
                    ) {
                        if (localIconUri.isNotEmpty()) {
                            Image(
                                painter = rememberAsyncImagePainter(model = localIconUri),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(Dp(50f), Dp(50f)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.size(Dp(20f), Dp(50f)))
                        } else {
                            Spacer(Modifier.size(Dp(70f), Dp(50f)))
                        }
                        Text(
                            flickrUser!!.username,
                            Modifier.background(Color.Black, RoundedCornerShape(Dp(5f)))
                        )
                    }
                }
                if (photoData.tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .border(Dp(10f), Color.hsl(0f, 0f, 0f, 0f))
                    ) {
                        photoData.tags.forEach {
                            Box(
                                Modifier
                                    .padding(Dp(10f))
                                    .background(Color.Black, RoundedCornerShape(Dp(5f)))
                            ) {
                                Text(
                                    text = it,
                                    color = Style.Colors.TAGS
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
