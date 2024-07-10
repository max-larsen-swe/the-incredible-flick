package com.github.max_larsen_swe.android.theincredibleflick.composables

import android.annotation.SuppressLint
import android.view.KeyEvent
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
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.github.max_larsen_swe.android.theincredibleflick.backend.API
import com.github.max_larsen_swe.android.theincredibleflick.backend.DownloadManager
import com.github.max_larsen_swe.android.theincredibleflick.data.DC
import com.github.max_larsen_swe.android.theincredibleflick.data.DCType
import com.github.max_larsen_swe.android.theincredibleflick.data.Photo
import com.github.max_larsen_swe.android.theincredibleflick.data.PhotoList
import com.github.max_larsen_swe.android.theincredibleflick.data.User
import com.github.max_larsen_swe.android.theincredibleflick.ui.theme.TheIncredibleFlickTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

object UI {
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @Composable
    fun App(state: SnapshotStateList<DC>) {
        var _state: SnapshotStateList<DC> = remember { state }
        LaunchedEffect(_state) {
            _state.add(API.textSearchForPhotoList("Yorkshire"))
        }
        val coroutineScope = rememberCoroutineScope()
        TheIncredibleFlickTheme {
            // A surface container using the 'background' color from the theme
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Style.Colors.BACKGROUND,
                contentColor = Style.Colors.TEXT
            ) {
                Scaffold(
                    topBar = {
                        var searchText = remember { mutableStateOf("SEARCH") }
                        TextField(
                            value = searchText.value,
                            onValueChange = { searchText.value = it },
                            label = { Text("Search") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .onKeyEvent {
                                    if (it.key in listOf(Key.Enter, Key.NumPadEnter)) {
                                        coroutineScope.launch {
                                            val result =
                                                API.textSearchForPhotoList(searchText.value)
                                            _state.add(result)
                                        }
                                    }
                                    true
                                }
                        )
                    },
                    content = {
                        when (_state.last().type) {
                            DCType.Photo -> PhotoDetails(_state, _state.last() as Photo)
                            DCType.User -> {
                                val user = (_state.last() as User)
                                val userPhotos =
                                    remember { mutableStateOf<List<Photo>>(emptyList()) }
                                LaunchedEffect(user) {
                                    userPhotos.value = API.searchForPhotosByUser(user).photos
                                }
                                PhotoPostList(
                                    _state,
                                    PhotoList(userPhotos.value),
                                    canRedirectToPhotoDetails = true,
                                    canRedirectToUserDetails = false
                                )
                            }

                            DCType.PhotoList -> PhotoPostList(
                                _state,
                                photosList = _state.last() as PhotoList,
                                canRedirectToPhotoDetails = true,
                                canRedirectToUserDetails = true
                            )

                            else -> {
                                Text("L O A D I N G . . .")
                            }
                        }
                    })
            }
        }
    }

    @Composable
    fun PhotoDetails(
        state: SnapshotStateList<DC>,
        photo: Photo,
        modifier: Modifier = Modifier
    ) {
        Column(Modifier.fillMaxWidth(1f)) {
            Text(photo.title)
            PhotoItem(state, photo, false, true)
            Text(photo.description)
        }
    }

    @Composable
    fun PhotoPostList(
        state: SnapshotStateList<DC>,
        photosList: PhotoList,
        canRedirectToPhotoDetails: Boolean = false,
        canRedirectToUserDetails: Boolean = false,
        modifier: Modifier = Modifier
    ) {
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
                    state,
                    photoData = photosList.photos[index],
                    canRedirectToPhotoDetails,
                    canRedirectToUserDetails
                )
            }
        }
    }

    @Composable
    fun PhotoItem(
        state: SnapshotStateList<DC>,
        photoData: Photo,
        canRedirectToPhotoDetails: Boolean = false,
        canRedirectToUserDetails: Boolean = false,
        modifier: Modifier = Modifier
    ) {
        var localPhotoUri: MutableState<String> = remember { mutableStateOf("") }
        var localIconUri: MutableState<String> = remember { mutableStateOf("") }
        var flickrUser: MutableState<User?> = remember { mutableStateOf(null) }
        LaunchedEffect(localPhotoUri, localIconUri, flickrUser) {
            localPhotoUri.value =
                DownloadManager.getCachedOrDownloadedLocalUri(photoData.remoteUri) ?: ""
            if (localPhotoUri.value.isNotEmpty()) {
                if (photoData.tags.isEmpty()) {
                    API.pullPhotoTags(photoData)
                }
                if (flickrUser.value == null) {
                    flickrUser.value = API.getUserData(photoData.owner)
                    localIconUri.value =
                        DownloadManager.getCachedOrDownloadedLocalUri(flickrUser.value!!.iconRemoteUri)
                            ?: ""
                }
            }
        }
        //visuals
        if (localPhotoUri.value.isNotEmpty()) {
            Column(
                modifier = modifier
                    .border(
                        Dp(1f), Style.Colors.BORDERS, RoundedCornerShape(CornerSize(Dp(5f)))
                    )
                    .padding(Dp(5f))
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = localPhotoUri.value),
                    contentDescription = "${photoData.title} ${photoData.description}",
                    modifier = modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(Dp(5f))
                        .border(
                            Dp(1f), Style.Colors.BORDERS, RoundedCornerShape(CornerSize(Dp(0f)))
                        )
                        .clickable {
                            if (canRedirectToPhotoDetails) {
                                state.add(photoData)
                            }
                        },
                    contentScale = ContentScale.Crop,
                )
                if (flickrUser.value != null) {
                    UserBlock(flickrUser.value!!, localIconUri.value, modifier.clickable {
                        if (canRedirectToUserDetails) {
                            state.add(flickrUser.value!!)
                        }
                    })
                }
                if (photoData.tags.isNotEmpty()) {
                    TagBlock(photoData.tags)
                }
            }
        }
    }

    @Composable
    fun UserBlock(user: User, localIconUri: String, modifier: Modifier = Modifier) {
        Row(
            modifier = modifier
                .padding(Dp(5f))
                .size(Dp.Infinity, Dp(30f))
        ) {
            if (localIconUri.isNotEmpty()) {
                Image(
                    painter = rememberAsyncImagePainter(model = localIconUri),
                    contentDescription = null,
                    modifier = Modifier.size(Dp(50f), Dp(50f)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.size(Dp(20f), Dp(50f)))
            } else {
                Spacer(Modifier.size(Dp(70f), Dp(50f)))
            }
            Text(
                user.username, Modifier.background(Color.Black, RoundedCornerShape(Dp(5f)))
            )
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun TagBlock(tags: List<String>) {
        FlowRow(
            modifier = Modifier.border(Dp(10f), Color.hsl(0f, 0f, 0f, 0f))
        ) {
            tags.forEach {
                Box(
                    Modifier
                        .padding(Dp(10f))
                        .background(Color.Black, RoundedCornerShape(Dp(5f)))
                ) {
                    Text(
                        text = it, color = Style.Colors.TAGS
                    )
                }
            }
        }
    }
}