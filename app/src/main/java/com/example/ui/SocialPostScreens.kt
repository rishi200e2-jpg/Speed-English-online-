package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.SocialPost
import com.example.data.AdminPostFilterState
import com.google.firebase.storage.FirebaseStorage
import java.util.Locale
import java.util.UUID

/**
 * Compact Number Formatter for Views, Likes, and Shares:
 * 1 -> 1
 * 10 -> 10
 * 100 -> 100
 * 900 -> 900
 * 1,000 -> 1K
 * 12,500 -> 12.5K
 * 100,000 -> 100K
 * 1,000,000 -> 1M
 * 2,500,000 -> 2.5M
 * 100,000,000 -> 100M
 * 1,000,000,000 -> 1B
 */
fun formatCompactNumber(count: Long): String {
    if (count <= 0L) return "0"
    if (count < 1000L) return count.toString()

    val (divisor, suffix) = when {
        count >= 1_000_000_000L -> 1_000_000_000.0 to "B"
        count >= 1_000_000L -> 1_000_000.0 to "M"
        else -> 1000.0 to "K"
    }

    val value = count.toDouble() / divisor
    val formatted = if (value >= 100.0 || (count % (divisor.toLong() / 10L) == 0L && count % divisor.toLong() == 0L)) {
        String.format(Locale.US, "%.0f", value)
    } else {
        val s = String.format(Locale.US, "%.1f", value)
        if (s.endsWith(".0")) s.substringBefore(".0") else s
    }
    return "$formatted$suffix"
}

/**
 * ==========================================
 * USER SOCIAL POST FEED SCREEN
 * ==========================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostTabContent(viewModel: QuizViewModel) {
    val context = LocalContext.current
    val posts by viewModel.posts.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val postsSyncError by viewModel.postsSyncError.collectAsState()
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var fullMediaPost by remember { mutableStateOf<SocialPost?>(null) }

    LaunchedEffect(Unit) {
        viewModel.startObservingPosts()
    }

    // Displays all exact posts from Cloud Firestore, ensuring both Admin and every User see the exact same posts
    val visiblePosts = remember(posts, searchQuery) {
        if (searchQuery.isBlank()) {
            posts
        } else {
            posts.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Post",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            actions = {
                IconButton(
                    onClick = { viewModel.refreshPosts() },
                    modifier = Modifier.testTag("post_refresh_icon")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Posts"
                    )
                }
                IconButton(
                    onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    },
                    modifier = Modifier.testTag("post_search_icon")
                ) {
                    Icon(
                        imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search Posts"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            windowInsets = WindowInsets(0, 0, 0, 0)
        )
            if (postsSyncError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val errorDisplay = if (postsSyncError?.contains("PERMISSION_DENIED", ignoreCase = true) == true) {
                            "Firebase Rules Notice: Set 'allow read: if true;' for social_posts in Firebase Console Rules."
                        } else {
                            "Sync status: $postsSyncError"
                        }
                        Text(
                            text = errorDisplay,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = { viewModel.refreshPosts() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search updates, tips and vocab challenge...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("post_search_field")
                )
            }

            PullToRefreshLayout(
                isRefreshing = isSyncing,
                onRefresh = {
                    viewModel.refreshAllAppData()
                },
                modifier = Modifier.fillMaxSize()
            ) {
                if (visiblePosts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DynamicFeed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                modifier = Modifier.size(54.dp)
                            )
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No matching posts found" else "No posts yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (searchQuery.isNotEmpty()) "Try a different search keyword." else "New challenges and grammar tips will appear here soon!",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
                    ) {
                        visiblePosts.forEachIndexed { index, post ->
                            item(key = post.documentId) {
                                SocialPostCard(
                                    post = post,
                                    viewModel = viewModel,
                                    onMediaClick = { fullMediaPost = post }
                                )
                            }
                            // Insert exactly 1 Native Ad after every 4 actual posts
                            if ((index + 1) % 4 == 0) {
                                val adSlotIndex = (index + 1) / 4
                                item(key = "native_ad_feed_slot_$adSlotIndex") {
                                    com.example.ads.NativeAdContainer(
                                        placement = "post_feed_ad_$adSlotIndex",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = 600.dp)
                                    )
                                }
                            }
                            if (index == visiblePosts.size - 1) {
                                item(key = "pagination_trigger") {
                                    LaunchedEffect(index) {
                                        viewModel.loadNextPostsPage()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    if (fullMediaPost != null) {
        SocialMediaPreviewDialog(
            post = fullMediaPost!!,
            onDismiss = { fullMediaPost = null }
        )
    }
}

/**
 * High-Fidelity Social Post Card:
 * - 18sp Bold Title
 * - 14sp Regular Description
 * - 12-13sp Medium Stats with compact number formatting (1K, 12.5K, 100K, 1M, 2.5M, etc.)
 * - Instant Like toggle (+1 / -1) with duplicate prevention
 * - Native Video streaming & player controls
 * - Cached image loading via Coil
 * - Share intent with atomic +1 share counter
 */
@Composable
fun SocialPostCard(
    post: SocialPost,
    viewModel: QuizViewModel,
    onMediaClick: () -> Unit
) {
    val context = LocalContext.current
    val currentUserId = viewModel.getCurrentUserId()
    val isLiked = post.likedUserIds.contains(currentUserId) || viewModel.isPostLikedByUser(post.documentId, currentUserId)

    // Trigger automatic view registration when this post enters screen
    LaunchedEffect(post.documentId) {
        viewModel.recordPostView(post.documentId)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("post_card_${post.documentId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 4.dp)
        ) {
            // Header Row: Avatar, Author, Relative Timestamp, Pinned Badge, 3-dots Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Speed English Circular Avatar
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_speed_english_logo),
                            contentDescription = "Speed English Logo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = post.authorName.ifBlank { "Speed English" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${formatRelativeTime(post.createdAt)} • ",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "Public",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (post.isPinned) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEDE9FE),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Pinned",
                                    tint = Color(0xFF6D28D9),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Pinned",
                                    color = Color(0xFF6D28D9),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share Post") },
                                onClick = {
                                    showMenu = false
                                    sharePostAction(context, post, viewModel)
                                },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1. Post Title: 18sp Bold
            Text(
                text = post.title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Post Description: 14sp Regular
            Text(
                text = post.description,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            )

            // Media Box (Image or Video)
            val effectiveMediaUrl = post.mediaUrl.ifBlank { post.feedMediaUrl.ifBlank { post.originalMediaUrl } }
            if (effectiveMediaUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                if (post.mediaType.equals("video", ignoreCase = true)) {
                    PostVideoCardPlayer(
                        videoUrl = effectiveMediaUrl,
                        videoDuration = post.videoDuration,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { onMediaClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        val context = LocalContext.current
                        val imageModel: Any = remember(effectiveMediaUrl) {
                            if (effectiveMediaUrl.startsWith("data:image")) {
                                try {
                                    val base64Data = effectiveMediaUrl.substringAfter("base64,")
                                    android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                                } catch (e: Exception) {
                                    effectiveMediaUrl
                                }
                            } else {
                                effectiveMediaUrl
                            }
                        }

                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageModel)
                                .crossfade(true)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = post.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stats Row: Views, Likes, Shares with Compact Number Formatting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Views
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Views",
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${formatCompactNumber(post.viewCount)} Views",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF6B7280)
                    )
                }

                // Likes
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Likes",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${formatCompactNumber(post.likeCount)} Likes",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF6B7280)
                    )
                }

                // Shares
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Reply,
                        contentDescription = "Shares",
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${formatCompactNumber(post.shareCount)} Shares",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF6B7280)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.8.dp
            )

            // Action Buttons Row: Like (+1/-1 toggle) and Share (with Android Share Sheet)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like Button: First tap = Like & +1 count, second tap = Unlike & -1 count
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { viewModel.togglePostLike(post.documentId) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color(0xFFEF4444) else Color(0xFF4B5563),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isLiked) "Liked" else "Like",
                        color = if (isLiked) Color(0xFFEF4444) else Color(0xFF4B5563),
                        fontWeight = if (isLiked) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.5.sp
                    )
                }

                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.height(20.dp),
                    thickness = 0.8.dp
                )

                // Share Button: Opens standard Android Share Intent and increments share count
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { sharePostAction(context, post, viewModel) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color(0xFF4B5563),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Share",
                        color = Color(0xFF4B5563),
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp
                    )
                }
            }
        }
    }
}

/**
 * Inline Video Player inside Post card:
 * Streams video directly from mediaUrl with caching, play/pause controls,
 * buffering indicator, and error recovery.
 */
@Composable
fun PostVideoCardPlayer(
    videoUrl: String,
    videoDuration: String,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var videoViewRef by remember { mutableStateOf<android.widget.VideoView?>(null) }

    Box(
        modifier = modifier
            .height(210.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (!hasError && videoUrl.isNotBlank()) {
            AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        val uri = Uri.parse(videoUrl)
                        setVideoURI(uri)
                        setOnPreparedListener { mp ->
                            isBuffering = false
                            mp.isLooping = true
                        }
                        setOnInfoListener { _, what, _ ->
                            if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                                isBuffering = true
                            } else if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                                isBuffering = false
                            }
                            false
                        }
                        setOnErrorListener { _, _, _ ->
                            hasError = true
                            isBuffering = false
                            isPlaying = false
                            true
                        }
                        videoViewRef = this
                    }
                },
                update = { view ->
                    videoViewRef = view
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Overlay controls for Play/Pause, Buffering, and Errors
        if (!isPlaying || isBuffering || hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (!isPlaying) Color.Black.copy(alpha = 0.35f) else Color.Transparent)
                    .clickable {
                        if (!hasError) {
                            videoViewRef?.let { vv ->
                                if (isPlaying) {
                                    vv.pause()
                                    isPlaying = false
                                } else {
                                    isBuffering = true
                                    vv.start()
                                    isPlaying = true
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isBuffering) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                } else if (hasError) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Playback error", color = Color.White, fontSize = 12.sp)
                    }
                } else if (!isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Video",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        } else {
            // While playing, tapping pauses playback
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        videoViewRef?.pause()
                        isPlaying = false
                    }
            )
        }

        // Bottom right duration badge (e.g. ▶ 01:24)
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.75f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = videoDuration.ifBlank { "01:24" },
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Standard Android Share Action:
 * Opens Android system Share Sheet and immediately increments Share count.
 */
private fun sharePostAction(context: Context, post: SocialPost, viewModel: QuizViewModel) {
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TEXT,
                "${post.title}\n\n${post.description}\n\nMaster English with daily vocabulary drills on Speed English!"
            )
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Post")
        context.startActivity(shareIntent)
        viewModel.recordPostShare(post.documentId)
    } catch (e: Exception) {
        android.util.Log.e("SocialPost", "Failed sharing post: ${e.message}")
    }
}

private fun formatRelativeTime(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hours ago"
        days == 1L -> "Yesterday"
        days < 7 -> "$days days ago"
        else -> "${days / 7} weeks ago"
    }
}

@Composable
fun SocialMediaPreviewDialog(
    post: SocialPost,
    onDismiss: () -> Unit
) {
    val isVideo = post.mediaType.equals("video", ignoreCase = true)

    if (isVideo) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = post.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    PostVideoCardPlayer(
                        videoUrl = post.mediaUrl,
                        videoDuration = post.videoDuration,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = post.description,
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        // FULL-SCREEN IMAGE VIEWER WITH PINCH-TO-ZOOM & PAN
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Main image viewport with pinch-to-zoom and pan gestures
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    val extraWidth = (scale - 1f) * size.width / 2f
                                    val extraHeight = (scale - 1f) * size.height / 2f
                                    val newX = (offset.x + pan.x).coerceIn(-extraWidth, extraWidth)
                                    val newY = (offset.y + pan.y).coerceIn(-extraHeight, extraHeight)
                                    offset = Offset(newX, newY)
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val context = LocalContext.current
                    val fullMediaUrl = post.mediaUrl.ifBlank { post.feedMediaUrl.ifBlank { post.originalMediaUrl } }
                    val fullImageModel: Any = remember(fullMediaUrl) {
                        if (fullMediaUrl.startsWith("data:image")) {
                            try {
                                val base64Data = fullMediaUrl.substringAfter("base64,")
                                android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                            } catch (e: Exception) {
                                fullMediaUrl
                            }
                        } else {
                            fullMediaUrl
                        }
                    }

                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(fullImageModel)
                            .crossfade(true)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = post.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                }

                // Top Control Bar: Title & Close Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = post.title.ifBlank { "Quiz Report Scorecard" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (post.authorName.isNotBlank()) {
                            Text(
                                text = "Posted by ${post.authorName}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Viewer",
                            tint = Color.White
                        )
                    }
                }

                // Tap to Reset Zoom Pill
                if (scale > 1.05f) {
                    Surface(
                        onClick = {
                            scale = 1f
                            offset = Offset.Zero
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 24.dp)
                    ) {
                        Text(
                            text = "Reset Zoom (${String.format("%.1f", scale)}x)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * ==========================================
 * ADMIN POST MANAGEMENT CONTENT & DIALOGS
 * ==========================================
 */
@Composable
fun AdminPostManagementContent(viewModel: QuizViewModel) {
    val adminPosts by viewModel.adminPosts.collectAsState()
    val isFetching by viewModel.isAdminFetchingPosts.collectAsState()
    val currentFilter by viewModel.adminPostsFilter.collectAsState()
    val hasMore by viewModel.adminHasMorePosts.collectAsState()
    val selectedIds by viewModel.adminSelectedPostIds.collectAsState()
    val bulkProgress by viewModel.bulkDeleteProgress.collectAsState()

    var showCreateEditDialog by remember { mutableStateOf<SocialPost?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var previewPost by remember { mutableStateOf<SocialPost?>(null) }
    var deleteConfirmPost by remember { mutableStateOf<SocialPost?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showDeleteAllFilteredConfirm by remember { mutableStateOf(false) }
    var deleteAllTypedConfirm by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showAdvancedFilters by remember { mutableStateOf(false) }

    // Filter local form state
    var searchQueryInput by remember(currentFilter.searchQuery) { mutableStateOf(currentFilter.searchQuery) }
    var minLikesInput by remember(currentFilter.minLikes) { mutableStateOf(currentFilter.minLikes?.toString() ?: "") }
    var maxLikesInput by remember(currentFilter.maxLikes) { mutableStateOf(currentFilter.maxLikes?.toString() ?: "") }
    var minViewsInput by remember(currentFilter.minViews) { mutableStateOf(currentFilter.minViews?.toString() ?: "") }
    var maxViewsInput by remember(currentFilter.maxViews) { mutableStateOf(currentFilter.maxViews?.toString() ?: "") }
    var minSharesInput by remember(currentFilter.minShares) { mutableStateOf(currentFilter.minShares?.toString() ?: "") }
    var maxSharesInput by remember(currentFilter.maxShares) { mutableStateOf(currentFilter.maxShares?.toString() ?: "") }

    LaunchedEffect(Unit) {
        viewModel.fetchAdminPosts(reset = true)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header with Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Admin Post Management (${adminPosts.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Filter by User ID, Name, ID, or Date • Bulk Delete • Edit Engagement Counters",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    isCreatingNew = true
                    showCreateEditDialog = SocialPost(
                        documentId = "",
                        title = "",
                        description = "",
                        mediaUrl = "https://images.unsplash.com/photo-1456513080510-7bf3a84b82f8?w=800&auto=format&fit=crop&q=80",
                        mediaType = "image",
                        isPinned = false,
                        isPublished = true
                    )
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Post", fontSize = 13.sp)
            }
        }

        // Bulk Action Bar if items selected or filtered delete
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedIds.isNotEmpty()) {
                Button(
                    onClick = { showBulkDeleteConfirm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete Selected (${selectedIds.size})", fontSize = 12.5.sp)
                }
            }

            OutlinedButton(
                onClick = {
                    deleteAllTypedConfirm = ""
                    showDeleteAllFilteredConfirm = true
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete All Matched", fontSize = 12.5.sp)
            }
        }

        // Status Banner
        if (statusMessage != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = statusMessage ?: "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        // Search & Filter Panel Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQueryInput,
                    onValueChange = { searchQueryInput = it },
                    placeholder = { Text("Search User ID, Author Name, Post ID, or Keywords...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQueryInput.isNotEmpty()) {
                            IconButton(onClick = { searchQueryInput = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                // Filter Chips Row: Date Range
                Text("Date Range Filter:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL" to "All Time", "TODAY" to "Today", "7_DAYS" to "7 Days", "30_DAYS" to "30 Days").forEach { (code, label) ->
                        FilterChip(
                            selected = currentFilter.dateRange == code,
                            onClick = { viewModel.setAdminPostsFilter(currentFilter.copy(dateRange = code)) },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                // Filter Chips Row: Sort & Image
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sort Order:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = currentFilter.sortOrder == "NEWEST",
                                onClick = { viewModel.setAdminPostsFilter(currentFilter.copy(sortOrder = "NEWEST")) },
                                label = { Text("Newest", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = currentFilter.sortOrder == "OLDEST",
                                onClick = { viewModel.setAdminPostsFilter(currentFilter.copy(sortOrder = "OLDEST")) },
                                label = { Text("Oldest", fontSize = 11.sp) }
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Image Filter:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = currentFilter.imageFilter == "ALL",
                                onClick = { viewModel.setAdminPostsFilter(currentFilter.copy(imageFilter = "ALL")) },
                                label = { Text("All", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = currentFilter.imageFilter == "HAS_IMAGE",
                                onClick = { viewModel.setAdminPostsFilter(currentFilter.copy(imageFilter = "HAS_IMAGE")) },
                                label = { Text("Image", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                TextButton(
                    onClick = { showAdvancedFilters = !showAdvancedFilters },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(
                        imageVector = if (showAdvancedFilters) Icons.Default.ExpandLess else Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showAdvancedFilters) "Hide Engagement Ranges" else "Filter by Likes / Views / Shares Range", fontSize = 12.sp)
                }

                if (showAdvancedFilters) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Engagement Count Ranges (Min / Max):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = minLikesInput,
                                onValueChange = { minLikesInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Min Likes", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            OutlinedTextField(
                                value = maxLikesInput,
                                onValueChange = { maxLikesInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Max Likes", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = minViewsInput,
                                onValueChange = { minViewsInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Min Views", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            OutlinedTextField(
                                value = maxViewsInput,
                                onValueChange = { maxViewsInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Max Views", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = minSharesInput,
                                onValueChange = { minSharesInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Min Shares", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            OutlinedTextField(
                                value = maxSharesInput,
                                onValueChange = { maxSharesInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Max Shares", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                // Filter Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            searchQueryInput = ""
                            minLikesInput = ""
                            maxLikesInput = ""
                            minViewsInput = ""
                            maxViewsInput = ""
                            minSharesInput = ""
                            maxSharesInput = ""
                            viewModel.setAdminPostsFilter(AdminPostFilterState())
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Reset Filters", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            viewModel.setAdminPostsFilter(
                                currentFilter.copy(
                                    searchQuery = searchQueryInput,
                                    minLikes = minLikesInput.toLongOrNull(),
                                    maxLikes = maxLikesInput.toLongOrNull(),
                                    minViews = minViewsInput.toLongOrNull(),
                                    maxViews = maxViewsInput.toLongOrNull(),
                                    minShares = minSharesInput.toLongOrNull(),
                                    maxShares = maxSharesInput.toLongOrNull()
                                )
                            )
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Apply Search & Filters", fontSize = 12.sp)
                    }
                }
            }
        }

        // Multi-Select Bar
        if (adminPosts.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val allSelected = selectedIds.size == adminPosts.size && adminPosts.isNotEmpty()
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = {
                            if (allSelected) viewModel.clearSelectedAdminPosts() else viewModel.selectAllAdminPosts()
                        }
                    )
                    Text("Select All (${adminPosts.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                if (selectedIds.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearSelectedAdminPosts() }) {
                        Text("Clear Selection (${selectedIds.size})", fontSize = 12.sp)
                    }
                }
            }
        }

        // Post Items List
        if (isFetching && adminPosts.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else if (adminPosts.isEmpty()) {
            Text(
                text = "No posts match current search/filters.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            adminPosts.forEach { post ->
                val isSelected = selectedIds.contains(post.documentId)
                AdminPostItemCard(
                    post = post,
                    isSelected = isSelected,
                    onToggleSelect = { viewModel.toggleSelectAdminPost(post.documentId) },
                    onPreview = { previewPost = post },
                    onEdit = {
                        isCreatingNew = false
                        showCreateEditDialog = post
                    },
                    onDelete = { deleteConfirmPost = post },
                    onTogglePin = { viewModel.toggleSocialPostPinned(post.documentId, !post.isPinned) },
                    onTogglePublish = { viewModel.toggleSocialPostPublished(post.documentId, !post.isPublished) },
                    onUpdateCounters = { v, l, s ->
                        viewModel.updateSocialPostCounters(post.documentId, v, l, s) { success, err ->
                            statusMessage = if (success) "Counters updated for '${post.title}'!" else "Error: $err"
                        }
                    }
                )
            }

            if (hasMore) {
                Button(
                    onClick = { viewModel.fetchAdminPosts(reset = false) },
                    enabled = !isFetching,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Load Next Batch", fontSize = 13.sp)
                }
            }
        }
    }

    // Full Post Detail Preview Dialog
    if (previewPost != null) {
        val post = previewPost!!
        AlertDialog(
            onDismissRequest = { previewPost = null },
            title = { Text(post.title.ifBlank { "Post Details" }, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Post ID: ${post.documentId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text("User ID: ${post.userId.ifBlank { "Unknown" }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    Text("Author: ${post.authorName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(post.createdAt))}", fontSize = 11.sp)
                    HorizontalDivider()
                    Text("Description:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(post.description.ifBlank { "No description provided." }, fontSize = 12.sp)
                    if (post.mediaUrl.isNotBlank()) {
                        Text("Media URL: ${post.mediaUrl}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    HorizontalDivider()
                    Text("Engagement Counters: Views: ${post.viewCount} | Likes: ${post.likeCount} | Shares: ${post.shareCount}", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                Button(onClick = { previewPost = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Create / Edit Dialog
    if (showCreateEditDialog != null) {
        AdminCreateEditPostDialog(
            initialPost = showCreateEditDialog!!,
            isNew = isCreatingNew,
            onDismiss = { showCreateEditDialog = null },
            onSave = { savedPost ->
                viewModel.saveSocialPost(savedPost) { success, err ->
                    showCreateEditDialog = null
                    statusMessage = if (success) "Post '${savedPost.title}' saved successfully!" else "Error: $err"
                }
            }
        )
    }

    // Single Delete Dialog
    if (deleteConfirmPost != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmPost = null },
            title = { Text("Delete Post", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete '${deleteConfirmPost?.title}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val id = deleteConfirmPost!!.documentId
                        deleteConfirmPost = null
                        viewModel.deleteSocialPost(id) { success, _ ->
                            statusMessage = if (success) "Post deleted." else "Failed to delete post."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteConfirmPost = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Bulk Delete Selected Confirmation Dialog
    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Delete Selected Posts", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete the ${selectedIds.size} selected post(s)? This process executes in safe background batches.") },
            confirmButton = {
                Button(
                    onClick = {
                        showBulkDeleteConfirm = false
                        viewModel.deleteSelectedAdminPosts { success, msg ->
                            statusMessage = msg
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Bulk Delete (${selectedIds.size})")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBulkDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Bulk Delete All Filtered Confirmation Dialog
    if (showDeleteAllFilteredConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllFilteredConfirm = false },
            title = { Text("Delete ALL Filtered Posts", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("WARNING: You are about to permanently delete ALL posts matching your current search/filters.")
                    Text("Type 'DELETE' in all caps below to confirm bulk operation:")
                    OutlinedTextField(
                        value = deleteAllTypedConfirm,
                        onValueChange = { deleteAllTypedConfirm = it },
                        placeholder = { Text("DELETE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllFilteredConfirm = false
                        viewModel.deleteAllFilteredAdminPosts { success, msg ->
                            statusMessage = msg
                        }
                    },
                    enabled = deleteAllTypedConfirm == "DELETE",
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All Matched")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteAllFilteredConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Progress Dialog during Bulk Deletion
    if (bulkProgress != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Bulk Delete in Progress", fontWeight = FontWeight.Bold) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Text(bulkProgress ?: "Processing...", fontSize = 13.sp)
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun AdminPostItemCard(
    post: SocialPost,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onPreview: () -> Unit = {},
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onTogglePublish: () -> Unit,
    onUpdateCounters: (Long, Long, Long) -> Unit
) {
    var editViews by remember(post.viewCount) { mutableStateOf(post.viewCount.toString()) }
    var editLikes by remember(post.likeCount) { mutableStateOf(post.likeCount.toString()) }
    var editShares by remember(post.shareCount) { mutableStateOf(post.shareCount.toString()) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    if (post.isPinned) {
                        Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = Color(0xFF7C3AED), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = post.title.ifBlank { "Untitled Post" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onPreview() }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Published Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (post.isPublished) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                        modifier = Modifier.clickable { onTogglePublish() }
                    ) {
                        Text(
                            text = if (post.isPublished) "Published" else "Draft",
                            color = if (post.isPublished) Color(0xFF15803D) else Color(0xFFB91C1C),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Preview Button
                    IconButton(onClick = onPreview, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Visibility, contentDescription = "Preview", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    }

                    // Pin toggle button
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (post.isPinned) Icons.Default.PushPin else Icons.Default.BookmarkBorder,
                            contentDescription = "Pin/Unpin",
                            tint = if (post.isPinned) Color(0xFF7C3AED) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Edit button
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }

                    // Delete button
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Subtitle info line with Author and User ID
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Author: ${post.authorName}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                if (post.userId.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "UID: ${post.userId.take(12)}...",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = post.description,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onPreview() }
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            // Real-Time Counters Customizer (Admin requirement: Modify View, Like, Share counts)
            Text(
                text = "Modify Live Counters (Full integer saved to Firestore):",
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = editViews,
                    onValueChange = { editViews = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Views", fontSize = 10.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = editLikes,
                    onValueChange = { editLikes = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Likes", fontSize = 10.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = editShares,
                    onValueChange = { editShares = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Shares", fontSize = 10.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                )

                Button(
                    onClick = {
                        val v = editViews.toLongOrNull() ?: post.viewCount
                        val l = editLikes.toLongOrNull() ?: post.likeCount
                        val s = editShares.toLongOrNull() ?: post.shareCount
                        onUpdateCounters(v, l, s)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("Update", fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCreateEditPostDialog(
    initialPost: SocialPost,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (SocialPost) -> Unit
) {
    var title by remember { mutableStateOf(initialPost.title) }
    var description by remember { mutableStateOf(initialPost.description) }
    var mediaUrl by remember { mutableStateOf(initialPost.mediaUrl) }
    var mediaType by remember { mutableStateOf(initialPost.mediaType) }
    var videoDuration by remember { mutableStateOf(initialPost.videoDuration) }
    var isPinned by remember { mutableStateOf(initialPost.isPinned) }
    var isPublished by remember { mutableStateOf(initialPost.isPublished) }
    var initialViews by remember { mutableStateOf(initialPost.viewCount.toString()) }
    var initialLikes by remember { mutableStateOf(initialPost.likeCount.toString()) }
    var initialShares by remember { mutableStateOf(initialPost.shareCount.toString()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isUploadingMedia by remember { mutableStateOf(false) }
    var uploadSuccessMsg by remember { mutableStateOf<String?>(null) }

    // Launcher for uploading Photo from Phone
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingMedia = true
            errorMsg = null
            uploadSuccessMsg = null
            mediaType = "image"
            val storage = FirebaseStorage.getInstance()
            val ref = storage.reference.child("posts/${UUID.randomUUID()}_${System.currentTimeMillis()}.jpg")
            ref.putFile(uri)
                .addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { downloadUri ->
                        mediaUrl = downloadUri.toString()
                        isUploadingMedia = false
                        uploadSuccessMsg = "✓ Photo successfully uploaded to Firebase Storage!"
                    }.addOnFailureListener { e ->
                        isUploadingMedia = false
                        errorMsg = "Failed to get download URL: ${e.message}"
                    }
                }
                .addOnFailureListener { e ->
                    isUploadingMedia = false
                    errorMsg = "Storage upload error: ${e.message}"
                }
        }
    }

    // Launcher for uploading Video from Phone
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingMedia = true
            errorMsg = null
            uploadSuccessMsg = null
            mediaType = "video"
            val storage = FirebaseStorage.getInstance()
            val ref = storage.reference.child("posts/${UUID.randomUUID()}_${System.currentTimeMillis()}.mp4")
            ref.putFile(uri)
                .addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { downloadUri ->
                        mediaUrl = downloadUri.toString()
                        isUploadingMedia = false
                        uploadSuccessMsg = "✓ Video successfully uploaded to Firebase Storage!"
                    }.addOnFailureListener { e ->
                        isUploadingMedia = false
                        errorMsg = "Failed to get download URL: ${e.message}"
                    }
                }
                .addOnFailureListener { e ->
                    isUploadingMedia = false
                    errorMsg = "Storage upload error: ${e.message}"
                }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (isNew) "Create New Post" else "Edit Post",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                if (errorMsg != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMsg ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (uploadSuccessMsg != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDCFCE7),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uploadSuccessMsg ?: "",
                            color = Color(0xFF15803D),
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Post Title") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Post Description") },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Media Type Selector: Image vs Video
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mediaType.equals("image", ignoreCase = true),
                        onClick = { mediaType = "image" },
                        label = { Text("Image Post") },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = mediaType.equals("video", ignoreCase = true),
                        onClick = { mediaType = "video" },
                        label = { Text("Video Post") },
                        leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Phone Upload Buttons Section
                Text(
                    text = "Upload Media from Phone:",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isUploadingMedia,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Photo", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { videoPickerLauncher.launch("video/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isUploadingMedia,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6D28D9))
                    ) {
                        Icon(Icons.Default.VideoCameraBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Video", fontSize = 12.sp)
                    }
                }

                if (isUploadingMedia) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Uploading file to Firebase Storage...", fontSize = 12.sp)
                        }
                    }
                }

                // Media Preview if mediaUrl is present
                if (mediaUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (mediaType.equals("video", ignoreCase = true)) {
                            PostVideoCardPlayer(
                                videoUrl = mediaUrl,
                                videoDuration = videoDuration,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(mediaUrl)
                                    .crossfade(true)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .build(),
                                contentDescription = "Media Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Media URL field
                OutlinedTextField(
                    value = mediaUrl,
                    onValueChange = { mediaUrl = it },
                    label = { Text("Media Download URL") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (mediaType.equals("video", ignoreCase = true)) {
                    OutlinedTextField(
                        value = videoDuration,
                        onValueChange = { videoDuration = it },
                        label = { Text("Video Duration (e.g. 01:24)") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pin to Top (📌 Pinned)", fontSize = 13.5.sp)
                    Switch(checked = isPinned, onCheckedChange = { isPinned = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Publish Post Immediately", fontSize = 13.5.sp)
                    Switch(checked = isPublished, onCheckedChange = { isPublished = it })
                }

                if (isNew) {
                    Text("Initial Counters (Full numeric value in Firestore):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = initialViews,
                            onValueChange = { initialViews = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Views", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = initialLikes,
                            onValueChange = { initialLikes = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Likes", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = initialShares,
                            onValueChange = { initialShares = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Shares", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                errorMsg = "Please enter a post title."
                                return@Button
                            }
                            if (description.isBlank()) {
                                errorMsg = "Please enter a post description."
                                return@Button
                            }

                            val views = initialViews.toLongOrNull() ?: initialPost.viewCount
                            val likes = initialLikes.toLongOrNull() ?: initialPost.likeCount
                            val shares = initialShares.toLongOrNull() ?: initialPost.shareCount

                            val updated = initialPost.copy(
                                title = title.trim(),
                                description = description.trim(),
                                mediaUrl = mediaUrl.trim(),
                                mediaType = mediaType,
                                videoDuration = videoDuration.trim(),
                                isPinned = isPinned,
                                isPublished = isPublished,
                                viewCount = views,
                                likeCount = likes,
                                shareCount = shares
                            )
                            onSave(updated)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isNew) "POST" else "Save Changes")
                    }
                }
            }
        }
    }
}
