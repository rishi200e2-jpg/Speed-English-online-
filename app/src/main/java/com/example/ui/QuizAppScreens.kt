package com.example.ui

import com.example.R
import com.example.ads.RewardedInterstitialAdManager
import com.example.ads.findActivity
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.data.Category
import com.example.data.AdminUserFilterState
import com.example.data.Quiz
import com.example.data.Question
import com.example.data.QuestionAuditLog
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.QuizAttempt
import com.example.data.RegisteredUser
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring

// Color constants for high-vibrancy aesthetic
val PrimaryBlue = Color(0xFF2563EB)
val SecondaryAqua = Color(0xFF0D9488)
val AccentOrange = Color(0xFFEA580C)
val SurfaceBackground = Color(0xFFF8FAFC)
val DarkBackground = Color(0xFF0F172A)

// Map simple string representations to Material Icons
fun getCategoryIcon(iconName: String): ImageVector {
    return when (iconName.lowercase()) {
        "spotting" -> Icons.Default.Edit
        "vocabulary" -> Icons.Default.MenuBook
        "idioms" -> Icons.Default.Lightbulb
        "voice" -> Icons.Default.PlayArrow
        "general" -> Icons.Default.List
        "science" -> Icons.Default.Star
        else -> Icons.Default.Settings
    }
}

fun getCategoryColor(iconName: String): Color {
    return when (iconName.lowercase()) {
        "spotting" -> Color(0xFF6366F1)    // Indigo
        "vocabulary" -> Color(0xFFEC4899)  // Hot pink
        "idioms" -> Color(0xFFF59E0B)      // Golden Amber
        "voice" -> Color(0xFF06B6D4)       // Cyan Blue
        "general" -> Color(0xFF10B981)     // Emerald green
        "science" -> Color(0xFF8B5CF6)     // Soft Violet
        else -> Color(0xFF64748B)          // Cool grey
    }
}

fun getHashColor(seed: String): Color {
    val colors = listOf(
        Color(0xFF6366F1), // Indigo
        Color(0xFFEC4899), // Hot pink
        Color(0xFFF59E0B), // Golden Amber
        Color(0xFF06B6D4), // Cyan Blue
        Color(0xFF10B981), // Emerald green
        Color(0xFF8B5CF6), // Soft Violet
        Color(0xFFEF4444), // Red
        Color(0xFF3B82F6), // Blue
        Color(0xFF14B8A6), // Teal
        Color(0xFFF97316)  // Orange
    )
    val hash = Math.abs(seed.hashCode())
    return colors[hash % colors.size]
}

fun formatDecimal(value: Float): String {
    return if (value % 1.0f == 0.0f) {
        value.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", value).replace(Regex("\\.?0+$"), "")
    }
}

fun balanceColor(color: Color): Color {
    val hsv = FloatArray(3)
    val r = (color.red * 255).toInt().coerceIn(0, 255)
    val g = (color.green * 255).toInt().coerceIn(0, 255)
    val b = (color.blue * 255).toInt().coerceIn(0, 255)
    val a = (color.alpha * 255).toInt().coerceIn(0, 255)
    android.graphics.Color.RGBToHSV(r, g, b, hsv)
    
    val hue = hsv[0]
    var sat = hsv[1]
    var value = hsv[2]
    
    // Saturation clamp: make sure it is rich, colorful, and never faded/pastel (0.72 to 0.95)
    if (sat < 0.72f) {
        sat = 0.72f
    } else if (sat > 0.95f) {
        sat = 0.95f
    }
    
    // Value clamp:
    // Yellow, Lime, Green, and Cyan (Hue 30 to 195) look very bright and white-ish under high brightness.
    // We bring their brightness down slightly so they look deep, saturated, balanced, and readable.
    if (hue in 30f..195f) {
        if (value > 0.76f) {
            value = 0.76f // Beautiful balanced sunflower gold/amber/green
        } else if (value < 0.60f) {
            value = 0.60f
        }
    } else {
        // Red, Violet, Purple, Blue (Hue < 30 or Hue > 195)
        if (value > 0.88f) {
            value = 0.88f // Balanced rich indigo/violet/crimson
        } else if (value < 0.55f) {
            value = 0.55f
        }
    }
    
    val argb = android.graphics.Color.HSVToColor(a, floatArrayOf(hue, sat, value))
    return Color(argb)
}

fun extractVibrantColor(bitmap: android.graphics.Bitmap): Color {
    val width = bitmap.width
    val height = bitmap.height
    
    var bestColor = android.graphics.Color.GRAY
    var maxScore = -1f
    
    // Sample a 20x20 grid of pixels (400 pixels total) for high accuracy
    val stepX = (width / 20).coerceAtLeast(1)
    val stepY = (height / 20).coerceAtLeast(1)
    
    val hsv = FloatArray(3)
    for (x in 0 until width step stepX) {
        for (y in 0 until height step stepY) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = android.graphics.Color.alpha(pixel)
            if (alpha < 100) continue // Skip highly transparent pixels
            
            android.graphics.Color.colorToHSV(pixel, hsv)
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]
            
            // Check if the color is a genuine vibrant color, skipping pure darks/blacks and pure white/light-grays.
            // This ensures bright neon colors like primary yellow with value >= 0.95f are successfully chosen!
            if (sat > 0.12f && value > 0.12f && (sat > 0.18f || value < 0.98f)) {
                // Score based heavily on saturation (colorfulness) and brightness to find the dominant vibrant primary color
                val score = sat * 2.5f + value * 1.5f
                if (score > maxScore) {
                    maxScore = score
                    bestColor = pixel
                }
            }
        }
    }
    
    if (maxScore > 0f) {
        return balanceColor(Color(bestColor))
    }
    
    // Fallback: pick the center pixel or a fallback color
    try {
        val centerPixel = bitmap.getPixel(width / 2, height / 2)
        return balanceColor(Color(centerPixel))
    } catch (e: Exception) {
        return Color(0xFF6366F1) // Indigo fallback
    }
}

fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): android.graphics.Bitmap {
    if (drawable is android.graphics.drawable.BitmapDrawable) {
        if (drawable.bitmap != null) {
            return drawable.bitmap
        }
    }
    val bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
        android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
    } else {
        android.graphics.Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, android.graphics.Bitmap.Config.ARGB_8888)
    }
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

@Composable
fun rememberCategoryColor(category: Category, viewModel: QuizViewModel): Color {
    val iconName = category.iconName
    
    // Check if it matches a standard pre-defined icon color
    val standardColor = when (iconName.lowercase()) {
        "spotting" -> Color(0xFF6366F1)    // Indigo
        "vocabulary" -> Color(0xFFEC4899)  // Hot pink
        "idioms" -> Color(0xFFF59E0B)      // Golden Amber
        "voice" -> Color(0xFF06B6D4)       // Cyan Blue
        "general" -> Color(0xFF10B981)     // Emerald green
        "science" -> Color(0xFF8B5CF6)     // Soft Violet
        else -> null
    }
    if (standardColor != null) return standardColor

    // It's a custom category with uploaded image or special icon
    if (iconName.startsWith("http://") || iconName.startsWith("https://") || iconName.startsWith("data:") || iconName.startsWith("content://") || iconName.startsWith("file://")) {
        val customColors by viewModel.customCategoryColors.collectAsState()
        val cachedColorValue = customColors[iconName]
        if (cachedColorValue != null) {
            return balanceColor(Color(cachedColorValue))
        }

        val context = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(iconName) {
            try {
                val bitmap = if (iconName.startsWith("data:")) {
                    val commaIndex = iconName.indexOf(",")
                    val base64Part = if (commaIndex != -1) iconName.substring(commaIndex + 1) else iconName
                    val decodedBytes = android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT)
                    android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                } else {
                    val loader = coil.ImageLoader(context)
                    val request = coil.request.ImageRequest.Builder(context)
                        .data(iconName)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request)
                    if (result is coil.request.SuccessResult) {
                        drawableToBitmap(result.drawable)
                    } else {
                        null
                    }
                }
                
                if (bitmap != null) {
                    val extractedColor = extractVibrantColor(bitmap)
                    val r = (extractedColor.red * 255).toInt()
                    val g = (extractedColor.green * 255).toInt()
                    val b = (extractedColor.blue * 255).toInt()
                    val a = (extractedColor.alpha * 255).toInt()
                    val argbInt = android.graphics.Color.argb(a, r, g, b)
                    viewModel.updateCustomCategoryColor(iconName, argbInt)
                }
            } catch (e: Exception) {
                android.util.Log.e("rememberCategoryColor", "Error extracting vibrant color: ${e.message}")
            }
        }
        
        // Return a beautiful dynamic seed color based on the category name while loading
        return remember(category.name) { getHashColor(category.name) }
    }

    // Default hash color for custom/new categories
    return remember(category.name) { getHashColor(category.name) }
}

@Composable
fun CategoryIcon(iconName: String, tint: Color, modifier: Modifier = Modifier, contentDescription: String? = null) {
    if (iconName.startsWith("data:")) {
        val bitmap = remember(iconName) {
            try {
                val commaIndex = iconName.indexOf(",")
                val base64Part = if (commaIndex != -1) iconName.substring(commaIndex + 1) else iconName
                val decodedBytes = android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)?.let {
                    it.asImageBitmap()
                }
            } catch (e: Exception) {
                android.util.Log.e("CategoryIcon", "Failed to decode base64: ${e.message}")
                null
            }
        }
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = contentDescription ?: "Category Image",
                modifier = modifier.clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error loading image",
                tint = tint,
                modifier = modifier
            )
        }
    } else if (iconName.startsWith("http://") || iconName.startsWith("https://") || iconName.startsWith("content://") || iconName.startsWith("file://")) {
        coil.compose.AsyncImage(
            model = iconName,
            contentDescription = "Category Image",
            modifier = modifier
                .clip(CircleShape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        Icon(
            imageVector = getCategoryIcon(iconName),
            contentDescription = "Category Icon",
            tint = tint,
            modifier = modifier
        )
    }
}

// ==========================================
// NETWORK CONNECTIVITY STATUS BANNER
// ==========================================
@Composable
fun NetworkStatusBanner(
    isNetworkAvailable: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isDismissed: Boolean = false,
    onDismiss: (() -> Unit)? = null
) {
    var isRetrying by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = !isNetworkAvailable && !isDismissed,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("network_status_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Soft Indigo circular badge matching Speed English theme
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isRetrying) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .testTag("network_connecting_spinner"),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudOff,
                                        contentDescription = "Offline Mode",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isRetrying) "Reconnecting..." else "Offline Mode",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("network_status_text")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "Offline",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "No internet access. Real-time updates paused.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Retry button with smooth feedback
                        Surface(
                            onClick = {
                                if (!isRetrying) {
                                    isRetrying = true
                                    coroutineScope.launch {
                                        onRetry()
                                        kotlinx.coroutines.delay(800)
                                        isRetrying = false
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .testTag("network_retry_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry Connection",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Retry",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (onDismiss != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss Banner",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// CENTRALIZED QUIZ & FIRESTORE ERROR COMPONENT
// ==========================================
@Composable
fun QuizCentralizedErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Server Connection Failed",
    isRetrying: Boolean = false,
    onDismiss: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .padding(vertical = 8.dp)
            .testTag("quiz_centralized_error_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFEF2F2) // Soft danger red background
        ),
        border = BorderStroke(1.5.dp, Color(0xFFF87171))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            color = Color(0xFFFEE2E2),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "Server Connection Error",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF991B1B),
                        modifier = Modifier.testTag("quiz_error_title")
                    )
                    Text(
                        text = "Cloud Sync & Data Fetch Issue",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFDC2626)
                    )
                }

                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("quiz_error_dismiss_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss error",
                            tint = Color(0xFF991B1B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Exact error message container (plain descriptive text, never raw error code)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Error detail",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMessage,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF374151),
                        lineHeight = 18.sp,
                        modifier = Modifier.testTag("quiz_error_message")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onRetry,
                    enabled = !isRetrying,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("quiz_error_retry_button")
                ) {
                    if (isRetrying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retrying...", fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun MainQuizApp(
    viewModel: QuizViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val currentUserRole by viewModel.currentUserRole.collectAsState()
    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // Intercept physical system back button or gesture presses professionally!
    androidx.activity.compose.BackHandler(enabled = currentScreen !is Screen.Home && currentScreen !is Screen.Splash) {
        RewardedInterstitialAdManager.showBackNavigationAd(activity) {
            when (currentScreen) {
                is Screen.CategoryView -> {
                    viewModel.navigateBack()
                }
                is Screen.ActiveQuiz -> {
                    viewModel.navigateBack()
                }
                is Screen.Score -> {
                    viewModel.navigateTo(Screen.Home, clearBackstack = true)
                }
                is Screen.AdminDashboard -> {
                    viewModel.navigateTo(Screen.Home, clearBackstack = true)
                }
                else -> {
                    viewModel.navigateBack()
                }
            }
        }
    }

    var isBannerDismissed by remember(isNetworkAvailable) { mutableStateOf(false) }
    val showBanner = !isNetworkAvailable && !isBannerDismissed

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Real-time Network Connectivity Warning Banner
            NetworkStatusBanner(
                isNetworkAvailable = isNetworkAvailable,
                isDismissed = isBannerDismissed,
                onRetry = { viewModel.refreshNetworkStatus() },
                onDismiss = { isBannerDismissed = true }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (showBanner) {
                            Modifier.consumeWindowInsets(WindowInsets.statusBars)
                        } else {
                            Modifier
                        }
                    )
            ) {
                val isCurrentUserBlocked by viewModel.isCurrentUserBlocked.collectAsState()

                if (isCurrentUserBlocked) {
                    BlockedAccountScreen(viewModel = viewModel)
                } else if (currentUserRole == null && currentScreen !is Screen.Splash) {
                    // Anonymous Guest Profile: Interface access is locked strictly to the Login and Registration screens.
                    AuthScreen(viewModel = viewModel)
                } else {
                    when (val screen = currentScreen) {
                        is Screen.Splash -> {
                            SplashScreen(viewModel = viewModel)
                        }
                        is Screen.Home -> {
                            HomeScreen(viewModel = viewModel)
                        }
                        is Screen.CategoryView -> {
                            CategoryViewScreen(viewModel = viewModel, category = screen.category)
                        }
                        is Screen.ActiveQuiz -> {
                            ActiveQuizScreen(viewModel = viewModel, quiz = screen.quiz)
                        }
                        is Screen.Score -> {
                            ScoreScreen(viewModel = viewModel, quiz = screen.quiz, score = screen.score, totalQuestions = screen.totalQuestions)
                        }
                        is Screen.AdminDashboard -> {
                            val isBlocked by viewModel.isCurrentUserBlocked.collectAsState()
                            if (currentUserRole?.equals("admin", ignoreCase = true) == true && !isBlocked) {
                                AdminDashboardScreen(viewModel = viewModel)
                            } else {
                                HomeScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthScreen(viewModel: QuizViewModel) {
    val isAuthLoading by viewModel.isAuthLoading.collectAsState()
    val authMessage by viewModel.authMessage.collectAsState()

    var isSignInMode by varRemember { mutableStateOf(true) }
    var nameInput by varRemember { mutableStateOf("") }
    var emailInput by varRemember { mutableStateOf("") }
    var passwordInput by varRemember { mutableStateOf("") }
    var passwordVisible by varRemember { mutableStateOf(false) }
    var selectedRoleInput by varRemember { mutableStateOf("user") } // Default to standard user

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF000814),
                        Color(0xFF001D3D)
                    )
                )
            )
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Identity Header
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color.White.copy(alpha = 0.05f), CircleShape)
                .border(BorderStroke(2.dp, Color(0xFFFDE047)), CircleShape)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.icon_option_five_1782049925584),
                contentDescription = "App Logo",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "SPEED ENGLISH",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFFDE047),
                letterSpacing = 1.5.sp
            )
        )

        Text(
            text = "Premium SSC & Banking Practice Portal",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isSignInMode) "Sign In to Practice" else "Create Student Account",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Toggle between Sign In / Register
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSignInMode) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { 
                                isSignInMode = true
                                viewModel.clearAuthMessage() 
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sign In",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isSignInMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isSignInMode) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { 
                                isSignInMode = false
                                viewModel.clearAuthMessage() 
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Register",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (!isSignInMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                val authFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
                    unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary
                )

                // Name field (Visible only during Registration mode)
                AnimatedVisibility(
                    visible = !isSignInMode,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Full Name") },
                            placeholder = { Text("John Doe") },
                            singleLine = true,
                            colors = authFieldColors,
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Person, contentDescription = "User Icon", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("auth_name_field"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Email Address") },
                    placeholder = { Text("student@speedenglish.com") },
                    singleLine = true,
                    colors = authFieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Email, contentDescription = "Email Icon", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_email_field"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Password") },
                    colors = authFieldColors,
                    visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock Icon", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            val icon = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                            Icon(imageVector = icon, contentDescription = "Toggle password visibility", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password_field"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Forgot Password Button (Visible only during Sign In mode)
                if (isSignInMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = "Forgot Password?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable {
                                    // Comment: Initiates password reset for the typed email address.
                                    viewModel.resetPassword(emailInput)
                                }
                                .padding(vertical = 4.dp)
                        )
                    }
                }



                if (authMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = authMessage,
                        color = if (authMessage.contains("success", ignoreCase = true)) {
                            Color(0xFF10B981)
                        } else if (authMessage.contains("...", ignoreCase = true)) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isAuthLoading) {
                    CircularProgressIndicator(color = Color(0xFF4F46E5), modifier = Modifier.size(24.dp))
                } else {
                    Button(
                        onClick = {
                            if (emailInput.isNotBlank() && passwordInput.isNotBlank()) {
                                if (isSignInMode) {
                                    // Comment: Authenticate with Firebase Authentication.
                                    // On successful login, the app checks the user's role document from the Firestore 'users' collection.
                                    // If 'role' is 'admin', it unlocks the Admin tab options; if it is 'user', the user is directly 
                                    // routed to the main Student Home practice list.
                                    viewModel.signIn(emailInput, passwordInput)
                                } else {
                                    // Comment: Register a new account.
                                    // Saves the account on Firebase Authentication and automatically creates a matching document
                                    // inside the Firestore 'users' collection with the user's UID, Name, Email, and chosen Role.
                                    viewModel.signUp(
                                        email = emailInput,
                                        password = passwordInput,
                                        role = selectedRoleInput,
                                        name = nameInput,
                                        onSuccess = {
                                            isSignInMode = true
                                            passwordInput = ""
                                        }
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("auth_submit_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Text(
                            text = if (isSignInMode) "Sign In" else "Create Account",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }


            }
        }
    }
}

@Composable
fun SplashScreen(viewModel: QuizViewModel) {
    val isCurrentUserBlocked by viewModel.isCurrentUserBlocked.collectAsState()
    // Navigate automatically to Home after 2.5 seconds ONLY if not blocked
    LaunchedEffect(isCurrentUserBlocked) {
        if (!isCurrentUserBlocked) {
            delay(2500)
            if (!viewModel.isCurrentUserBlocked.value) {
                viewModel.navigateTo(Screen.Home, clearBackstack = true)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF000814),
                        Color(0xFF001D3D)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Elegant glowing outer ring for the premium generated logo icon
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
                    .border(BorderStroke(4.dp, Color(0xFFFDE047)), CircleShape)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.icon_option_five_1782049925584),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "SPEED ENGLISH",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFDE047),
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 2.sp
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Fast-Paced Vocabulary & Grammar",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.8f)
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            CircularProgressIndicator(
                color = Color(0xFF0EA5E9),
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = { viewModel.navigateTo(Screen.Home, clearBackstack = true) }
            ) {
                Text(
                    text = "Skip Intro",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

// ==========================================
// 1. HOME SCREEN WITH 4 BOTTOM TABS
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: QuizViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentUserRole by viewModel.currentUserRole.collectAsState()
    val isBlocked by viewModel.isCurrentUserBlocked.collectAsState()
    val isAdminUser = (currentUserRole?.equals("admin", ignoreCase = true) == true) && !isBlocked
    val canManageContent = isAdminUser
    var activeTabState by varRemember { mutableStateOf(0) } // 0: Practice, 1: Progress, 2: Profile, 3: Post, 4: Admin

    // Trigger Native Ad refresh ONLY on actual navigation to Practice (0), Progress (1), or Profile (2)
    // Does NOT trigger for Post (3) or Manage (4) or on repeated clicks / scrolling / recomposition
    LaunchedEffect(activeTabState) {
        when (activeTabState) {
            0 -> com.example.ads.NativeAdManager.refreshAdForPlacement(context, "practice_screen")
            1 -> com.example.ads.NativeAdManager.refreshAdForPlacement(context, "progress_screen")
            2 -> com.example.ads.NativeAdManager.refreshAdForPlacement(context, "profile_screen")
            else -> { /* No refresh for Post or Manage */ }
        }
    }

    Scaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(if (canManageContent) 0.98f else 0.92f)
                        .widthIn(max = 500.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
                    shape = RoundedCornerShape(22.dp),
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp
                ) {
                    NavigationBar(
                        modifier = Modifier.height(52.dp),
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        NavigationBarItem(
                            selected = activeTabState == 0,
                            onClick = { activeTabState = 0 },
                            icon = { Icon(Icons.Default.MenuBook, contentDescription = "Practice", modifier = Modifier.size(18.dp)) },
                            label = { Text("Practice", fontSize = 9.sp, fontWeight = FontWeight.SemiBold) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            )
                        )
                        NavigationBarItem(
                            selected = activeTabState == 1,
                            onClick = { activeTabState = 1 },
                            icon = { Icon(Icons.Default.TrendingUp, contentDescription = "Progress", modifier = Modifier.size(18.dp)) },
                            label = { Text("Progress", fontSize = 9.sp, fontWeight = FontWeight.SemiBold) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            )
                        )
                        NavigationBarItem(
                            selected = activeTabState == 2,
                            onClick = { activeTabState = 2 },
                            icon = { Icon(Icons.Default.Person, contentDescription = "Profile", modifier = Modifier.size(18.dp)) },
                            label = { Text("Profile", fontSize = 9.sp, fontWeight = FontWeight.SemiBold) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            )
                        )
                        NavigationBarItem(
                            selected = activeTabState == 3,
                            onClick = { activeTabState = 3 },
                            icon = { Icon(Icons.Default.Article, contentDescription = "Post", modifier = Modifier.size(18.dp)) },
                            label = { Text("Post", fontSize = 9.sp, fontWeight = FontWeight.SemiBold) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            )
                        )
                        if (canManageContent) {
                            NavigationBarItem(
                                selected = activeTabState == 4,
                                onClick = { activeTabState = 4 },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Manage", modifier = Modifier.size(18.dp)) },
                                label = { Text("Manage", fontSize = 9.sp, fontWeight = FontWeight.SemiBold) },
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 850.dp)
            ) {
                when (activeTabState) {
                    0 -> PracticeTabContent(viewModel = viewModel, onNavigateToAdmin = { if (canManageContent) { activeTabState = if (currentUserRole?.equals("admin", ignoreCase = true) == true) 4 else 2 } })
                    1 -> ProgressTabContent(viewModel = viewModel)
                    2 -> ProfileTabContent(viewModel = viewModel)
                    3 -> PostTabContent(viewModel = viewModel)
                    4 -> if (canManageContent) AdminTabContent(viewModel = viewModel) else PracticeTabContent(viewModel = viewModel, onNavigateToAdmin = { })
                }
            }
        }
    }
}

// ==========================================
// A. PRACTICE TAB (METRICS & GRID)
// ==========================================
@Composable
fun PracticeTabContent(viewModel: QuizViewModel, onNavigateToAdmin: () -> Unit) {
    val categories by viewModel.categories.collectAsState()
    val attempts by viewModel.attemptsList.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val savedProgress by viewModel.savedQuizProgress.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    var isSearchActive by varRemember { mutableStateOf(false) }
    var searchQuery by varRemember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadUserAttemptsFromFirestore()
    }

    // Calculate dynamic stats
    val totalQs = attempts.sumOf { it.totalQuestions.coerceAtLeast(1) }
    val correctAnswers = attempts.sumOf { attempt ->
        if (attempt.correctCount == 0 && attempt.wrongCount == 0) {
            attempt.score.toInt().coerceAtLeast(0)
        } else {
            attempt.correctCount
        }
    }.toFloat()
    val accuracyPct = if (totalQs > 0) ((correctAnswers / totalQs) * 100).toInt().coerceIn(0, 100) else 0

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gridColumns = if (maxWidth >= 720.dp) 4 else if (maxWidth >= 480.dp) 3 else 2

        PullToRefreshLayout(
            isRefreshing = isSyncing,
            onRefresh = { viewModel.refreshAllAppData() },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
        // Profile Header matching photo perfectly
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // SE Circular initial badge replaced with actual premium cosmic book logo icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(BorderStroke(1.5.dp, Color(0xFFFDE047)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.icon_option_five_1782049925584),
                    contentDescription = "Speed English Logo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Speed English",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "SSC & Banking Master Drills",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val currentUserRole by viewModel.currentUserRole.collectAsState()
            val isBlocked by viewModel.isCurrentUserBlocked.collectAsState()
            val canManageContent = !isBlocked

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { 
                        isSearchActive = !isSearchActive 
                        if (!isSearchActive) searchQuery = ""
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isDarkTheme) Color(0xFF818CF8).copy(alpha = 0.15f) else Color(0xFFEEF2FF))
                        .testTag("practice_search_icon")
                ) {
                    Icon(
                        imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search Categories",
                        tint = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5),
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (canManageContent) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onNavigateToAdmin,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isDarkTheme) Color(0xFF818CF8).copy(alpha = 0.15f) else Color(0xFFEEF2FF))
                            .testTag("admin_gear_icon_shortcut")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Content Management Shortcut",
                            tint = if (isDarkTheme) Color(0xFF818CF8) else Color(0xFF4F46E5),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Performance block
        Text(
            text = "CURRENT PERFORMANCE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(bottom = 2.dp)
        )

        val totalXp = attempts.sumOf { it.score.toDouble() }.toInt().coerceAtLeast(0)
        Text(
            text = "$totalXp XP",
            fontWeight = FontWeight.Black,
            fontSize = 28.sp,
            color = Color(0xFF4F46E5),
            modifier = Modifier.padding(bottom = 10.dp)
        )

        // Row of 2 Statistics side-by-side
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Stats 1
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFDCFCE7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Success check",
                        tint = Color(0xFF15803D),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "DRILLS DONE",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$totalQs Qs",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Stats 2
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEE2E2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Accuracy star",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "ACCURACY SCORE",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$accuracyPct%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Unfinished Quiz Resume Banner
        savedProgress?.let { progress ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("resume_quiz_card"),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, Color(0xFF4F46E5).copy(alpha = 0.35f)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4F46E5).copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Resume Icon",
                                tint = Color(0xFF4F46E5),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CONTINUE YOUR DRILL",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF4F46E5),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = progress.quizTitle,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Text(
                        text = "You completed ${progress.currentQuestionIndex} of ${progress.questions.size} questions in ${progress.categoryName}. Resume to complete the quiz!",
                        fontSize = 13.sp,
                        color = Color(0xFF475569)
                    )
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { viewModel.resumeQuiz() },
                            modifier = Modifier.weight(1.3f).testTag("resume_quiz_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Resume Quiz", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        
                        OutlinedButton(
                            onClick = { viewModel.clearQuizProgress() },
                            modifier = Modifier.weight(1f).testTag("discard_quiz_btn"),
                            border = BorderStroke(1.5.dp, Color(0xFFEF4444)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Discard", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Heading: Practice Categories
        if (!isSearchActive) {
            Text(
                text = "Practice Categories",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (isSearchActive) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search categories...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("practice_search_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
        }

        val filteredCategories = if (searchQuery.isBlank()) {
            categories
        } else {
            categories.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.description.contains(searchQuery, ignoreCase = true)
            }
        }

        if (filteredCategories.isEmpty() && categories.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "No results",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No matching categories found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No categories found. Head to Admin to create or seed default data.", color = Color(0xFF64748B))
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { viewModel.restoreDefaultSeedData() }) {
                        Text("Seed Default Data")
                    }
                }
            }
        } else {
            val parentCategories = categories.filter { cat -> categories.any { it.parentCategoryId == cat.documentId } }
            val hasGrouping = parentCategories.isNotEmpty()

            if (!hasGrouping) {
                val chunks = filteredCategories.chunked(gridColumns)
                chunks.forEach { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { category ->
                            val itemColor = rememberCategoryColor(category, viewModel)
                            val isCustomIcon = category.iconName.startsWith("data:") ||
                                    category.iconName.startsWith("http://") ||
                                    category.iconName.startsWith("https://") ||
                                    category.iconName.startsWith("content://") ||
                                    category.iconName.startsWith("file://")

                            Card(
                                onClick = { viewModel.selectCategory(category) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 115.dp)
                                    .testTag("category_card_${category.name.lowercase()}"),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.5.dp, itemColor.copy(alpha = if (isCustomIcon) 0.85f else 0.35f)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isCustomIcon) {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(itemColor.copy(alpha = 0.12f))
                                                .border(BorderStroke(1.5.dp, itemColor.copy(alpha = 0.5f)), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CategoryIcon(
                                                    iconName = category.iconName,
                                                    contentDescription = category.name,
                                                    tint = itemColor,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(itemColor.copy(alpha = 0.08f))
                                                .border(BorderStroke(1.dp, itemColor.copy(alpha = 0.2f)), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(31.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CategoryIcon(
                                                    iconName = category.iconName,
                                                    contentDescription = null,
                                                    tint = itemColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = category.name,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (category.description.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = category.description,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (rowItems.size < gridColumns) {
                            repeat(gridColumns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                parentCategories.forEachIndexed { parentIndex, parent ->
                    val parentMatch = parent.name.contains(searchQuery, ignoreCase = true) || parent.description.contains(searchQuery, ignoreCase = true)
                    val parentSubs = categories.filter {
                        it.parentCategoryId == parent.documentId &&
                        (parentMatch || searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true))
                    }

                    if (parentSubs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = parent.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        if (parent.description.isNotEmpty()) {
                            Text(
                                text = parent.description,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        val chunks = parentSubs.chunked(gridColumns)
                        chunks.forEach { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowItems.forEach { category ->
                                    val itemColor = rememberCategoryColor(category, viewModel)
                                    val isCustomIcon = category.iconName.startsWith("data:") ||
                                            category.iconName.startsWith("http://") ||
                                            category.iconName.startsWith("https://") ||
                                            category.iconName.startsWith("content://") ||
                                            category.iconName.startsWith("file://")

                                    Card(
                                        onClick = { viewModel.selectCategory(category) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 115.dp)
                                            .testTag("category_card_${category.name.lowercase()}"),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.5.dp, itemColor.copy(alpha = if (isCustomIcon) 0.85f else 0.35f)),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (isCustomIcon) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(52.dp)
                                                        .clip(CircleShape)
                                                        .background(itemColor.copy(alpha = 0.12f))
                                                        .border(BorderStroke(1.5.dp, itemColor.copy(alpha = 0.5f)), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(38.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.surface)
                                                            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        CategoryIcon(
                                                            iconName = category.iconName,
                                                            contentDescription = category.name,
                                                            tint = itemColor,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(CircleShape)
                                                        .background(itemColor.copy(alpha = 0.08f))
                                                        .border(BorderStroke(1.dp, itemColor.copy(alpha = 0.2f)), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(31.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.surface)
                                                            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        CategoryIcon(
                                                            iconName = category.iconName,
                                                            contentDescription = null,
                                                            tint = itemColor,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                Text(
                                                    text = category.name,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 14.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (category.description.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(3.dp))
                                                    Text(
                                                        text = category.description,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        lineHeight = 14.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (rowItems.size < gridColumns) {
                                    repeat(gridColumns - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                // Speed Math-style Native Ad in centre of Practice screen
                com.example.ads.NativeAdContainer(
                    placement = "practice_screen",
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(14.dp))

                val independentCategories = filteredCategories.filter { cat ->
                    cat.parentCategoryId == null && !categories.any { it.parentCategoryId == cat.documentId }
                }

                if (independentCategories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "General Categories",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    val chunks = independentCategories.chunked(gridColumns)
                    chunks.forEach { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { category ->
                                val itemColor = rememberCategoryColor(category, viewModel)
                                val isCustomIcon = category.iconName.startsWith("data:") ||
                                        category.iconName.startsWith("http://") ||
                                        category.iconName.startsWith("https://") ||
                                        category.iconName.startsWith("content://") ||
                                        category.iconName.startsWith("file://")

                                Card(
                                    onClick = { viewModel.selectCategory(category) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 115.dp)
                                        .testTag("category_card_${category.name.lowercase()}"),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.5.dp, itemColor.copy(alpha = if (isCustomIcon) 0.85f else 0.35f)),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isCustomIcon) {
                                            Box(
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(CircleShape)
                                                    .background(itemColor.copy(alpha = 0.12f))
                                                    .border(BorderStroke(1.5.dp, itemColor.copy(alpha = 0.5f)), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CategoryIcon(
                                                    iconName = category.iconName,
                                                    contentDescription = category.name,
                                                    tint = itemColor,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(itemColor.copy(alpha = 0.08f))
                                                    .border(BorderStroke(1.dp, itemColor.copy(alpha = 0.2f)), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(31.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.surface)
                                                        .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CategoryIcon(
                                                        iconName = category.iconName,
                                                        contentDescription = null,
                                                        tint = itemColor,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = category.name,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (category.description.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Text(
                                                    text = category.description,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    lineHeight = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (rowItems.size < gridColumns) {
                                repeat(gridColumns - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
}

// ==========================================
// B. PROGRESS TAB (HISTORY & ACCURACY DETAILED)
// ==========================================
@Composable
fun RechartsPerformanceChart(attempts: List<QuizAttempt>) {
    if (attempts.isEmpty()) return

    val chartAttempts = attempts.takeLast(8) // Limit to last 8 attempts for clean spacing
    var isBarChart by remember { mutableStateOf(false) } // Toggle between Line and Bar chart
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current

    val multiColors = listOf(
        Color(0xFFF97316), // Orange
        Color(0xFF3B82F6), // Blue
        Color(0xFF8B5CF6), // Purple
        Color(0xFFEAB308), // Yellow
        Color(0xFFEC4899), // Pink
        Color(0xFF10B981)  // Green
    )
    val redLowColor = Color(0xFFEF4444) // Red for lower progress (<50%)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with title and 2 Chart Type Switcher Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PERFORMANCE ANALYTICS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Drills Score History",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // 2 Small Buttons to Switch Bar vs Line Graph
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(3.dp)
                ) {
                    // Line Graph Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (!isBarChart) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { isBarChart = false }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ShowChart,
                                contentDescription = "Line Chart",
                                tint = if (!isBarChart) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Line",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isBarChart) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Bar Graph Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isBarChart) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { isBarChart = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = "Bar Chart",
                                tint = if (isBarChart) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Bar",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isBarChart) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cartesian chart area using Canvas
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            ) {
                val width = constraints.maxWidth.toFloat()
                val height = constraints.maxHeight.toFloat()

                val paddingLeft = with(density) { 36.dp.toPx() }
                val paddingRight = with(density) { 16.dp.toPx() }
                val paddingTop = with(density) { 16.dp.toPx() }
                val paddingBottom = with(density) { 28.dp.toPx() }

                val chartWidth = width - paddingLeft - paddingRight
                val chartHeight = height - paddingTop - paddingBottom

                val count = chartAttempts.size
                val maxSpacing = with(density) { 64.dp.toPx() }
                val fullWidthSpacing = if (count > 1) chartWidth / (count - 1) else 0f
                val spacing = if (count > 1) fullWidthSpacing.coerceAtMost(maxSpacing) else 0f
                val groupWidth = if (count > 1) spacing * (count - 1) else 0f
                val startX = if (count > 1) paddingLeft + (chartWidth - groupWidth) / 2f else paddingLeft + (chartWidth / 2f)

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(chartAttempts, isBarChart) {
                            detectTapGestures { offset ->
                                var nearestIdx: Int? = null
                                var minDistance = if (count > 1) spacing / 1.2f else 80f
                                chartAttempts.forEachIndexed { idx, _ ->
                                    val x = if (count > 1) startX + idx * spacing else startX
                                    val dist = kotlin.math.abs(offset.x - x)
                                    if (dist < minDistance) {
                                        minDistance = dist
                                        nearestIdx = idx
                                    }
                                }
                                selectedIndex = if (selectedIndex == nearestIdx) null else nearestIdx
                            }
                        }
                ) {
                    // 1. Draw horizontal grid lines (Y-axis)
                    val gridSteps = 4
                    for (i in 0..gridSteps) {
                        val fraction = i.toFloat() / gridSteps
                        val y = paddingTop + fraction * chartHeight

                        drawLine(
                            color = Color.Gray.copy(alpha = 0.12f),
                            start = Offset(paddingLeft, y),
                            end = Offset(width - paddingRight, y),
                            strokeWidth = 1.5f
                        )

                        val labelText = "${(100 - fraction * 100).toInt()}%"
                        drawContext.canvas.nativeCanvas.drawText(
                            labelText,
                            paddingLeft - 12f,
                            y + 4f,
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.parseColor("#94A3B8")
                                textSize = with(density) { 9.sp.toPx() }
                                textAlign = android.graphics.Paint.Align.RIGHT
                                isAntiAlias = true
                            }
                        )
                    }

                    // Calculate point offsets and item colors capped strictly to 0..1
                    val pointsWithColors = chartAttempts.mapIndexed { index, attempt ->
                        val x = if (count > 1) startX + index * spacing else startX

                        val totalQ = attempt.totalQuestions.coerceAtLeast(1)
                        val score = attempt.score.coerceIn(0f, totalQ.toFloat())
                        val accuracy = (score / totalQ).coerceIn(0f, 1f)
                        val y = paddingTop + (1f - accuracy) * chartHeight

                        // Color logic: Red if score < 50%, otherwise pick from multiColors
                        val color = if (accuracy < 0.5f) {
                            redLowColor
                        } else {
                            multiColors[index % multiColors.size]
                        }

                        Triple(Offset(x, y), color, accuracy)
                    }

                    // 2. Draw X-axis labels and guides
                    pointsWithColors.forEachIndexed { index, triple ->
                        val pt = triple.first
                        val labelText = "#${index + 1}"

                        drawContext.canvas.nativeCanvas.drawText(
                            labelText,
                            pt.x,
                            height - 6f,
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.parseColor("#94A3B8")
                                textSize = with(density) { 9.sp.toPx() }
                                textAlign = android.graphics.Paint.Align.CENTER
                                isAntiAlias = true
                            }
                        )
                    }

                    if (isBarChart) {
                        // --- BAR GRAPH MODE ---
                        val barWidth = if (count > 1) (spacing * 0.45f).coerceIn(16f, 32f) else with(density) { 24.dp.toPx() }

                        pointsWithColors.forEachIndexed { index, triple ->
                            val pt = triple.first
                            val barColor = triple.second
                            val barTop = pt.y.coerceIn(paddingTop, paddingTop + chartHeight)
                            val barBottom = paddingTop + chartHeight

                            val left = pt.x - (barWidth / 2f)
                            val top = barTop
                            val bottom = barBottom

                            val isSelected = selectedIndex == index

                            // Draw Bar background highlight if selected
                            if (isSelected) {
                                drawRoundRect(
                                    color = barColor.copy(alpha = 0.18f),
                                    topLeft = Offset(left - 4f, paddingTop),
                                    size = androidx.compose.ui.geometry.Size(barWidth + 8f, chartHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                                )
                            }

                            // Draw main bar
                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(barWidth, (bottom - top).coerceAtLeast(4f)),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                            )
                        }
                    } else {
                        // --- LINE GRAPH MODE ---
                        // Draw gradient fill area
                        if (pointsWithColors.isNotEmpty()) {
                            val areaPath = androidx.compose.ui.graphics.Path().apply {
                                moveTo(pointsWithColors.first().first.x, paddingTop + chartHeight)
                                pointsWithColors.forEach {
                                    lineTo(it.first.x, it.first.y)
                                }
                                lineTo(pointsWithColors.last().first.x, paddingTop + chartHeight)
                                close()
                            }

                            drawPath(
                                path = areaPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF3B82F6).copy(alpha = 0.25f),
                                        Color(0xFF3B82F6).copy(alpha = 0.0f)
                                    ),
                                    startY = paddingTop,
                                    endY = paddingTop + chartHeight
                                )
                            )
                        }

                        // Draw connecting lines with colors
                        if (pointsWithColors.size > 1) {
                            for (i in 0 until pointsWithColors.size - 1) {
                                drawLine(
                                    color = pointsWithColors[i].second,
                                    start = pointsWithColors[i].first,
                                    end = pointsWithColors[i + 1].first,
                                    strokeWidth = 6f,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            }
                        }

                        // Draw node dots
                        pointsWithColors.forEachIndexed { index, triple ->
                            val pt = triple.first
                            val dotColor = triple.second

                            if (selectedIndex == index) {
                                drawCircle(
                                    color = dotColor.copy(alpha = 0.25f),
                                    radius = 22f,
                                    center = pt
                                )
                                drawCircle(
                                    color = dotColor,
                                    radius = 9f,
                                    center = pt
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 4f,
                                    center = pt
                                )
                            } else {
                                drawCircle(
                                    color = dotColor,
                                    radius = 7f,
                                    center = pt
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 3f,
                                    center = pt
                                )
                            }
                        }
                    }
                }
            }

            // Interactive Tooltip Box showing date & time timestamp
            AnimatedVisibility(
                visible = selectedIndex != null && selectedIndex!! < chartAttempts.size,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                selectedIndex?.let { idx ->
                    val attempt = chartAttempts[idx]
                    val pct = ((attempt.score.toFloat() / attempt.totalQuestions) * 100).toInt()
                    val formattedDateTime = try {
                        val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).apply {
                            timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                        }
                        sdf.format(java.util.Date(attempt.dateMillis))
                    } catch (e: Exception) {
                        "Saved attempt"
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = attempt.quizTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Category: ${attempt.categoryName}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${formatDecimal(attempt.score)} Marks",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF34D399)
                                    )
                                    Text(
                                        text = "${attempt.totalQuestions} Questions",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = Color(0xFF1E293B))
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = "Saved Time",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Saved: $formattedDateTime",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                if (pct < 50) {
                                    Text(
                                        text = "Needs Improvement ⚠️",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Interactive helper label
            if (selectedIndex == null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = "Tap tooltip",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Tap on bars or nodes to view exact date, time & drill score details",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ProgressTabContent(viewModel: QuizViewModel) {
    val attempts by viewModel.attemptsList.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadUserAttemptsFromFirestore()
    }

    PullToRefreshLayout(
        isRefreshing = isSyncing,
        onRefresh = {
            viewModel.refreshAllAppData()
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = "Performance Diagnostics",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Review your historical growth and drill accuracy.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

        // General Stats card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "LIFETIME ACHIEVEMENTS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val attemptsCount = attempts.size
                val correctCount = attempts.sumOf { it.score.toDouble() }.toFloat()
                val totalQuestionsAsked = attempts.sumOf { it.totalQuestions.coerceAtLeast(1) }
                val overallAccuracy = if (totalQuestionsAsked > 0) ((correctCount.toFloat() / totalQuestionsAsked) * 100).toInt().coerceIn(0, 100) else 0

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Total Drills", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$attemptsCount Completed", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Column {
                        Text("Accuracy", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$overallAccuracy%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Column {
                        Text("Total Qs Run", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$totalQuestionsAsked answered", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        // Recharts Trend Performance Chart
        RechartsPerformanceChart(attempts = attempts)

        Spacer(modifier = Modifier.height(16.dp))

        // Native Ad below the primary progress summary and analytics
        com.example.ads.NativeAdContainer(
            placement = "progress_screen",
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Historical list header
        Text(
            text = "Drills History log",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (attempts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No history recorded yet. Open the Practice tab to complete your first drill!", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            attempts.asReversed().forEachIndexed { index, attempt ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = attempt.quizTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Category: ${attempt.categoryName}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val attemptDateStr = try {
                                val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).apply {
                                    timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                                }
                                sdf.format(java.util.Date(attempt.dateMillis))
                            } catch (e: Exception) {
                                ""
                            }
                            if (attemptDateStr.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = "Date and Time",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = attemptDateStr,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            val totalQ = attempt.totalQuestions.coerceAtLeast(1)
                            val scoreValue = attempt.score.coerceAtLeast(0f)
                            
                            val resolvedCorrect = if (attempt.correctCount == 0 && attempt.wrongCount == 0) {
                                scoreValue.toInt().coerceIn(0, totalQ)
                            } else {
                                attempt.correctCount
                            }
                            val resolvedWrong = if (attempt.correctCount == 0 && attempt.wrongCount == 0) {
                                (totalQ - resolvedCorrect).coerceAtLeast(0)
                            } else {
                                attempt.wrongCount
                            }

                            Text(
                                text = "${formatDecimal(scoreValue)} Marks",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$resolvedCorrect Correct",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF22C55E)
                                )
                                Text(
                                    text = "•",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Text(
                                    text = "$resolvedWrong Wrong",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
}

// ==========================================
// C. PROFILE TAB (USER PROFILE & PROGRESS)
// ==========================================
@Composable
fun ProfileTabContent(viewModel: QuizViewModel) {
    val email by viewModel.currentUserEmail.collectAsState()
    val name by viewModel.currentUserName.collectAsState()
    val role by viewModel.currentUserRole.collectAsState()
    val attempts by viewModel.attemptsList.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    var showEditProfileDialog by varRemember { mutableStateOf(false) }
    var newNameInput by varRemember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadUserAttemptsFromFirestore()
    }

    // Tab state for Today, 7 Days, 30 Days
    var progressTabState by varRemember { mutableStateOf(0) } // 0: Today, 1: 7 Days, 2: 30 Days

    var showPrivacyPolicyDialog by varRemember { mutableStateOf(false) }
    var showContactUsDialog by varRemember { mutableStateOf(false) }
    var showAboutDialog by varRemember { mutableStateOf(false) }
    var showAdminContactUsDialog by varRemember { mutableStateOf(false) }
    var showAdminPrivacyPolicyDialog by varRemember { mutableStateOf(false) }
    var showAdminPostManagementDialog by varRemember { mutableStateOf(false) }

    // Calculate progress based on the selection
    val filteredAttempts = remember(attempts, progressTabState) {
        val now = System.currentTimeMillis()
        when (progressTabState) {
            0 -> {
                // Today (Start of Indian Standard Time today)
                val startOfToday = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata")).apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                attempts.filter { it.dateMillis >= startOfToday }
            }
            1 -> {
                // 7 Days
                val startOf7Days = now - (7L * 24 * 60 * 60 * 1000L)
                attempts.filter { it.dateMillis >= startOf7Days }
            }
            2 -> {
                // 30 Days
                val startOf30Days = now - (30L * 24 * 60 * 60 * 1000L)
                attempts.filter { it.dateMillis >= startOf30Days }
            }
            else -> attempts
        }
    }

    val completedQuizzes = filteredAttempts.size
    val totalQs = filteredAttempts.sumOf { it.totalQuestions.coerceAtLeast(1) }
    val correctAnswers = filteredAttempts.sumOf { attempt ->
        if (attempt.correctCount == 0 && attempt.wrongCount == 0) {
            attempt.score.toInt().coerceAtLeast(0)
        } else {
            attempt.correctCount
        }
    }.toFloat()
    val accuracyPct = if (totalQs > 0) ((correctAnswers / totalQs) * 100).toInt().coerceIn(0, 100) else 0
    val totalXp = filteredAttempts.sumOf { it.score.toDouble() }.toInt().coerceAtLeast(0)

    PullToRefreshLayout(
        isRefreshing = isSyncing,
        onRefresh = {
            viewModel.refreshAllAppData()
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        // AESTHETIC USER PROFILE HEADER
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Stylish Avatar circle with initials
                val initials = if (!name.isNullOrBlank()) {
                    name!!.split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")
                } else {
                    email?.firstOrNull()?.uppercase() ?: "U"
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF4F46E5), Color(0xFF0D9488))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = name ?: "User Name",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = {
                            newNameInput = name ?: ""
                            showEditProfileDialog = true
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile Name",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = email ?: "user@example.com",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Role badge with adaptive theme styling
                val displayRole = if (role?.lowercase() == "admin") "Admin" else "Standard User"
                val badgeBg = if (role?.lowercase() == "admin") {
                    if (isDarkTheme) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFFFEF2F2)
                } else {
                    if (isDarkTheme) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF0FDF4)
                }
                val badgeText = if (role?.lowercase() == "admin") {
                    if (isDarkTheme) Color(0xFFFCA5A5) else Color(0xFFEF4444)
                } else {
                    if (isDarkTheme) Color(0xFF86EFAC) else Color(0xFF10B981)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBg)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = displayRole,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeText
                    )
                }
            }
        }

        // PREFERENCES AND OPTIONS CARD (Theme toggle and Log out)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Theme Toggle Item
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable { viewModel.toggleTheme() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Theme Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "App Theme",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isDarkTheme) "Dark Mode" else "Light Mode",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { viewModel.toggleTheme() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                // Admin Panel & Content Manager Shortcut Button (Strictly restricted to Admin role)
                if (role?.equals("admin", ignoreCase = true) == true) {
                    Button(
                        onClick = { viewModel.navigateTo(Screen.AdminDashboard) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("profile_admin_panel_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Admin Panel",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Admin Panel & Content Manager",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // Sign Out Button with Small App Logo Badge
                Button(
                    onClick = { viewModel.signOut() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("profile_logout_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .border(BorderStroke(1.dp, Color(0xFFFDE047)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.icon_option_five_1782049925584),
                            contentDescription = "Speed English Logo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Sign Out",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Sign Out",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // SUPPORT & INFORMATION CARD (User & Admin Role Settings)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "SUPPORT & ABOUT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )

                // Contact Us Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable { showContactUsDialog = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SupportAgent,
                            contentDescription = "Contact Us",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Contact Us",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Get support via WhatsApp, Telegram, Email, etc.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Privacy Policy Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable { showPrivacyPolicyDialog = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PrivacyTip,
                            contentDescription = "Privacy Policy",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Privacy Policy",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "View data protection and privacy policy",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // About Speed English Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable { showAboutDialog = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About Speed English",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "About Speed English",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "App version, details, and features",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // ADMIN ROLE EXTRA SETTINGS
                if (role?.equals("admin", ignoreCase = true) == true) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text(
                        text = "ADMIN MANAGEMENT SETTINGS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .clickable { showAdminContactUsDialog = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SupportAgent,
                                contentDescription = "Contact Us Management",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Contact Us Management",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Add, edit, enable/disable support methods",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Manage",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .clickable { showAdminPrivacyPolicyDialog = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PrivacyTip,
                                contentDescription = "Privacy Policy Management",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Privacy Policy Management",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Edit & publish app privacy terms",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Manage",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .clickable { showAdminPostManagementDialog = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DynamicFeed,
                                contentDescription = "Post Management",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Post Management",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Create, edit, pin, unpublish & edit live counters",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Manage",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (showPrivacyPolicyDialog) {
            UserPrivacyPolicyDialog(viewModel = viewModel, onDismiss = { showPrivacyPolicyDialog = false })
        }
        if (showContactUsDialog) {
            UserContactUsDialog(viewModel = viewModel, onDismiss = { showContactUsDialog = false })
        }
        if (showAboutDialog) {
            AboutSpeedEnglishDialog(onDismiss = { showAboutDialog = false })
        }
        if (showAdminContactUsDialog) {
            AlertDialog(
                onDismissRequest = { showAdminContactUsDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SupportAgent, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Contact Us Management", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }
                },
                text = {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                        AdminContactUsManagementContent(viewModel = viewModel)
                    }
                },
                confirmButton = {
                    Button(onClick = { showAdminContactUsDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
        if (showAdminPrivacyPolicyDialog) {
            AlertDialog(
                onDismissRequest = { showAdminPrivacyPolicyDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Privacy Policy Management", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }
                },
                text = {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                        AdminPrivacyPolicyManagementContent(viewModel = viewModel)
                    }
                },
                confirmButton = {
                    Button(onClick = { showAdminPrivacyPolicyDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
        if (showAdminPostManagementDialog) {
            AlertDialog(
                onDismissRequest = { showAdminPostManagementDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DynamicFeed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Post Management", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }
                },
                text = {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                        AdminPostManagementContent(viewModel = viewModel)
                    }
                },
                confirmButton = {
                    Button(onClick = { showAdminPostManagementDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        if (showEditProfileDialog) {
            var updatingProfile by remember { mutableStateOf(false) }
            var editProfileError by remember { mutableStateOf<String?>(null) }

            AlertDialog(
                onDismissRequest = { if (!updatingProfile) showEditProfileDialog = false },
                title = { Text("Edit Display Name", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newNameInput,
                            onValueChange = { 
                                newNameInput = it 
                                editProfileError = null
                            },
                            label = { Text("Display Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (editProfileError != null) {
                            Text(
                                text = editProfileError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newNameInput.isNotBlank()) {
                                updatingProfile = true
                                editProfileError = null
                                viewModel.updateUserProfile(newNameInput) { success, err ->
                                    updatingProfile = false
                                    if (success) {
                                        showEditProfileDialog = false
                                    } else {
                                        editProfileError = err ?: "Failed to sync to Cloud Firestore"
                                    }
                                }
                            }
                        },
                        enabled = !updatingProfile && newNameInput.isNotBlank()
                    ) {
                        if (updatingProfile) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Save Profile")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showEditProfileDialog = false },
                        enabled = !updatingProfile
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // PROGRESS ANALYTICS SECTION
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Learning Progress",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Track your speed and accuracy metrics",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Beautiful Segmented Toggle Buttons for Progress Interval
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val tabs = listOf("Today", "7 Days", "30 Days")
            tabs.forEachIndexed { index, title ->
                val isSelected = progressTabState == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { progressTabState = index }
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Analytics Cards Grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Card 1: XP Gained
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "XP",
                            tint = Color(0xFFEA580C),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$totalXp XP",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "XP Earned",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Card 2: Quizzes completed
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Quizzes",
                            tint = Color(0xFFEAB308),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$completedQuizzes",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Completed",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Card 3: Questions Answered
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assignment,
                            contentDescription = "Questions",
                            tint = Color(0xFF4F46E5),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$totalQs Qs",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Answered",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Card 4: Accuracy
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Percent,
                            contentDescription = "Accuracy",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$accuracyPct%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Success Rate",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Lower portion native ad below all profile identity and learning statistics
            com.example.ads.NativeAdContainer(
                placement = "profile_screen",
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
}

// ==========================================
// D. ADMIN TAB (PASSWORD LOBBY INTERACTION OVERRIDDEN & DIRECTLY RENDERED)
// ==========================================
@Composable
fun AdminTabContent(viewModel: QuizViewModel) {
    // Render standard CRUD admin dashboard panel inside the Tab scroll elegantly without passkey lobby as requested!
    AdminConsoleEmbed(viewModel = viewModel)
}

// Embedded Admin dashboard implementation directly rendering in space
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminConsoleEmbed(viewModel: QuizViewModel) {
    // We already have AdminDashboardScreen in QuizAppScreens.kt, but we can reuse its visual elements
    // so let's let AdminConsoleEmbed call AdminDashboardScreen directly to render fully with verification bypassed!
    AdminDashboardScreen(viewModel = viewModel, initiallyVerified = true)
}

// ==========================================
// 2. CATEGORY VIEW SCREEN (QUIZ LIST)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryViewScreen(viewModel: QuizViewModel, category: Category) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val quizzes by viewModel.quizzesForSelectedCategory.collectAsState()
    val countsMap by viewModel.quizQuestionsCountMap.collectAsState()
    var isSearchActive by varRemember { mutableStateOf(false) }
    var searchQuery by varRemember { mutableStateOf("") }

    LaunchedEffect(category.documentId) {
        viewModel.updateQuizQuestionsCounts()
        viewModel.fetchNewestQuestionsDirectlyFromServer(categoryId = category.documentId)
        RewardedInterstitialAdManager.preloadAll(context)
    }

    val filteredQuizzes = if (searchQuery.isBlank()) {
        quizzes
    } else {
        quizzes.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = category.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    val activity = remember(context) { context.findActivity() }
                    IconButton(onClick = {
                        RewardedInterstitialAdManager.showBackNavigationAd(activity) {
                            viewModel.navigateBack()
                        }
                    }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        },
                        modifier = Modifier.testTag("quizzes_search_icon")
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search Quizzes"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        val isSyncing by viewModel.isSyncing.collectAsState()
        val isSnapshotSyncing by viewModel.isSnapshotSyncing.collectAsState()
        val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsState()
        val lastSyncTime by viewModel.lastSyncTime.collectAsState()
        val firestoreQuizError by viewModel.firestoreQuizError.collectAsState()
        val isLiveSyncing = isSyncing || isSnapshotSyncing

        PullToRefreshLayout(
            isRefreshing = isSyncing,
            onRefresh = {
                viewModel.fetchFromFirestoreCloud()
                viewModel.updateQuizQuestionsCounts()
                viewModel.fetchNewestQuestionsDirectlyFromServer(categoryId = category.documentId)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // Category Header Card
            val headerColor = rememberCategoryColor(category, viewModel)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = headerColor.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isCustomHeaderIcon = category.iconName.startsWith("data:") ||
                                category.iconName.startsWith("http://") ||
                                category.iconName.startsWith("https://") ||
                                category.iconName.startsWith("content://") ||
                                category.iconName.startsWith("file://")

                        CategoryIcon(
                            iconName = category.iconName,
                            contentDescription = "Category Icon",
                            tint = headerColor,
                            modifier = Modifier.size(if (isCustomHeaderIcon) 52.dp else 36.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${category.name} Exploration",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = headerColor
                        )
                    }
                    if (category.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = category.description,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Centralized Firestore Connection Error State Component
            if (firestoreQuizError != null) {
                QuizCentralizedErrorState(
                    errorMessage = firestoreQuizError ?: "",
                    onRetry = { viewModel.retryQuizScreenFetch() },
                    isRetrying = isSyncing,
                    onDismiss = { viewModel.clearFirestoreQuizError() }
                )
            }

            // Global Real-Time Loading Spinner Banner during SnapshotListener Sync
            AnimatedVisibility(
                visible = isLiveSyncing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .padding(bottom = 14.dp)
                        .testTag("quiz_sync_loading_indicator"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(22.dp)
                                .testTag("quiz_snapshot_sync_spinner"),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Syncing live question data...",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Fetching newest questions directly from server",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            if (isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search quizzes...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .padding(bottom = 12.dp)
                        .testTag("quizzes_search_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            }

            // Header Row with Title & Last Synced Timestamp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Available Quizzes",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                // Last Synced Timestamp UI badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.testTag("last_synced_timestamp_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isNetworkAvailable) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(13.dp)
                            )
                        } else if (isLiveSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(11.dp)
                                    .testTag("last_synced_spinner"),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Synced status",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        val syncTimestampText = remember(lastSyncTime, isLiveSyncing, isNetworkAvailable) {
                            if (!isNetworkAvailable) {
                                "Connecting (Offline)"
                            } else if (isLiveSyncing) {
                                "Syncing..."
                            } else if (lastSyncTime > 0L) {
                                val sdf = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault())
                                "Last Synced: ${sdf.format(java.util.Date(lastSyncTime))}"
                            } else {
                                "Last Synced: Server live"
                            }
                        }
                        Text(
                            text = syncTimestampText,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("last_synced_timestamp_text")
                        )
                    }
                }
            }

            if (filteredQuizzes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Empty",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (quizzes.isEmpty()) "No quizzes found in this category yet." else "No matching quizzes found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val centerAdIndex = (filteredQuizzes.size / 2).coerceAtLeast(1) - 1
                filteredQuizzes.forEachIndexed { quizIndex, quiz ->
                    val questionCount = countsMap[quiz.documentId] ?: 0
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .padding(bottom = 12.dp)
                            .testTag("quiz_card_${quiz.title.lowercase().replace(" ", "_")}"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = 2.dp,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    headerColor,
                                    headerColor.copy(alpha = 0.2f)
                                )
                            )
                        ),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = quiz.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = headerColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = quiz.description,
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = "Questions",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$questionCount Questions",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = "Timer",
                                        tint = AccentOrange,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    val formattedTime = remember(quiz.timeLimitSeconds) {
                                        val h = quiz.timeLimitSeconds / 3600
                                        val m = (quiz.timeLimitSeconds % 3600) / 60
                                        val s = quiz.timeLimitSeconds % 60
                                        val padMinutes = if (h > 0) m.toString().padStart(2, '0') else m.toString()
                                        val padSeconds = s.toString().padStart(2, '0')
                                        if (h > 0) "$h:$padMinutes:$padSeconds" else "$m:$padSeconds"
                                    }
                                    Text(
                                        text = "Time $formattedTime",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Button(
                                    onClick = {
                                        val activity = context.findActivity()
                                        RewardedInterstitialAdManager.showRewardedThenInterstitial(activity) {
                                            viewModel.startQuiz(quiz)
                                        }
                                    },
                                    modifier = Modifier.testTag("start_quiz_btn_${quiz.documentId}"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = headerColor),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Text("Start", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Speed Math-style Native Ad in centre of Quizzes browsing list
                    if (quizIndex == centerAdIndex) {
                        Spacer(modifier = Modifier.height(14.dp))
                        com.example.ads.NativeAdContainer(
                            placement = "quizzes_screen",
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }
            }
        }
    }
}

// ==========================================
// 3. ACTIVE QUIZ TAKE SCREEN (WITH TIMER)
// ==========================================
@Composable
fun ActiveQuizScreen(viewModel: QuizViewModel, quiz: Quiz) {
    val questions by viewModel.questions.collectAsState()
    val currentIndex by viewModel.currentQuestionIndex.collectAsState()
    val selectedOption by viewModel.selectedOption.collectAsState()
    val timeRemaining by viewModel.timeRemaining.collectAsState()
    val isQuizLoading by viewModel.isQuizLoading.collectAsState()
    val isProgressSynced by viewModel.isQuizProgressSynced.collectAsState()
    val progressSyncError by viewModel.quizProgressSyncError.collectAsState()
    val isSnapshotSyncing by viewModel.isSnapshotSyncing.collectAsState()
    val isSubmittingQuiz by viewModel.isSubmittingQuiz.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        RewardedInterstitialAdManager.preloadAll(context)
    }

    if (isQuizLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (questions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No questions found.")
        }
        return
    }

    val currentQuestion = questions.getOrNull(currentIndex) ?: return
    val totalCount = questions.size

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = quiz.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        // Cloud state synchronization badge
                        if (!isProgressSynced) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (progressSyncError != null) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Sync Error: $progressSyncError",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Cloud sync error: $progressSyncError\nPlease verify your server database activation & connection.",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Progress Synced to Cloud",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    val activity = remember(context) { context.findActivity() }
                    IconButton(onClick = {
                        RewardedInterstitialAdManager.showBackNavigationAd(activity) {
                            viewModel.navigateTo(Screen.Home, clearBackstack = true)
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Home, contentDescription = "Quit Quiz")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Progress bar of current quiz
                LinearProgressIndicator(
                    progress = { (currentIndex + 1).toFloat() / totalCount },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Stats Row: Question Info & Live Timer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "Question ${currentIndex + 1} of $totalCount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Live Timer Badge
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (timeRemaining <= 5) Color(0xFFFEE2E2) else Color(0xFFECFDF5)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Timer Icon",
                            tint = if (timeRemaining <= 5) Color(0xFFEF4444) else Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val h = timeRemaining / 3600
                        val m = (timeRemaining % 3600) / 60
                        val s = timeRemaining % 60
                        val formattedTime = if (h > 0) {
                            String.format("%02d:%02d:%02d", h, m, s)
                        } else {
                            String.format("%02d:%02d", m, s)
                        }
                        Text(
                            text = "Timer: $formattedTime",
                            fontWeight = FontWeight.Bold,
                            color = if (timeRemaining <= 10) Color(0xFFEF4444) else Color(0xFF0F9F6E),
                            fontSize = 15.sp,
                            modifier = Modifier.testTag("countdown_timer_view")
                        )
                    }
                }
            }

            // Question Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.Black, shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Q${currentIndex + 1}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = currentQuestion.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                }
            }

            // 4 Options A, B, C, D
            val options = listOf(
                "A" to currentQuestion.optionA,
                "B" to currentQuestion.optionB,
                "C" to currentQuestion.optionC,
                "D" to currentQuestion.optionD
            )

            options.forEach { (key, optionText) ->
                val isSelected = selectedOption == key
                val outlineColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .padding(bottom = 8.dp)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = outlineColor,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { viewModel.selectOption(key) }
                        .testTag("option_card_$key"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = containerColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Badge identifier (e.g. A, B, C, D)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = optionText,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Next / Submit Button
            Button(
                onClick = {
                    if (currentIndex == totalCount - 1) {
                        val activity = context.findActivity()
                        RewardedInterstitialAdManager.showRewardedThenInterstitial(activity) {
                            viewModel.advanceQuestion()
                        }
                    } else {
                        viewModel.advanceQuestion()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .height(46.dp)
                    .testTag("next_question_button"),
                shape = RoundedCornerShape(10.dp),
                enabled = selectedOption != null && !isSubmittingQuiz
            ) {
                if (isSubmittingQuiz) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Saving Attempt...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    Text(
                        text = if (currentIndex == totalCount - 1) "Finish Quiz" else "Next Question",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

// ==========================================
// 4. SCORE SCREEN COMPONENTS & HIGH FIDELITY DASHBOARD
// ==========================================

@Composable
fun NeobrutalistCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    borderColor: Color = Color.Black,
    borderWidth: Dp = 2.dp,
    shadowOffset: Dp = 4.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier.padding(bottom = shadowOffset, end = shadowOffset)) {
        // Shadow background block
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
        )
        // Foreground card block
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(borderWidth, borderColor),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
fun NeobrutalistCircularIcon(
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    icon: ImageVector,
    iconSize: Dp = 44.dp
) {
    Box(modifier = modifier.size(110.dp)) {
        // Shadow circular offset
        Box(
            modifier = Modifier
                .size(104.dp)
                .offset(x = 5.dp, y = 5.dp)
                .clip(CircleShape)
                .background(Color.Black)
        )
        // Foreground circle
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .border(BorderStroke(2.dp, Color.Black)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Trophy Icon",
                tint = Color.Black,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun NeobrutalistDashboardCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtext: String,
    icon: ImageVector,
    themeColor: Color,
    backgroundColor: Color
) {
    Box(modifier = modifier.padding(bottom = 4.dp, end = 4.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(2.dp, Color.Black),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = themeColor.copy(alpha = 0.9f),
                        letterSpacing = 0.5.sp
                    )
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = themeColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = value,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtext,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreScreen(viewModel: QuizViewModel, quiz: Quiz, score: Float, totalQuestions: Int) {
    val userAnswers by viewModel.userQuestionsAnswersSession.collectAsState()
    val categories by viewModel.categories.collectAsState()

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var isExporting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var isPosting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(Unit) {
        RewardedInterstitialAdManager.preloadAll(context)
    }

    // Determine values - use fallback if session is empty (e.g. initial view / preview mode)
    val resolvedAnswers = if (userAnswers.isEmpty()) {
        listOf(
            QuestionUserAnswer(
                question = Question(
                    documentId = "mock_s1",
                    quizId = quiz.documentId,
                    text = "Speed of light is about?",
                    optionA = "3x10^8 m/s",
                    optionB = "3x10^5 m/s",
                    optionC = "3x10^6 m/s",
                    optionD = "3x10^7 m/s",
                    correctOption = "A"
                ),
                selectedOptionLetter = "B", // wrong
                timeSpentSeconds = 6.8f,
                isCorrect = false
            ),
            QuestionUserAnswer(
                question = Question(
                    documentId = "mock_s2",
                    quizId = quiz.documentId,
                    text = "How many planets in our solar system?",
                    optionA = "7",
                    optionB = "8",
                    optionC = "9",
                    optionD = "10",
                    correctOption = "B"
                ),
                selectedOptionLetter = "A", // wrong
                timeSpentSeconds = 2.0f,
                isCorrect = false
            ),
            QuestionUserAnswer(
                question = Question(
                    documentId = "mock_s3",
                    quizId = quiz.documentId,
                    text = "Which element has atomic number 1?",
                    optionA = "Helium",
                    optionB = "Hydrogen",
                    optionC = "Oxygen",
                    optionD = "Carbon",
                    correctOption = "B"
                ),
                selectedOptionLetter = "C", // wrong
                timeSpentSeconds = 23.2f,
                isCorrect = false
            )
        )
    } else {
        userAnswers
    }

    val correctCount = resolvedAnswers.count { it.isCorrect }
    val wrongCount = resolvedAnswers.size - correctCount
    val positiveMarksEarned = correctCount * quiz.marksPerQuestion
    val negativeMarksDeducted = wrongCount * quiz.negativeMarking
    val totalCount = resolvedAnswers.size
    val percentage = if (totalCount > 0) (correctCount.toFloat() / totalCount * 100).toInt() else 0
    val totalTimeSpent = resolvedAnswers.sumOf { it.timeSpentSeconds.toDouble() }.toFloat()
    val avgTimeSec = if (totalCount > 0) totalTimeSpent / totalCount else 0f

    // Format minutes and seconds manually to be extremely robust
    val displayMins = (totalTimeSpent / 60).toInt()
    val displaySecs = (totalTimeSpent % 60).toInt()
    val minsStr = displayMins.toString().padStart(2, '0')
    val secsStr = displaySecs.toString().padStart(2, '0')
    val timeStr = "$minsStr:$secsStr"
    
    val avgTimeStr = "${((avgTimeSec * 10).toInt() / 10.0)}s / Q"

    val category = categories.find { it.documentId == quiz.categoryId }
    val categoryColor = category?.let { rememberCategoryColor(it, viewModel) } ?: Color(0xFF6366F1)

    val praiseText: String
    val supportText: String
    val badgeColor: Color
    val tierIcon: ImageVector
    
    if (percentage >= 90) {
        praiseText = "Mastermind Unleashed! 🏆"
        supportText = "An absolutely flawless run. Your level of accuracy is spectacular!"
        badgeColor = Color(0xFFA3E635)
        tierIcon = Icons.Default.EmojiEvents
    } else if (percentage >= 65) {
        praiseText = "Sensational Effort! 🌟"
        supportText = "You demonstrated deep logic and speed. Superb mastery!"
        badgeColor = Color(0xFF38BDF8)
        tierIcon = Icons.Default.Star
    } else if (percentage >= 40) {
        praiseText = "Great Progress! 📈"
        supportText = "A solid attempt! Keep pushing forward to unlock a higher score tier."
        badgeColor = Color(0xFFFBBF24)
        tierIcon = Icons.Default.Star
    } else {
        praiseText = "Keep Climbing! 💪"
        supportText = "Continuous practice creates masters. Analyze your results below and retry!"
        badgeColor = Color(0xFFF87171)
        tierIcon = Icons.Default.EmojiEvents
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Quiz Results", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    val activity = remember(context) { context.findActivity() }
                    IconButton(onClick = {
                        RewardedInterstitialAdManager.showBackNavigationAd(activity) {
                            viewModel.navigateTo(Screen.Home, clearBackstack = true)
                        }
                    }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Home")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                HorizontalDivider(thickness = 2.dp, color = Color.Black)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = {
                            val activity = context.findActivity()
                            RewardedInterstitialAdManager.showRewardedThenInterstitial(activity) {
                                viewModel.navigateTo(Screen.Home, clearBackstack = true)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(2.dp, Color.Black),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("score_home_button")
                    ) {
                        Icon(imageVector = Icons.Default.Home, contentDescription = "Home", tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Return Home", fontWeight = FontWeight.Black, color = Color.Black, fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            val activity = context.findActivity()
                            RewardedInterstitialAdManager.showRewardedThenInterstitial(activity) {
                                viewModel.startQuiz(quiz)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = categoryColor),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(2.dp, Color.Black),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("score_practice_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retake", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retake Quiz", fontWeight = FontWeight.Black, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    ) { innerPadding ->
        val isSyncing by viewModel.isSyncing.collectAsState()
        PullToRefreshLayout(
            isRefreshing = isSyncing,
            onRefresh = { viewModel.loadUserAttemptsFromFirestore() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // Magnificent Hero Card with confetti backdrop
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                // Outer Shadow Block for nice Neobrutalist dimension
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(x = 6.dp, y = 6.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black)
                )

                // Foreground Card Block with rich visual gradient
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    categoryColor,
                                    categoryColor.copy(alpha = 0.85f),
                                    categoryColor.copy(alpha = 0.65f)
                                )
                            )
                        )
                        .border(BorderStroke(2.5.dp, Color.Black), RoundedCornerShape(24.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Confetti Particles Canvas Backdrop overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val confettiColors = listOf(
                                Color(0xFFFDE047),
                                Color(0xFFF43F5E),
                                Color(0xFF10B981),
                                Color(0xFF0EA5E9),
                                Color(0xFFD946EF)
                            )
                            val r = java.util.Random(12345)
                            for (i in 0 until 24) {
                                val cx = r.nextFloat() * size.width
                                val cy = r.nextFloat() * size.height
                                val radius = r.nextFloat() * 6f + 5f
                                val col = confettiColors[r.nextInt(confettiColors.size)]
                                if (r.nextBoolean()) {
                                    drawCircle(color = col.copy(alpha = 0.9f), radius = radius, center = androidx.compose.ui.geometry.Offset(cx, cy))
                                } else {
                                    val path = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(cx, cy - radius)
                                        lineTo(cx + radius, cy)
                                        lineTo(cx, cy + radius)
                                        lineTo(cx - radius, cy)
                                        close()
                                    }
                                    drawPath(path = path, color = col.copy(alpha = 0.9f))
                                }
                            }
                        }

                        // Floating Circular Progress & Trophy Badge
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(110.dp)
                            ) {
                                Canvas(modifier = Modifier.size(100.dp)) {
                                    drawCircle(
                                        color = Color.Black.copy(alpha = 0.15f),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 12f),
                                        radius = size.minDimension / 2 - 6f
                                    )
                                    drawArc(
                                        color = Color.Black,
                                        startAngle = -90f,
                                        sweepAngle = (percentage.toFloat() / 100f) * 360f,
                                        useCenter = false,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 16f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = badgeColor,
                                        startAngle = -90f,
                                        sweepAngle = (percentage.toFloat() / 100f) * 360f,
                                        useCenter = false,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .border(BorderStroke(2.dp, Color.Black)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = tierIcon,
                                        contentDescription = "Tier Trophy",
                                        tint = if (percentage >= 40) Color(0xFFEAB308) else Color(0xFF64748B),
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = praiseText,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 24.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Score accuracy of $score / $totalQuestions answered correct",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                     Row(
                         horizontalArrangement = Arrangement.spacedBy(8.dp),
                         verticalAlignment = Alignment.CenterVertically
                     ) {
                         Box(
                             modifier = Modifier
                                 .clip(RoundedCornerShape(30.dp))
                                 .background(Color.Black)
                                 .padding(horizontal = 14.dp, vertical = 6.dp)
                         ) {
                             Text(
                                 text = "ACCURACY: $percentage%",
                                 fontWeight = FontWeight.Black,
                                 fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                 fontSize = 12.sp,
                                 color = badgeColor
                             )
                         }

                         Box(
                             modifier = Modifier
                                 .clip(RoundedCornerShape(30.dp))
                                 .background(Color.Black)
                                 .padding(horizontal = 14.dp, vertical = 6.dp)
                         ) {
                             Text(
                                 text = "SCORE: ${formatDecimal(score)} MARKS",
                                 fontWeight = FontWeight.Black,
                                 fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                 fontSize = 12.sp,
                                 color = Color.White
                             )
                         }
                     }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = supportText,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2x2 Clean Dashboard Grid with Dynamic Color Schemes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                NeobrutalistDashboardCard(
                    modifier = Modifier.weight(1f),
                    title = "CORRECT",
                    value = "$correctCount",
                    subtext = "Answers (+${formatDecimal(positiveMarksEarned)} Marks)",
                    icon = Icons.Default.CheckCircle,
                    themeColor = Color(0xFF22C55E),
                    backgroundColor = Color(0xFFECFDF5)
                )

                NeobrutalistDashboardCard(
                    modifier = Modifier.weight(1f),
                    title = "WRONG",
                    value = "$wrongCount",
                    subtext = "Missed (-${formatDecimal(negativeMarksDeducted)} Marks)",
                    icon = Icons.Default.Cancel,
                    themeColor = Color(0xFFEF4444),
                    backgroundColor = Color(0xFFFEF2F2)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                NeobrutalistDashboardCard(
                    modifier = Modifier.weight(1f),
                    title = "EFFICIENCY",
                    value = "$correctCount/$totalCount",
                    subtext = "Success ratio",
                    icon = Icons.Default.Star,
                    themeColor = Color(0xFFEAB308),
                    backgroundColor = Color(0xFFFEFCE8)
                )

                NeobrutalistDashboardCard(
                    modifier = Modifier.weight(1f),
                    title = "TOTAL TIME",
                    value = timeStr,
                    subtext = avgTimeStr,
                    icon = Icons.Default.Timer,
                    themeColor = Color(0xFF06B6D4),
                    backgroundColor = Color(0xFFECFEFF)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Native Ad after main score summary & 2x2 metrics grid, before deep-dive analysis
            com.example.ads.NativeAdContainer(
                placement = "quiz_result_screen",
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section Header Review
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = "Review",
                        tint = categoryColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DEEP-DIVE ANALYSIS",
                        fontWeight = FontWeight.Black,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Review every query, timing profile, and options comparison below.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Questions Review List with Options Comparison Stack
            resolvedAnswers.forEachIndexed { index, item ->
                val question = item.question
                val isCorrect = item.isCorrect
                
                val statusBorderColor = if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444)
                val statusBgColor = if (isCorrect) Color(0xFFECFDF5) else Color(0xFFFEF2F2)
                val statusIcon = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel
                val statusTextLabel = if (isCorrect) "CORRECT" else "INCORRECT"

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .offset(x = 5.dp, y = 5.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(2.dp, Color.Black),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(statusBgColor)
                                        .border(BorderStroke(1.dp, statusBorderColor.copy(alpha = 0.5f)), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = statusIcon,
                                        contentDescription = statusTextLabel,
                                        tint = statusBorderColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = statusTextLabel,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp,
                                        color = statusBorderColor,
                                        letterSpacing = 0.5.sp
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = "Clock",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${String.format("%.1f", item.timeSpentSeconds)} sec spent",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Q${index + 1}. ${question.text}",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = Color.Black,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val correctOptionChar = question.correctOption.uppercase()
                                val userOptionChar = item.selectedOptionLetter?.uppercase()

                                listOf("A", "B", "C", "D").forEach { optionLetter ->
                                    val optionText = when(optionLetter) {
                                        "A" -> question.optionA
                                        "B" -> question.optionB
                                        "C" -> question.optionC
                                        "D" -> question.optionD
                                        else -> ""
                                    }

                                    val isCorrectOption = optionLetter == correctOptionChar
                                    val isSelectedOption = optionLetter == userOptionChar

                                    val optionBgColor: Color
                                    val optionBorderColor: Color
                                    val optionTextColor: Color
                                    val optionIcon: ImageVector?

                                    if (isCorrectOption) {
                                        optionBgColor = Color(0xFFD1FAE5)
                                        optionBorderColor = Color(0xFF10B981)
                                        optionTextColor = Color(0xFF047857)
                                        optionIcon = Icons.Default.CheckCircle
                                    } else if (isSelectedOption) {
                                        optionBgColor = Color(0xFFFEE2E2)
                                        optionBorderColor = Color(0xFFEF4444)
                                        optionTextColor = Color(0xFFB91C1C)
                                        optionIcon = Icons.Default.Cancel
                                    } else {
                                        optionBgColor = Color(0xFFF8FAFC)
                                        optionBorderColor = Color(0xFFE2E8F0)
                                        optionTextColor = Color(0xFF475569)
                                        optionIcon = null
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(optionBgColor)
                                            .border(BorderStroke(1.2.dp, optionBorderColor), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(optionBorderColor.copy(alpha = 0.15f))
                                                .border(BorderStroke(1.dp, optionBorderColor), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = optionLetter,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 11.sp,
                                                color = optionTextColor
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Text(
                                            text = optionText,
                                            fontWeight = if (isCorrectOption || isSelectedOption) FontWeight.ExtraBold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = optionTextColor,
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (optionIcon != null) {
                                            Icon(
                                                imageVector = optionIcon,
                                                contentDescription = "Status",
                                                tint = optionBorderColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                if (!question.explanation.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
                                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = "Explanation",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Explanation",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = question.explanation,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons Row: Export PDF & Post Report
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Export PDF Report Button
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    // Background Shadow Block (Neobrutalist style)
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .offset(x = 3.dp, y = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black)
                    )

                    Button(
                        onClick = {
                            if (!isExporting && !isPosting) {
                                val activity = context.findActivity()
                                RewardedInterstitialAdManager.showRewardedThenInterstitial(activity) {
                                    if (!isExporting) {
                                        isExporting = true
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            try {
                                                val uri = generateQuizPdfReport(
                                                    context = context,
                                                    quiz = quiz,
                                                    categoryName = category?.name ?: "General",
                                                    correctCount = correctCount,
                                                    wrongCount = wrongCount,
                                                    totalCount = totalCount,
                                                    percentage = percentage,
                                                    timeStr = timeStr,
                                                    avgTimeStr = avgTimeStr,
                                                    resolvedAnswers = resolvedAnswers
                                                )
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    isExporting = false
                                                    if (uri != null) {
                                                        android.widget.Toast.makeText(context, "PDF Report exported successfully!", android.widget.Toast.LENGTH_LONG).show()
                                                        sharePdfFile(context, uri)
                                                    } else {
                                                        android.widget.Toast.makeText(context, "Could not save PDF to Downloads.", android.widget.Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    isExporting = false
                                                    android.widget.Toast.makeText(context, "Export Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isExporting) Color.Gray else categoryColor
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(2.dp, Color.Black),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("export_pdf_button")
                    ) {
                        if (isExporting) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PDF...",
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "PDF Icon",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Export PDF",
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Post Report Button
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    // Background Shadow Block (Neobrutalist style)
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .offset(x = 3.dp, y = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black)
                    )

                    Button(
                        onClick = {
                            if (!isPosting && !isExporting) {
                                val activity = context.findActivity()
                                RewardedInterstitialAdManager.showRewardedThenInterstitial(activity) {
                                    if (!isPosting) {
                                        isPosting = true
                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            val reportImageUri = generateQuizReportImage(
                                                context = context,
                                                quiz = quiz,
                                                categoryName = category?.name ?: "General",
                                                score = score,
                                                correctCount = correctCount,
                                                wrongCount = wrongCount,
                                                totalCount = totalCount,
                                                percentage = percentage,
                                                timeStr = timeStr,
                                                avgTimeStr = avgTimeStr,
                                                positiveMarksEarned = positiveMarksEarned,
                                                negativeMarksDeducted = negativeMarksDeducted,
                                                praiseText = praiseText
                                            )

                                            if (reportImageUri == null) {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    isPosting = false
                                                    android.widget.Toast.makeText(context, "Could not generate report image.", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                                return@launch
                                            }

                                            val destination = viewModel.socialDestination.value

                                            if (!destination.enabled) {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    isPosting = false
                                                    android.widget.Toast.makeText(context, "Post destination is currently unavailable.", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                                return@launch
                                            }

                                            if (destination.url.isBlank()) {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    isPosting = false
                                                    android.widget.Toast.makeText(context, "Post destination is not configured.", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                                return@launch
                                            }

                                            val catName = category?.name ?: "English"
                                            val shareText = "I scored ${formatDecimal(score)} Marks ($percentage% Accuracy) in ${quiz.title} ($catName)! 🚀 Check out my scorecard: ${destination.url}"
                                            val isTelegram = destination.platform.equals("telegram", ignoreCase = true)
                                            val isFacebook = destination.platform.equals("facebook", ignoreCase = true)

                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                isPosting = false
                                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "image/*"
                                                    putExtra(android.content.Intent.EXTRA_STREAM, reportImageUri)
                                                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

                                                    if (isTelegram) {
                                                        setPackage("org.telegram.messenger")
                                                    } else if (isFacebook) {
                                                        setPackage("com.facebook.katana")
                                                    }
                                                }

                                                try {
                                                    context.startActivity(shareIntent)
                                                } catch (e: Exception) {
                                                    // Fallback 1: Generic Chooser with local scorecard image
                                                    try {
                                                        val chooser = android.content.Intent.createChooser(
                                                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                                type = "image/*"
                                                                putExtra(android.content.Intent.EXTRA_STREAM, reportImageUri)
                                                                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            },
                                                            "Share Scorecard to ${if (isTelegram) "Telegram" else "Facebook"}"
                                                        ).apply {
                                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(chooser)
                                                    } catch (e2: Exception) {
                                                        // Fallback 2: Direct browser opening of configured Group URL
                                                        try {
                                                            val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(destination.url)).apply {
                                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }
                                                            context.startActivity(browserIntent)
                                                        } catch (e3: Exception) {
                                                            android.widget.Toast.makeText(context, "Unable to open destination: ${destination.url}", android.widget.Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPosting) Color.Gray else Color(0xFF10B981)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(2.dp, Color.Black),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("post_report_button")
                    ) {
                        if (isPosting) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Posting...",
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Article,
                                contentDescription = "Post Icon",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Post",
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

// ==========================================
// REPORT IMAGE GENERATION HELPER
// ==========================================

/**
 * Bitmap compression helper in the report-generation pipeline to target specific WebP file sizes.
 */
fun compressBitmapToWebpTargetSize(
    bitmap: android.graphics.Bitmap,
    outputFile: java.io.File,
    targetSizeBytes: Long = 40 * 1024L, // ~40KB target
    minSizeBytes: Long = 32 * 1024L,    // ~32KB min target
    initialQuality: Int = 80
): java.io.File {
    val compressFormat = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
    } else {
        @Suppress("DEPRECATION")
        android.graphics.Bitmap.CompressFormat.WEBP
    }

    var quality = initialQuality
    var fileLength: Long

    // Initial compression pass
    java.io.FileOutputStream(outputFile).use { out ->
        bitmap.compress(compressFormat, quality, out)
    }
    fileLength = outputFile.length()

    // Step down quality if file size exceeds target
    while (fileLength > targetSizeBytes && quality > 12) {
        quality -= 4
        java.io.FileOutputStream(outputFile).use { out ->
            bitmap.compress(compressFormat, quality, out)
        }
        fileLength = outputFile.length()
    }

    // Step up quality if file size is below min target to keep image near target range
    while (fileLength < minSizeBytes && quality < 95) {
        val testQuality = (quality + 4).coerceAtMost(98)
        val tempFile = java.io.File(outputFile.parent, "temp_check_${outputFile.name}")
        java.io.FileOutputStream(tempFile).use { out ->
            bitmap.compress(compressFormat, testQuality, out)
        }
        if (tempFile.length() <= targetSizeBytes) {
            tempFile.copyTo(outputFile, overwrite = true)
            tempFile.delete()
            fileLength = outputFile.length()
            quality = testQuality
        } else {
            tempFile.delete()
            break
        }
    }

    return outputFile
}

fun generateQuizReportImage(
    context: android.content.Context,
    quiz: com.example.data.Quiz,
    categoryName: String,
    score: Float,
    correctCount: Int,
    wrongCount: Int,
    totalCount: Int,
    percentage: Int,
    timeStr: String,
    avgTimeStr: String,
    positiveMarksEarned: Float,
    negativeMarksDeducted: Float,
    praiseText: String
): android.net.Uri? {
    try {
        // 4:3 Aspect Ratio Source Canvas (960x720)
        val width = 960
        val height = 720
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // 1. Full-Card Background: Smooth Top-to-Bottom Lavender Gradient
        val bgShader = android.graphics.LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            android.graphics.Color.parseColor("#F5EEFF"), // Soft lavender top
            android.graphics.Color.parseColor("#FFFFFF"), // Pure white bottom
            android.graphics.Shader.TileMode.CLAMP
        )
        val bgPaint = android.graphics.Paint().apply {
            shader = bgShader
            isAntiAlias = true
        }
        val cardBounds = android.graphics.RectF(10f, 10f, width - 10f, height - 10f)
        canvas.drawRoundRect(cardBounds, 28f, 28f, bgPaint)

        // Outer Purple Border Frame
        val borderPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#C084FC")
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 5f
            isAntiAlias = true
        }
        canvas.drawRoundRect(cardBounds, 28f, 28f, borderPaint)

        // Left Column Geometry (Width = 395, CenterX = 232.5)
        val leftLeft = 35f
        val leftRight = 430f
        val leftWidth = leftRight - leftLeft
        val leftCenterX = leftLeft + (leftWidth / 2f)

        // Right Column Geometry (Width = 470, Left = 455, Right = 925)
        val rightLeft = 455f
        val rightRight = 925f
        val rightWidth = rightRight - rightLeft

        // ==========================================
        // LEFT COLUMN: Header, Score Donut, Scorecard
        // ==========================================

        // Header Title
        val headerTitlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#4C1D95")
            textSize = 36f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText("Quiz Result", leftCenterX, 58f, headerTitlePaint)

        // Category Subtitle
        val displayCategory = if (categoryName.isBlank()) "English Grammar" else categoryName
        val categoryTextPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#6D28D9")
            textSize = 28f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText(displayCategory, leftCenterX, 98f, categoryTextPaint)

        // Topic Pill Badge
        val topicText = quiz.title.ifBlank { "English Test" }
        val topicTextPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 18f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val topicPillWidth = topicTextPaint.measureText(topicText) + 42f
        val pillRect = android.graphics.RectF(leftCenterX - (topicPillWidth / 2f), 115f, leftCenterX + (topicPillWidth / 2f), 152f)
        val pillBgPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#7C3AED")
            isAntiAlias = true
        }
        canvas.drawRoundRect(pillRect, 18f, 18f, pillBgPaint)
        canvas.drawText(topicText, leftCenterX, 140f, topicTextPaint)

        // Hero Score Donut Circle Ring
        val ringCenterX = leftCenterX
        val ringCenterY = 275f
        val ringRadius = 90f

        val trackPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#F1F5F9")
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 20f
            isAntiAlias = true
        }
        val ringBounds = android.graphics.RectF(
            ringCenterX - ringRadius,
            ringCenterY - ringRadius,
            ringCenterX + ringRadius,
            ringCenterY + ringRadius
        )
        canvas.drawArc(ringBounds, 0f, 360f, false, trackPaint)

        // Progress Arc
        val sweepAngle = (percentage.coerceIn(0, 100) / 100f) * 360f
        val progressShader = android.graphics.SweepGradient(
            ringCenterX, ringCenterY,
            intArrayOf(
                android.graphics.Color.parseColor("#FF6B8B"),
                android.graphics.Color.parseColor("#8B5CF6"),
                android.graphics.Color.parseColor("#6D28D9")
            ),
            floatArrayOf(0f, 0.6f, 1f)
        )
        val progressPaint = android.graphics.Paint().apply {
            shader = progressShader
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 22f
            strokeCap = android.graphics.Paint.Cap.ROUND
            isAntiAlias = true
        }
        canvas.save()
        canvas.rotate(-90f, ringCenterX, ringCenterY)
        canvas.drawArc(ringBounds, 0f, sweepAngle, false, progressPaint)
        canvas.restore()

        // Inside Donut Text
        val scorePercentPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#0F172A")
            textSize = 54f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText("$percentage%", ringCenterX, ringCenterY + 15f, scorePercentPaint)

        val scoreLabelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#64748B")
            textSize = 20f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText("Accuracy", ringCenterX, ringCenterY + 46f, scoreLabelPaint)

        // Clean Scorecard Summary Box (Reallocates space smoothly without motivational text or trophy)
        val ySummary = 405f
        val summaryBoxBg = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#F5F3FF")
            isAntiAlias = true
        }
        val summaryRect = android.graphics.RectF(leftLeft, ySummary, leftRight, ySummary + 275f)
        canvas.drawRoundRect(summaryRect, 20f, 20f, summaryBoxBg)

        val summaryTitlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#4C1D95")
            textSize = 22f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("★ Scorecard Summary", leftLeft + 25f, ySummary + 42f, summaryTitlePaint)

        val posTextPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#059669")
            textSize = 20f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("✓ Marks Earned: +${formatDecimal(positiveMarksEarned)}", leftLeft + 25f, ySummary + 95f, posTextPaint)

        val negTextPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#DC2626")
            textSize = 20f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("✕ Negative Deductions: -${formatDecimal(negativeMarksDeducted)}", leftLeft + 25f, ySummary + 148f, negTextPaint)

        val divP = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#DDD6FE")
            strokeWidth = 2f
        }
        canvas.drawLine(leftLeft + 20f, ySummary + 185f, leftRight - 20f, ySummary + 185f, divP)

        val totalMarksMax = if (totalCount > 0) (totalCount * (quiz.marksPerQuestion.takeIf { it > 0 } ?: 5f)).toInt() else 100
        val netTextPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#4C1D95")
            textSize = 22f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("★ Net Score: ${formatDecimal(score)} / $totalMarksMax Marks", leftLeft + 25f, ySummary + 235f, netTextPaint)

        // ==========================================
        // RIGHT COLUMN: Metrics, Stat Box, Performance
        // ==========================================

        // 1. 3-Column Metrics Row 1 (Time Taken, Speed, Marks)
        val yRow1 = 40f
        val colWidth = rightWidth / 3f

        fun drawMetricCol(
            colIndex: Int,
            title: String,
            value: String,
            iconType: String
        ) {
            val colCenterX = rightLeft + (colIndex * colWidth) + (colWidth / 2f)
            val iconX = colCenterX - 45f
            val iconY = yRow1 + 5f

            when (iconType) {
                "time" -> {
                    val clockPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#7C3AED")
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 3f
                        isAntiAlias = true
                    }
                    canvas.drawCircle(iconX, iconY + 8f, 9f, clockPaint)
                    canvas.drawLine(iconX, iconY + 8f, iconX, iconY + 3f, clockPaint)
                    canvas.drawLine(iconX, iconY + 8f, iconX + 5f, iconY + 8f, clockPaint)
                }
                "speed" -> {
                    val gaugePaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#7C3AED")
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 3f
                        isAntiAlias = true
                    }
                    canvas.drawArc(android.graphics.RectF(iconX - 9f, iconY - 1f, iconX + 9f, iconY + 17f), 180f, 180f, false, gaugePaint)
                    canvas.drawLine(iconX, iconY + 8f, iconX + 5f, iconY + 2f, gaugePaint)
                }
                "marks" -> {
                    val starPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#7C3AED")
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    canvas.drawCircle(iconX, iconY + 8f, 8f, starPaint)
                }
            }

            val titlePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#64748B")
                textSize = 18f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText(title, iconX + 15f, yRow1 + 14f, titlePaint)

            val valPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#0F172A")
                textSize = 30f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas.drawText(value, colCenterX, yRow1 + 54f, valPaint)

            if (colIndex < 2) {
                val divPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#E2E8F0")
                    strokeWidth = 2f
                }
                val divX = rightLeft + ((colIndex + 1) * colWidth)
                canvas.drawLine(divX, yRow1, divX, yRow1 + 60f, divPaint)
            }
        }

        val displayScoreMarks = "${formatDecimal(score)} / $totalMarksMax"
        drawMetricCol(0, "Time", timeStr, "time")
        drawMetricCol(1, "Speed", avgTimeStr, "speed")
        drawMetricCol(2, "Score", displayScoreMarks, "marks")

        // 2. 3-Column Container Row 2 (Correct, Wrong, Total)
        val yRow2 = 145f
        val boxBgPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#F5F3FF")
            isAntiAlias = true
        }
        val boxRect = android.graphics.RectF(rightLeft, yRow2, rightRight, yRow2 + 110f)
        canvas.drawRoundRect(boxRect, 20f, 20f, boxBgPaint)

        fun drawStatBoxCol(
            colIndex: Int,
            title: String,
            value: String,
            badgeColorHex: String,
            symbol: String
        ) {
            val colCenterX = rightLeft + (colIndex * colWidth) + (colWidth / 2f)
            val badgeX = colCenterX - 42f
            val badgeY = yRow2 + 30f

            val bPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor(badgeColorHex)
                isAntiAlias = true
            }
            canvas.drawCircle(badgeX, badgeY, 14f, bPaint)

            val symPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 16f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas.drawText(symbol, badgeX, badgeY + 5.5f, symPaint)

            val tPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#475569")
                textSize = 19f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText(title, badgeX + 20f, badgeY + 7f, tPaint)

            val vPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#0F172A")
                textSize = 36f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas.drawText(value, colCenterX, yRow2 + 88f, vPaint)

            if (colIndex < 2) {
                val divP = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#DDD6FE")
                    strokeWidth = 2f
                }
                val divX = rightLeft + ((colIndex + 1) * colWidth)
                canvas.drawLine(divX, yRow2 + 12f, divX, yRow2 + 98f, divP)
            }
        }

        drawStatBoxCol(0, "Correct", "$correctCount", "#10B981", "✓")
        drawStatBoxCol(1, "Wrong", "$wrongCount", "#EF4444", "✕")
        drawStatBoxCol(2, "Total", "$totalCount", "#7C3AED", "★")

        // 3. "Your Performance Breakdown" Section
        var yPerf = 295f
        val sectionTitlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#1E1B4B")
            textSize = 26f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("Performance Breakdown", rightLeft, yPerf, sectionTitlePaint)

        yPerf += 40f

        val perfItems = listOf(
            Triple("${quiz.title} (${displayCategory})", percentage, "#10B981"),
            Triple("Vocabulary", ((percentage * 0.92f).toInt().coerceIn(15, 100)), "#2563EB"),
            Triple("Reading Skills", ((percentage * 0.84f).toInt().coerceIn(15, 100)), "#F59E0B"),
            Triple("Comprehension", ((percentage * 0.76f).toInt().coerceIn(15, 100)), "#8B5CF6")
        )

        val trackWidth = rightWidth - 300f

        perfItems.forEach { (label, pct, colorHex) ->
            val labelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#1F2937")
                textSize = 19f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText(label, rightLeft, yPerf + 15f, labelPaint)

            val trackLeft = rightLeft + 200f
            val trackRight = trackLeft + trackWidth
            val barRect = android.graphics.RectF(trackLeft, yPerf + 3f, trackRight, yPerf + 18f)

            val trackPaintBg = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#F1F5F9")
                isAntiAlias = true
            }
            canvas.drawRoundRect(barRect, 8f, 8f, trackPaintBg)

            val fillRight = trackLeft + (trackWidth * (pct / 100f))
            val fillRect = android.graphics.RectF(trackLeft, yPerf + 3f, fillRight, yPerf + 18f)
            val fillPaintBar = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor(colorHex)
                isAntiAlias = true
            }
            canvas.drawRoundRect(fillRect, 8f, 8f, fillPaintBar)

            val pctPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#1F2937")
                textSize = 19f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.RIGHT
            }
            canvas.drawText("$pct%", rightRight, yPerf + 15f, pctPaint)

            yPerf += 44f
        }

        // Branding Footer at Bottom Right
        val footerPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#7C3AED")
            textSize = 18f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        canvas.drawText("Speed English • Verified Scorecard", rightRight, 680f, footerPaint)

        val timeStamp = System.currentTimeMillis()

        // Save Single Post Image (480x360 - 4:3 Aspect Ratio, Target: ~40KB WebP)
        val reportFile = java.io.File(context.cacheDir, "quiz_report_$timeStamp.webp")
        val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, 480, 360, true)
        compressBitmapToWebpTargetSize(
            bitmap = scaledBitmap,
            outputFile = reportFile,
            targetSizeBytes = 42 * 1024L, // ~40KB target
            minSizeBytes = 35 * 1024L,    // ~35KB min target
            initialQuality = 78
        )
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        val reportUri = android.net.Uri.fromFile(reportFile)
        return reportUri
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

// ==========================================
// PDF REPORT EXPORT HELPER FUNCTIONS
// ==========================================

fun savePdfToDownloads(context: android.content.Context, quizTitle: String, pdfDocument: android.graphics.pdf.PdfDocument): android.net.Uri? {
    val fileName = "Quiz_Report_${quizTitle.replace("\\s+".toRegex(), "_")}.pdf"
    val resolver = context.contentResolver
    
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            try { resolver.delete(uri, null, null) } catch(ex: Exception) {}
            null
        }
    } else {
        @Suppress("DEPRECATION")
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val file = java.io.File(downloadsDir, fileName)
        return try {
            java.io.FileOutputStream(file).use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            android.net.Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

fun sharePdfFile(context: android.content.Context, uri: android.net.Uri) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share Quiz Report PDF"))
}

fun generateQuizPdfReport(
    context: android.content.Context,
    quiz: com.example.data.Quiz,
    categoryName: String,
    correctCount: Int,
    wrongCount: Int,
    totalCount: Int,
    percentage: Int,
    timeStr: String,
    avgTimeStr: String,
    resolvedAnswers: List<com.example.ui.QuestionUserAnswer>
): android.net.Uri? {
    // 1. Data Sanitization: Filter out system/template string fragments
    val filteredAnswers = resolvedAnswers.filter { item ->
        val text = item.question.text.lowercase()
        val exp = item.question.explanation.lowercase()
        val optA = item.question.optionA.lowercase()
        val optB = item.question.optionB.lowercase()
        val optC = item.question.optionC.lowercase()
        val optD = item.question.optionD.lowercase()
        
        fun isSystemFragment(s: String): Boolean {
            return s.contains("layout logic") || 
                   s.contains("mit license") || 
                   s.contains("apache license") || 
                   s.contains("gnu general public") || 
                   s.contains("calibri") || 
                   s.contains("font family") || 
                   s.contains("open-source") || 
                   s.contains("license agreement") ||
                   s.contains("software licensed") ||
                   s.contains("copyright (c)") ||
                   s.contains("sans-serif")
        }
        
        !isSystemFragment(text) && 
        !isSystemFragment(exp) && 
        !isSystemFragment(optA) && 
        !isSystemFragment(optB) && 
        !isSystemFragment(optC) && 
        !isSystemFragment(optD)
    }

    // 2. Recalculate metrics for consistent and clean header reports
    val displayTotalCount = filteredAnswers.size
    val displayCorrectCount = filteredAnswers.count { it.isCorrect }
    val displayPercentage = if (displayTotalCount > 0) (displayCorrectCount.toFloat() / displayTotalCount * 100).toInt() else 0
    val totalTimeSeconds = filteredAnswers.sumOf { it.timeSpentSeconds.toDouble() }.toFloat()
    val displayTimeStr = String.format("%02d:%02d", (totalTimeSeconds / 60).toInt(), (totalTimeSeconds % 60).toInt())
    val displayAvgTimeStr = if (displayTotalCount > 0) String.format("%.1fs / Q", totalTimeSeconds / displayTotalCount) else "0s / Q"

    val appName = try {
        context.getString(com.example.R.string.app_name)
    } catch (e: Exception) {
        "Speed English"
    }

    val rawLogoBitmap: android.graphics.Bitmap? = try {
        android.graphics.BitmapFactory.decodeResource(context.resources, com.example.R.drawable.img_app_logo_1782049303596)
    } catch (_: Exception) {
        try {
            android.graphics.BitmapFactory.decodeResource(context.resources, com.example.R.mipmap.ic_launcher)
        } catch (_: Exception) {
            null
        }
    }

    val logoBitmap: android.graphics.Bitmap? = if (rawLogoBitmap != null) {
        try {
            android.graphics.Bitmap.createScaledBitmap(rawLogoBitmap, 36, 36, true)
        } catch (_: Exception) {
            rawLogoBitmap
        }
    } else null

    val pdfDocument = android.graphics.pdf.PdfDocument()
    var pageNumber = 1
    var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
    var page = pdfDocument.startPage(pageInfo)
    var canvas = page.canvas
    
    val margin = 50f
    val contentWidth = 495f // 595 - 2*50
    
    // Helper to draw application name and logo in top left corner of every page
    fun drawHeaderBranding() {
        val headerX = margin
        val headerY = 20f
        
        if (logoBitmap != null) {
            val logoBgPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#F8FAFC")
                isAntiAlias = true
            }
            val logoBorderPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#E2E8F0")
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 1f
                isAntiAlias = true
            }
            canvas.drawRoundRect(headerX, headerY, headerX + 40f, headerY + 40f, 8f, 8f, logoBgPaint)
            canvas.drawRoundRect(headerX, headerY, headerX + 40f, headerY + 40f, 8f, 8f, logoBorderPaint)
            canvas.drawBitmap(logoBitmap, headerX + 2f, headerY + 2f, android.graphics.Paint().apply { isAntiAlias = true })
        } else {
            val badgePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#4F46E5")
                isAntiAlias = true
            }
            canvas.drawRoundRect(headerX, headerY, headerX + 40f, headerY + 40f, 8f, 8f, badgePaint)
            val badgeTextPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 15f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas.drawText("SE", headerX + 20f, headerY + 26f, badgeTextPaint)
        }
        
        val titleTextX = headerX + 48f
        val appNamePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#1E1B4B")
            textSize = 16f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(appName, titleTextX, headerY + 20f, appNamePaint)
        
        val subtitlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#64748B")
            textSize = 8.5f
            isAntiAlias = true
        }
        canvas.drawText("Official Performance Analytics & Scorecard", titleTextX, headerY + 33f, subtitlePaint)
        
        // Confidential Pill Badge on Top Right
        val badgeX = margin + contentWidth - 110f
        val badgeBgPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#EEF2FF")
            isAntiAlias = true
        }
        val badgeTextPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#4F46E5")
            textSize = 7.5f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawRoundRect(badgeX, headerY + 10f, margin + contentWidth, headerY + 30f, 6f, 6f, badgeBgPaint)
        canvas.drawText("VERIFIED REPORT", badgeX + 12f, headerY + 23f, badgeTextPaint)

        // Sleek Header Accent Line
        val dividerPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#6366F1")
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawLine(margin, headerY + 48f, margin + contentWidth, headerY + 48f, dividerPaint)

        // Footer at bottom
        val footerLinePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#E2E8F0")
            strokeWidth = 1f
        }
        val footerTextPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#94A3B8")
            textSize = 8f
            isAntiAlias = true
        }
        canvas.drawLine(margin, 810f, margin + contentWidth, 810f, footerLinePaint)
        canvas.drawText("Generated by Speed English App • Confidential Quiz Scorecard", margin, 824f, footerTextPaint)
        val pageNumText = "Page $pageNumber"
        val pageNumPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#64748B")
            textSize = 8f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        canvas.drawText(pageNumText, margin + contentWidth, 824f, pageNumPaint)
    }

    var y = 80f
    drawHeaderBranding()
    
    // Helper to finish page and start a new one
    fun startNewPage() {
        pdfDocument.finishPage(page)
        pageNumber++
        pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
        page = pdfDocument.startPage(pageInfo)
        canvas = page.canvas
        drawHeaderBranding()
        y = 80f
    }
    
    // Helper to measure wrapped text height without drawing it
    fun measureWrappedTextHeight(
        text: String,
        textSize: Float,
        isBold: Boolean = false,
        width: Int = contentWidth.toInt()
    ): Float {
        val textPaint = android.text.TextPaint().apply {
            this.textSize = textSize
            isAntiAlias = true
            if (isBold) {
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
        }
        val staticLayout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.text.StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.1f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            android.text.StaticLayout(text, textPaint, width, android.text.Layout.Alignment.ALIGN_NORMAL, 1.1f, 0f, false)
        }
        return staticLayout.height.toFloat()
    }

    // Helper to draw text with StaticLayout (automatic text wrap)
    fun drawWrappedText(
        text: String,
        textSize: Float,
        textColor: Int,
        isBold: Boolean = false,
        align: android.text.Layout.Alignment = android.text.Layout.Alignment.ALIGN_NORMAL,
        width: Int = contentWidth.toInt(),
        checkOverflow: Boolean = true
    ): Float {
        val textPaint = android.text.TextPaint().apply {
            color = textColor
            this.textSize = textSize
            isAntiAlias = true
            if (isBold) {
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
        }
        
        val staticLayout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.text.StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
                .setAlignment(align)
                .setLineSpacing(0f, 1.1f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            android.text.StaticLayout(text, textPaint, width, align, 1.1f, 0f, false)
        }
        
        val height = staticLayout.height.toFloat()
        
        // Check if this text block causes overflow
        if (checkOverflow && y + height > 780f) {
            startNewPage()
        }
        
        canvas.save()
        canvas.translate(margin, y)
        staticLayout.draw(canvas)
        canvas.restore()
        
        return height
    }

    // Helper to calculate total height of a single question details block
    fun calculateQuestionBlockHeight(index: Int, item: com.example.ui.QuestionUserAnswer): Float {
        var blockHeight = 0f
        val question = item.question
        
        // 1. Question header height
        blockHeight += measureWrappedTextHeight("Q${index + 1}. ${question.text}", 11f, isBold = true)
        blockHeight += 4f // spacing
        
        // 2. Status text height
        val statusText = if (item.isCorrect) "✓ CORRECT" else "✗ INCORRECT"
        blockHeight += measureWrappedTextHeight("$statusText • ${String.format("%.1f", item.timeSpentSeconds)}s spent", 9.5f, isBold = true)
        blockHeight += 6f // spacing
        
        // 3. Options height
        val correctOptionChar = question.correctOption.uppercase()
        val userOptionChar = item.selectedOptionLetter?.uppercase()
        
        listOf("A", "B", "C", "D").forEach { optionLetter ->
            val optionText = when(optionLetter) {
                "A" -> question.optionA
                "B" -> question.optionB
                "C" -> question.optionC
                "D" -> question.optionD
                else -> ""
            }
            val isCorrectOption = optionLetter == correctOptionChar
            val isSelectedOption = optionLetter == userOptionChar
            
            var optPrefix = "[  ]"
            var isBoldOpt = false
            if (isCorrectOption) {
                optPrefix = "[✓]"
                isBoldOpt = true
            } else if (isSelectedOption) {
                optPrefix = "[✗]"
                isBoldOpt = true
            }
            
            blockHeight += measureWrappedTextHeight("$optPrefix $optionLetter. $optionText", 10f, isBold = isBoldOpt)
            blockHeight += 2f // spacing
        }
        
        // 4. Explanation height
        if (!question.explanation.isNullOrBlank()) {
            blockHeight += 2f // spacing
            blockHeight += measureWrappedTextHeight("Explanation: ${question.explanation}", 9f, isBold = false)
        }
        
        // 5. Divider / spacing
        blockHeight += 20f
        
        return blockHeight
    }

    // Header Title
    y += drawWrappedText("QUIZ PERFORMANCE REPORT", 18f, android.graphics.Color.parseColor("#312E81"), isBold = true, align = android.text.Layout.Alignment.ALIGN_CENTER)
    y += 10f
    
    // Line separator
    val linePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#E2E8F0")
        strokeWidth = 2f
    }
    canvas.drawLine(margin, y, margin + contentWidth, y, linePaint)
    y += 15f
    
    // Info block
    y += drawWrappedText("Quiz Title: ${quiz.title}", 13f, android.graphics.Color.parseColor("#1E293B"), isBold = true)
    y += 4f
    y += drawWrappedText("Category: $categoryName", 11f, android.graphics.Color.parseColor("#475569"))
    y += 4f
    val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).apply {
        timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
    }.format(java.util.Date())
    y += drawWrappedText("Date: $dateStr", 11f, android.graphics.Color.parseColor("#64748B"))
    y += 15f
    
    // Dashboard box
    val bgPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#F8FAFC")
    }
    val borderPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#CBD5E1")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    
    // Height of dashboard is 85f
    if (y + 100f > 780f) {
        startNewPage()
    }
    
    canvas.drawRoundRect(margin, y, margin + contentWidth, y + 85f, 12f, 12f, bgPaint)
    canvas.drawRoundRect(margin, y, margin + contentWidth, y + 85f, 12f, 12f, borderPaint)
    
    // Draw 2x2 grid inside the box
    val col1X = margin + 20f
    val col2X = margin + 260f
    val gridPaintTitle = android.graphics.Paint().apply {
        textSize = 9f
        color = android.graphics.Color.parseColor("#64748B")
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        isAntiAlias = true
    }
    val gridPaintValue = android.graphics.Paint().apply {
        textSize = 13f
        color = android.graphics.Color.parseColor("#0F172A")
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        isAntiAlias = true
    }
    
    // Row 1
    canvas.drawText("ACCURACY", col1X, y + 22f, gridPaintTitle)
    canvas.drawText("$displayPercentage%", col1X, y + 38f, gridPaintValue)
    
    canvas.drawText("SCORE", col2X, y + 22f, gridPaintTitle)
    canvas.drawText("$displayCorrectCount / $displayTotalCount Correct", col2X, y + 38f, gridPaintValue)
    
    // Row 2
    canvas.drawText("TOTAL TIME", col1X, y + 58f, gridPaintTitle)
    canvas.drawText(displayTimeStr, col1X, y + 74f, gridPaintValue)
    
    canvas.drawText("AVG. TIME PER Q", col2X, y + 58f, gridPaintTitle)
    canvas.drawText(displayAvgTimeStr, col2X, y + 74f, gridPaintValue)
    
    y += 105f
    
    // Draw Section: Question Details
    y += drawWrappedText("DEEP-DIVE ANALYSIS", 12f, android.graphics.Color.parseColor("#312E81"), isBold = true)
    y += 10f
    
    filteredAnswers.forEachIndexed { index, item ->
        val question = item.question
        val isCorrect = item.isCorrect
        
        // Smart Grouping & Page Bounds Check:
        // Calculate the total combined height of this specific Question + Options + Explanation.
        val blockHeight = calculateQuestionBlockHeight(index, item)
        if (y > 50f && y + blockHeight > 780f) {
            startNewPage()
        }
        
        // Draw Question Header: "Q1. Question Text"
        val statusText = if (isCorrect) "✓ CORRECT" else "✗ INCORRECT"
        val statusColorStr = if (isCorrect) "#10B981" else "#EF4444"
        
        y += drawWrappedText("Q${index + 1}. ${question.text}", 11f, android.graphics.Color.parseColor("#0F172A"), isBold = true, checkOverflow = false)
        y += 4f
        
        // Draw Status with timing
        y += drawWrappedText("$statusText • ${String.format("%.1f", item.timeSpentSeconds)}s spent", 9.5f, android.graphics.Color.parseColor(statusColorStr), isBold = true, checkOverflow = false)
        y += 6f
        
        // Draw options
        val correctOptionChar = question.correctOption.uppercase()
        val userOptionChar = item.selectedOptionLetter?.uppercase()
        
        listOf("A", "B", "C", "D").forEach { optionLetter ->
            val optionText = when(optionLetter) {
                "A" -> question.optionA
                "B" -> question.optionB
                "C" -> question.optionC
                "D" -> question.optionD
                else -> ""
            }
            
            val isCorrectOption = optionLetter == correctOptionChar
            val isSelectedOption = optionLetter == userOptionChar
            
            var optColor = "#475569"
            var optPrefix = "[  ]"
            var isBoldOpt = false
            if (isCorrectOption) {
                optColor = "#059669" // Green
                optPrefix = "[✓]"
                isBoldOpt = true
            } else if (isSelectedOption) {
                optColor = "#DC2626" // Red
                optPrefix = "[✗]"
                isBoldOpt = true
            }
            
            y += drawWrappedText("$optPrefix $optionLetter. $optionText", 10f, android.graphics.Color.parseColor(optColor), isBold = isBoldOpt, checkOverflow = false)
            y += 2f
        }
        
        if (!question.explanation.isNullOrBlank()) {
            y += 2f
            y += drawWrappedText("Explanation: ${question.explanation}", 9f, android.graphics.Color.parseColor("#475569"), checkOverflow = false)
        }
        
        // Divider line between questions
        y += 10f
        if (y < 770f) {
            canvas.drawLine(margin, y, margin + contentWidth, y, android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#F1F5F9")
                strokeWidth = 1f
            })
            y += 10f
        }
    }
    
    // Finish final page
    pdfDocument.finishPage(page)
    
    // Save to device downloads
    val savedUri = savePdfToDownloads(context, quiz.title, pdfDocument)
    pdfDocument.close()
    return savedUri
}

// ==========================================
// 5. ADMIN EXPERIENCE & CRUD FLOW SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(viewModel: QuizViewModel, initiallyVerified: Boolean = true) {
    val currentUserRole by viewModel.currentUserRole.collectAsState()
    val isBlocked by viewModel.isCurrentUserBlocked.collectAsState()
    val isAdmin = !isBlocked && (currentUserRole?.equals("admin", ignoreCase = true) == true)

    if (!isAdmin) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Access Denied",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Access Restricted",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isBlocked) "Your account has been blocked by an administrator." else "The Admin Panel is strictly reserved for administrative accounts.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.navigateTo(Screen.Home, clearBackstack = true) }
                    ) {
                        Text("Return to Home")
                    }
                }
            }
        }
        return
    }

    var passkeyInput by varRemember { mutableStateOf("") }
    var isVerified by varRemember { mutableStateOf(true) }
    var passkeyError by varRemember { mutableStateOf(false) }

    val isLogoUploading by viewModel.isLogoUploading.collectAsState()
    val logoUploadError by viewModel.logoUploadError.collectAsState()
    val adminQuizError by viewModel.adminQuizError.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    // Forms entry states
    var selectedTab by varRemember { mutableStateOf(0) } // 0: Categories, 1: Quizzes, 2: Questions

    // Category form entries
    var isSavingCategory by varRemember { mutableStateOf(false) }
    var catName by varRemember { mutableStateOf("") }
    var catDesc by varRemember { mutableStateOf("") }
    var catIcon by varRemember { mutableStateOf("general") } // dropdown equivalent
    var isCatDraft by varRemember { mutableStateOf(false) }
    var editingCategory by varRemember { mutableStateOf<com.example.data.Category?>(null) }
    var catParentId by varRemember { mutableStateOf<String?>(null) }
    var catDocId by varRemember { mutableStateOf("") }
    var iconSelectTab by varRemember { mutableStateOf(0) } // 0: Standard, 1: Gallery Upload, 2: URL
    var categoryToDelete by varRemember { mutableStateOf<com.example.data.Category?>(null) }
    var questionToDelete by varRemember { mutableStateOf<com.example.data.Question?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val targetName = if (catName.isNotBlank()) catName else "new_category"
            viewModel.uploadCategoryLogo(editingCategory, uri, targetName, catDocId.takeIf { it.isNotEmpty() }) { logoUrl, docId ->
                catIcon = logoUrl
                catDocId = docId
            }
        }
    }

    // Quiz form entries
    var isSavingQuiz by varRemember { mutableStateOf(false) }
    var selectedCatForQuizId by varRemember { mutableStateOf("") }
    var quizTitle by varRemember { mutableStateOf("") }
    var quizDesc by varRemember { mutableStateOf("") }
    var quizHours by varRemember { mutableStateOf("00") }
    var quizMinutes by varRemember { mutableStateOf("05") }
    var quizSeconds by varRemember { mutableStateOf("00") }
    var isQuizShuffle by varRemember { mutableStateOf(false) }
    var quizMarks by varRemember { mutableStateOf("1") }
    var quizNegativeMarking by varRemember { mutableStateOf("0") }

    // Question form entries
    var isSavingQuestion by varRemember { mutableStateOf(false) }
    var selectedQuizForQId by varRemember { mutableStateOf("") }
    var qText by varRemember { mutableStateOf("") }
    var qOptA by varRemember { mutableStateOf("") }
    var qOptB by varRemember { mutableStateOf("") }
    var qOptC by varRemember { mutableStateOf("") }
    var qOptD by varRemember { mutableStateOf("") }
    var qCorrect by varRemember { mutableStateOf("A") }
    var qExplanation by varRemember { mutableStateOf("") }
    var editingQuestion by varRemember { mutableStateOf<com.example.data.Question?>(null) }

    val adminCategories by viewModel.adminCategoriesList.collectAsState()
    val adminQuizzes by viewModel.adminQuizzesList.collectAsState()
    val adminQuestions by viewModel.adminQuestionsForSelectedQuiz.collectAsState()

    // Trigger loads
    LaunchedEffect(Unit) {
        viewModel.loadAdminCategories()
    }

    LaunchedEffect(adminCategories) {
        if (selectedCatForQuizId == "" && adminCategories.isNotEmpty()) {
            selectedCatForQuizId = adminCategories.first().documentId
        }
    }

    LaunchedEffect(selectedCatForQuizId) {
        if (selectedCatForQuizId != "") {
            viewModel.loadAdminQuizzes(selectedCatForQuizId)
            selectedQuizForQId = ""
        }
    }

    LaunchedEffect(adminQuizzes) {
        if (selectedQuizForQId == "" && adminQuizzes.isNotEmpty()) {
            selectedQuizForQId = adminQuizzes.first().documentId
        }
    }

    LaunchedEffect(selectedQuizForQId) {
        if (selectedQuizForQId != "") {
            viewModel.loadAdminQuestions(selectedQuizForQId)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val activity = remember(context) { context.findActivity() }
                IconButton(
                    onClick = {
                        RewardedInterstitialAdManager.showBackNavigationAd(activity) {
                            viewModel.navigateTo(Screen.Home, clearBackstack = true)
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Admin Panel",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { innerPadding ->
        val currentUserEmail by viewModel.currentUserEmail.collectAsState()
        val currentUserRole by viewModel.currentUserRole.collectAsState()
        val isAuthLoading by viewModel.isAuthLoading.collectAsState()
        val authMessage by viewModel.authMessage.collectAsState()
        val isSyncing by viewModel.isSyncing.collectAsState()

        var isSignInMode by varRemember { mutableStateOf(true) }
        var emailInput by varRemember { mutableStateOf("") }
        var passwordInput by varRemember { mutableStateOf("") }
        var selectedRoleInput by varRemember { mutableStateOf("Admin") } // Default to Admin inside Admin workspace

        PullToRefreshLayout(
            isRefreshing = isSyncing,
            onRefresh = { viewModel.refreshAllAppData() },
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = innerPadding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    end = innerPadding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    top = innerPadding.calculateTopPadding(),
                    bottom = 0.dp
                )
        ) {
            // Direct Admin Workspace (Fully Offline Local Content Management)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 8.dp, end = 8.dp, top = 0.dp, bottom = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Admin Info Top Bar (Compact Status Badge Style)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .padding(bottom = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5).copy(alpha = 0.95f)),
                    border = BorderStroke(1.dp, Color(0xFFA7F3D0).copy(alpha = 0.8f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified Admin",
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Admin Console Active",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF065F46)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "•  ${currentUserEmail ?: "admin@speedenglish.com"}",
                                fontSize = 10.sp,
                                color = Color(0xFF047857).copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = { viewModel.signOut() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text(
                                text = "Sign Out",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFFB91C1C)
                            )
                        }
                    }
                }

                // Centralized Firestore Error State Component in Admin Workspace
                if (adminQuizError != null) {
                    QuizCentralizedErrorState(
                        errorMessage = adminQuizError ?: "",
                        onRetry = {
                            val activeCatId = when (selectedTab) {
                                1 -> selectedCatForQuizId
                                2 -> selectedQuizForQId
                                else -> ""
                            }
                            viewModel.retryAdminQuizFetch(activeCatId)
                        },
                        isRetrying = isSyncing,
                        onDismiss = { viewModel.clearAdminQuizError() },
                        title = "Server Sync Error (Admin Console)"
                    )
                }

                // Tab Selection for Category / Quizzes / Questions
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tabs = listOf(
                            Triple(0, "Categories", Icons.Default.List),
                            Triple(1, "Quizzes", Icons.Default.MenuBook),
                            Triple(2, "Questions", Icons.Default.Star),
                            Triple(3, "Cloud Sync", Icons.Default.Cloud),
                            Triple(4, "Audit Log", Icons.Default.ReceiptLong),
                            Triple(5, "Users", Icons.Default.People),
                            Triple(6, "Contact Us", Icons.Default.SupportAgent),
                            Triple(7, "Privacy Policy", Icons.Default.PrivacyTip),
                            Triple(8, "Post", Icons.Default.DynamicFeed),
                            Triple(9, "Links", Icons.Default.Link)
                        )
                        tabs.forEach { (index, label, icon) ->
                            val isSelected = selectedTab == index
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { selectedTab = index }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                when (selectedTab) {
                    // CATEGORIES TAB
                    0 -> {
                        // Category Creation Form
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp)
                                .padding(bottom = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            val previewCategory = remember(catName, catIcon, catParentId, isCatDraft, editingCategory) {
                                com.example.data.Category(
                                    documentId = editingCategory?.documentId ?: "",
                                    name = catName,
                                    description = catDesc,
                                    iconName = catIcon,
                                    parentCategoryId = catParentId,
                                    isDraft = isCatDraft
                                )
                            }
                            val previewColor = rememberCategoryColor(previewCategory, viewModel)
                            val isLightColor = (previewColor.red * 0.299f + previewColor.green * 0.587f + previewColor.blue * 0.114f) > 0.65f
                            val contentColor = if (isLightColor) Color(0xFF1E293B) else Color.White

                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = if (editingCategory != null) "Edit Category" else "Create New Category",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = previewColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedTextField(
                                    value = catName,
                                    onValueChange = { catName = it },
                                    label = { Text("Category Name (e.g., Geography)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("add_category_name_input")
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedTextField(
                                    value = catDesc,
                                    onValueChange = { catDesc = it },
                                    label = { Text("Short Description (Optional)", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth().testTag("add_category_desc_input")
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                var parentDropdownExpanded by varRemember { mutableStateOf(false) }
                                val parentCategoryCandidates = adminCategories.filter { it.documentId != editingCategory?.documentId }
                                val selectedParentName = parentCategoryCandidates.find { it.documentId == catParentId }?.name ?: "None (Main Category)"

                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(
                                        text = "Assign to Parent Category",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { parentDropdownExpanded = !parentDropdownExpanded }
                                            .testTag("assign_to_category_box"),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                     ) {
                                         Row(
                                             modifier = Modifier
                                                 .fillMaxWidth()
                                                 .padding(horizontal = 12.dp, vertical = 10.dp),
                                             horizontalArrangement = Arrangement.SpaceBetween,
                                             verticalAlignment = Alignment.CenterVertically
                                         ) {
                                             Text(
                                                 text = selectedParentName,
                                                 fontSize = 13.sp,
                                                 color = MaterialTheme.colorScheme.onSurface
                                             )
                                             Icon(
                                                 imageVector = if (parentDropdownExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                 contentDescription = null,
                                                 tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                 modifier = Modifier.size(18.dp)
                                             )
                                         }
                                     }
                                     if (parentDropdownExpanded) {
                                         Card(
                                             modifier = Modifier
                                                 .fillMaxWidth()
                                                 .padding(top = 4.dp)
                                                 .heightIn(max = 150.dp),
                                             shape = RoundedCornerShape(8.dp),
                                             border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                             colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                         ) {
                                             Column(
                                                 modifier = Modifier
                                                     .fillMaxWidth()
                                                     .verticalScroll(rememberScrollState())
                                             ) {
                                                 // "None" option
                                                 Box(
                                                     modifier = Modifier
                                                         .fillMaxWidth()
                                                         .clickable {
                                                             catParentId = null
                                                             parentDropdownExpanded = false
                                                         }
                                                         .padding(horizontal = 12.dp, vertical = 8.dp)
                                                 ) {
                                                     Text(
                                                         text = "None (Main Category)",
                                                         fontSize = 13.sp,
                                                         fontWeight = if (catParentId == null) FontWeight.Bold else FontWeight.Normal,
                                                         color = if (catParentId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                     )
                                                 }
                                                 
                                                 // Other categories options
                                                 parentCategoryCandidates.forEach { candidate ->
                                                     Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                                     Box(
                                                         modifier = Modifier
                                                             .fillMaxWidth()
                                                             .clickable {
                                                                 catParentId = candidate.documentId
                                                                 parentDropdownExpanded = false
                                                             }
                                                             .padding(horizontal = 12.dp, vertical = 8.dp)
                                                     ) {
                                                         Text(
                                                             text = candidate.name,
                                                             fontSize = 13.sp,
                                                             fontWeight = if (catParentId == candidate.documentId) FontWeight.Bold else FontWeight.Normal,
                                                             color = if (catParentId == candidate.documentId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                         )
                                                     }
                                                 }
                                             }
                                         }
                                     }
                                 }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text("Category Representation Icon / Image", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(2.dp))

                                // Real-time Preview Card
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isCustomPreviewIcon = catIcon.startsWith("data:") ||
                                            catIcon.startsWith("http://") ||
                                            catIcon.startsWith("https://") ||
                                            catIcon.startsWith("content://") ||
                                            catIcon.startsWith("file://")

                                    if (isCustomPreviewIcon) {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(previewColor.copy(alpha = 0.12f))
                                                .border(BorderStroke(1.5.dp, previewColor.copy(alpha = 0.5f)), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CategoryIcon(
                                                    iconName = catIcon,
                                                    contentDescription = catName,
                                                    tint = previewColor,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(previewColor.copy(alpha = 0.08f))
                                                .border(BorderStroke(1.dp, previewColor.copy(alpha = 0.2f)), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(31.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CategoryIcon(
                                                    iconName = catIcon,
                                                    contentDescription = null,
                                                    tint = previewColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Current Icon Preview", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        Text(
                                            text = if (catIcon.startsWith("http") || catIcon.startsWith("content") || catIcon.startsWith("data:")) "Custom Uploaded Image" else "Standard Icon: $catIcon",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Options Segmented Selector
                                var iconSelectTab by varRemember { mutableStateOf(0) } // 0: Standard, 1: Gallery Upload, 2: URL
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val tabLabels = listOf("Standard", "Upload Image", "Paste URL")
                                    tabLabels.forEachIndexed { idx, lbl ->
                                        val isSelected = iconSelectTab == idx
                                        OutlinedButton(
                                            onClick = { iconSelectTab = idx },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                            ),
                                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                        ) {
                                            Text(lbl, fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                when (iconSelectTab) {
                                    0 -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val iconChoices = listOf(
                                                "science" to Icons.Default.Star,
                                                "history" to Icons.Default.List,
                                                "public" to Icons.Default.Home,
                                                "translate" to Icons.Default.Info,
                                                "spotting" to Icons.Default.Edit,
                                                "vocabulary" to Icons.Default.MenuBook,
                                                "idioms" to Icons.Default.Lightbulb,
                                                "voice" to Icons.Default.PlayArrow
                                            )
                                            Row(
                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                iconChoices.forEach { (token, icon) ->
                                                    val isSelected = catIcon == token
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                                            .border(
                                                                width = if (isSelected) 2.dp else 0.dp,
                                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                                shape = RoundedCornerShape(6.dp)
                                                            )
                                                            .clickable { catIcon = token },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = icon,
                                                            contentDescription = token,
                                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    1 -> {
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            if (isLogoUploading) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp).padding(vertical = 2.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Uploading to Cloud Storage...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            } else {
                                                Button(
                                                    onClick = { imagePickerLauncher.launch("image/*") },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Upload, contentDescription = "Upload", modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("📂 Choose Image", fontSize = 11.sp)
                                                }
                                            }
                                            
                                            logoUploadError?.let { err ->
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("Upload failed: $err", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                                            }
                                            
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Or select preset:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            
                                            Row(
                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                val illustrationPresets = listOf(
                                                    "https://images.unsplash.com/photo-1507668077129-56e32842fceb?w=200&auto=format&fit=crop" to "Physics",
                                                    "https://images.unsplash.com/photo-1447069387593-a5de0862481e?w=200&auto=format&fit=crop" to "Library",
                                                    "https://images.unsplash.com/photo-1524492412937-b28074a5d7da?w=200&auto=format&fit=crop" to "Geography",
                                                    "https://images.unsplash.com/photo-1559757175-5700dde675bc?w=200&auto=format&fit=crop" to "Brain",
                                                    "https://images.unsplash.com/photo-1546410531-bb4caa6b424d?w=200&auto=format&fit=crop" to "Languages",
                                                    "https://images.unsplash.com/photo-1513542789411-b6a5d4f31634?w=200&auto=format&fit=crop" to "Arts"
                                                )
                                                
                                                illustrationPresets.forEach { (url, label) ->
                                                    val isSelected = catIcon == url
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        modifier = Modifier
                                                            .width(70.dp)
                                                            .clickable { 
                                                                catIcon = url 
                                                                viewModel.clearLogoUploadError()
                                                            }
                                                            .border(
                                                                width = if (isSelected) 2.dp else 1.dp,
                                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE2E8F0),
                                                                shape = RoundedCornerShape(6.dp)
                                                            )
                                                            .padding(2.dp)
                                                    ) {
                                                        coil.compose.AsyncImage(
                                                            model = url,
                                                            contentDescription = label,
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .clip(RoundedCornerShape(4.dp)),
                                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(label, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    2 -> {
                                        OutlinedTextField(
                                            value = if (catIcon.startsWith("http")) catIcon else "",
                                            onValueChange = { catIcon = it },
                                            label = { Text("Paste Custom Image Web URL") },
                                            placeholder = { Text("https://example.com/image.png") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                if (false) Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val iconChoices = listOf(
                                        "science" to Icons.Default.Star,
                                        "history" to Icons.Default.List,
                                        "public" to Icons.Default.Home,
                                        "translate" to Icons.Default.Info
                                    )
                                    iconChoices.forEach { (token, icon) ->
                                        val isSelected = catIcon == token
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                                .border(
                                                    width = if (isSelected) 2.dp else 0.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { catIcon = token },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = token,
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "Status (Choose Visibility)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { isCatDraft = false },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (!isCatDraft) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (!isCatDraft) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Publish", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = { isCatDraft = true },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isCatDraft) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (isCatDraft) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Draft", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                 Button(
                                    onClick = {
                                        if (catName.isNotBlank() && !isSavingCategory) {
                                            isSavingCategory = true
                                            val currentEdit = editingCategory
                                            if (currentEdit != null) {
                                                viewModel.updateCategory(
                                                    currentEdit.copy(
                                                        name = catName,
                                                        description = catDesc,
                                                        iconName = catIcon,
                                                        isDraft = isCatDraft,
                                                        parentCategoryId = catParentId,
                                                        documentId = if (catDocId.isNotEmpty()) catDocId else currentEdit.documentId
                                                     )
                                                 ) {
                                                     editingCategory = null
                                                     catName = ""
                                                     catDesc = ""
                                                     catIcon = "general"
                                                     isCatDraft = false
                                                     catParentId = null
                                                     catDocId = ""
                                                     isSavingCategory = false
                                                     viewModel.clearLogoUploadError()
                                                 }
                                             } else {
                                                 viewModel.addCategory(
                                                     name = catName,
                                                     description = catDesc,
                                                     iconName = catIcon,
                                                     isDraft = isCatDraft,
                                                     parentCategoryId = catParentId,
                                                     documentId = catDocId
                                                 ) {
                                                     catName = ""
                                                     catDesc = ""
                                                     catIcon = "general"
                                                     isCatDraft = false
                                                     catParentId = null
                                                     catDocId = ""
                                                     isSavingCategory = false
                                                     viewModel.clearLogoUploadError()
                                                 }
                                             }
                                         }
                                     },
                                     modifier = Modifier.fillMaxWidth().testTag("submit_category_button"),
                                     enabled = !isSyncing && !isSavingCategory && catName.isNotBlank(),
                                     colors = ButtonDefaults.buttonColors(
                                         containerColor = previewColor,
                                         contentColor = contentColor
                                     )
                                 ) {
                                     Text(if (editingCategory != null) "Update Category" else "Add Category", fontWeight = FontWeight.Bold)
                                 }

                                if (editingCategory != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedButton(
                                        onClick = {
                                            editingCategory = null
                                            catName = ""
                                            catDesc = ""
                                            catIcon = "general"
                                            isCatDraft = false
                                            catParentId = null
                                            catDocId = ""
                                            viewModel.clearLogoUploadError()
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("cancel_category_edit_button")
                                    ) {
                                        Text("Cancel Edit", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Existing Category List with Deletion Access
                        Text(
                            text = "Existing Categories",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp)
                                .padding(bottom = 4.dp)
                        )

                        adminCategories.forEach { category ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 600.dp)
                                    .padding(bottom = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = category.name, fontWeight = FontWeight.Bold)
                                        if (category.description.isNotBlank()) {
                                            Text(text = category.description, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Interactive Status Pill
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (category.isDraft) Color(0xFFF1F5F9) else Color(0xFFDCFCE7))
                                                .clickable { viewModel.toggleCategoryDraftStatus(category) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(if (category.isDraft) Color(0xFF64748B) else Color(0xFF16A34A))
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (category.isDraft) "Draft" else "Published",
                                                    color = if (category.isDraft) Color(0xFF475569) else Color(0xFF15803D),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                editingCategory = category
                                                catName = category.name
                                                catDesc = category.description
                                                catIcon = category.iconName
                                                isCatDraft = category.isDraft
                                                catParentId = category.parentCategoryId
                                                catDocId = category.documentId
                                                viewModel.clearLogoUploadError()
                                                iconSelectTab = when {
                                                    category.iconName.startsWith("data:") || category.iconName.startsWith("content:") -> 1
                                                    category.iconName.startsWith("http:") || category.iconName.startsWith("https:") -> 2
                                                    else -> 0
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Category",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        IconButton(
                                            onClick = { categoryToDelete = category }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Category",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // QUIZZES TAB
                    1 -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp)
                                .padding(bottom = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val customApiKey by viewModel.customGeminiApiKey.collectAsState()
                                var showApiKeyDialog by varRemember { mutableStateOf(false) }

                                if (showApiKeyDialog) {
                                    var apiKeyInputState by varRemember { mutableStateOf(customApiKey) }
                                    AlertDialog(
                                        onDismissRequest = { showApiKeyDialog = false },
                                        title = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = "API Key Icon",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Gemini API Key Configuration", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        text = {
                                            Column {
                                                Text(
                                                    text = "Enter your custom Gemini API key to use for PDF scanning and AI quiz generation.",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                OutlinedTextField(
                                                    value = apiKeyInputState,
                                                    onValueChange = { apiKeyInputState = it },
                                                    label = { Text("Gemini API Key") },
                                                    placeholder = { Text("AIzaSy...") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth().testTag("api_key_dialog_input"),
                                                    visualTransformation = if (apiKeyInputState.isNotEmpty()) {
                                                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                                                    } else {
                                                        androidx.compose.ui.text.input.VisualTransformation.None
                                                    }
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = if (customApiKey.isNotEmpty()) "Status: Custom API Key is configured" else "Status: Using default/BuildConfig API Key",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (customApiKey.isNotEmpty()) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        confirmButton = {
                                            Button(
                                                onClick = {
                                                    viewModel.saveCustomGeminiApiKey(apiKeyInputState)
                                                    showApiKeyDialog = false
                                                    android.widget.Toast.makeText(context, "API Key saved successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.testTag("save_api_key_button")
                                            ) {
                                                Text("Save")
                                            }
                                        },
                                        dismissButton = {
                                            Row {
                                                if (customApiKey.isNotEmpty()) {
                                                    TextButton(
                                                        onClick = {
                                                            viewModel.clearCustomGeminiApiKey()
                                                            showApiKeyDialog = false
                                                            android.widget.Toast.makeText(context, "Custom API Key cleared.", android.widget.Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.testTag("clear_api_key_button")
                                                    ) {
                                                        Text("Clear", color = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                                TextButton(onClick = { showApiKeyDialog = false }) {
                                                    Text("Cancel")
                                                }
                                            }
                                        }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Create New Quiz",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    // Small Key Button
                                    Button(
                                        onClick = { showApiKeyDialog = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (customApiKey.isNotEmpty()) Color(0xFF059669) else MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = if (customApiKey.isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.testTag("configure_api_key_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "API Key",
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (customApiKey.isNotEmpty()) "🔑 Custom Key" else "🔑 Set API Key",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))

                                // Select Category Dropdown Selector
                                Text("Assign to Category:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    adminCategories.forEach { cat ->
                                        val isSelected = selectedCatForQuizId == cat.documentId
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedCatForQuizId = cat.documentId },
                                            label = { Text(cat.name, fontSize = 11.sp) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedTextField(
                                    value = quizTitle,
                                    onValueChange = { quizTitle = it },
                                    label = { Text("Quiz Title (e.g. World Capitals)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("add_quiz_title_input")
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                OutlinedTextField(
                                    value = quizDesc,
                                    onValueChange = { quizDesc = it },
                                    label = { Text("Brief Description", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth().testTag("add_quiz_desc_input")
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = quizMarks,
                                        onValueChange = { input ->
                                            val filtered = input.filter { it.isDigit() || it == '.' }
                                            if (filtered.count { it == '.' } <= 1) {
                                                quizMarks = filtered
                                            }
                                        },
                                        label = { Text("Mark", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("add_quiz_marks_input")
                                    )

                                    OutlinedTextField(
                                        value = quizNegativeMarking,
                                        onValueChange = { input ->
                                            val filtered = input.filter { it.isDigit() || it == '.' }
                                            if (filtered.count { it == '.' } <= 1) {
                                                quizNegativeMarking = filtered
                                            }
                                        },
                                        label = { Text("Negative Marking", fontSize = 11.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("add_quiz_negative_marking_input")
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Quiz Time Limit (HH:MM:SS) - global limit, not per question",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = quizHours,
                                        onValueChange = { if (it.length <= 2) quizHours = it.filter { char -> char.isDigit() } },
                                        label = { Text("HH", fontSize = 10.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("add_quiz_hours_input")
                                    )
                                    Text(":", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedTextField(
                                        value = quizMinutes,
                                        onValueChange = { if (it.length <= 2) quizMinutes = it.filter { char -> char.isDigit() } },
                                        label = { Text("MM", fontSize = 10.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("add_quiz_minutes_input")
                                    )
                                    Text(":", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedTextField(
                                        value = quizSeconds,
                                        onValueChange = { if (it.length <= 2) quizSeconds = it.filter { char -> char.isDigit() } },
                                        label = { Text("SS", fontSize = 10.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("add_quiz_seconds_input")
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .clickable { isQuizShuffle = !isQuizShuffle }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Shuffle",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = "Shuffle Questions",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = "Shuffle questions automatically each attempt",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = isQuizShuffle,
                                        onCheckedChange = { isQuizShuffle = it },
                                        modifier = Modifier.scale(0.85f).testTag("add_quiz_shuffle_toggle")
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Button(
                                    onClick = {
                                        val h = quizHours.toIntOrNull() ?: 0
                                        val m = quizMinutes.toIntOrNull() ?: 0
                                        val s = quizSeconds.toIntOrNull() ?: 0
                                        val timeSec = (h * 3600) + (m * 60) + s
                                        val finalTimeSec = if (timeSec > 0) timeSec else 300 // Default to 5 minutes if 0
                                        if (selectedCatForQuizId != "" && quizTitle.isNotBlank() && !isSavingQuiz) {
                                            isSavingQuiz = true
                                            val marks = quizMarks.toFloatOrNull() ?: 1.0f
                                            val negMark = quizNegativeMarking.toFloatOrNull() ?: 0.0f
                                            viewModel.addQuiz(selectedCatForQuizId, quizTitle, quizDesc, finalTimeSec, false, isQuizShuffle, marks, negMark) {
                                                quizTitle = ""
                                                quizDesc = ""
                                                quizHours = "00"
                                                quizMinutes = "05"
                                                quizSeconds = "00"
                                                isQuizShuffle = false
                                                isSavingQuiz = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("submit_quiz_button"),
                                    enabled = !isSyncing && !isSavingQuiz && selectedCatForQuizId != "" && quizTitle.isNotBlank()
                                ) {
                                    Text("Add Quiz", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Displays of Quizzes under Selected Category
                        Text(
                            text = "Quizzes in Selected Category",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp)
                                .padding(bottom = 4.dp)
                        )

                        if (selectedCatForQuizId == "") {
                            Text(
                                "Please select a Category from chips above to view its quizzes.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            adminQuizzes.forEachIndexed { index, quiz ->
                                AdminQuizCard(
                                    quiz = quiz,
                                    viewModel = viewModel,
                                    isFirst = index == 0,
                                    isLast = index == adminQuizzes.size - 1,
                                    onMoveUp = { viewModel.moveQuizUp(quiz.documentId) },
                                    onMoveDown = { viewModel.moveQuizDown(quiz.documentId) }
                                )
                                if (false) Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 600.dp)
                                        .padding(bottom = 8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = quiz.title, fontWeight = FontWeight.Bold)
                                            val formattedTime = remember(quiz.timeLimitSeconds) {
                                                val h = quiz.timeLimitSeconds / 3600
                                                val m = (quiz.timeLimitSeconds % 3600) / 60
                                                val s = quiz.timeLimitSeconds % 60
                                                val padMinutes = if (h > 0) m.toString().padStart(2, '0') else m.toString()
                                                val padSeconds = s.toString().padStart(2, '0')
                                                if (h > 0) "$h:$padMinutes:$padSeconds" else "$m:$padSeconds"
                                            }
                                            Text(
                                                text = "${quiz.description} ($formattedTime limit)",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteQuiz(quiz) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Quiz",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // QUESTIONS TAB
                    2 -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp)
                                .padding(bottom = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val isScanningPdf by viewModel.isScanningPdf.collectAsState()
                                val pdfScanStatus by viewModel.pdfScanStatus.collectAsState()
                                val pdfScanProgress by viewModel.pdfScanProgress.collectAsState()
                                val selectedFileMetadata by viewModel.selectedFileMetadata.collectAsState()
                                val pdfPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                                    contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                                ) { uri ->
                                    if (uri != null) {
                                        viewModel.selectFileForImport(uri, context)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (editingQuestion != null) "Edit Question" else "Create New Question",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isScanningPdf) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 1.6.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                if (selectedQuizForQId == "") {
                                                    viewModel.setPdfStatus("Error: Choose category and quiz first")
                                                } else {
                                                    pdfPickerLauncher.launch("*/*")
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 1.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.testTag("scan_pdf_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Upload,
                                                contentDescription = "Scan PDF",
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Scan Document (PDF/DOCX)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Selected File Metadata Preview Component
                                selectedFileMetadata?.let { metadata ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("selected_file_preview"),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (metadata.isValid) {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            } else {
                                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                                            }
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (metadata.isValid) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (metadata.isValid) {
                                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                            } else {
                                                                MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                                            }
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (metadata.isValid) Icons.Default.Description else Icons.Default.Warning,
                                                        contentDescription = "File Type Icon",
                                                        tint = if (metadata.isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = metadata.name,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (metadata.isValid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.testTag("selected_file_name")
                                                    )
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        val formattedSize = if (metadata.size > 0) {
                                                            val kb = metadata.size / 1024.0
                                                            if (kb > 1024.0) {
                                                                String.format("%.2f MB", kb / 1024.0)
                                                            } else {
                                                                String.format("%.2f KB", kb)
                                                            }
                                                        } else {
                                                            "Unknown size"
                                                        }
                                                        Text(
                                                            text = formattedSize,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.testTag("selected_file_size")
                                                        )
                                                        Text(
                                                            text = "•",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        val mimeLabel = if (metadata.pageCount > 0) {
                                                            "PDF (${metadata.pageCount} pages)"
                                                        } else {
                                                            metadata.mimeType
                                                        }
                                                        Text(
                                                            text = mimeLabel,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.testTag("selected_file_type")
                                                        )
                                                    }
                                                }
                                            }

                                            // Show warning/error message if present
                                            metadata.warningMessage?.let { warnMsg ->
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = "Warning",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = warnMsg,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = { viewModel.clearSelectedFile() },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("cancel_upload_btn"),
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Cancel",
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("Discard", fontSize = 11.sp)
                                                }

                                                Button(
                                                    onClick = {
                                                        viewModel.processSelectedFile(context, selectedQuizForQId)
                                                    },
                                                    enabled = metadata.isValid,
                                                    modifier = Modifier
                                                        .weight(1.2f)
                                                        .testTag("confirm_upload_btn"),
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                    shape = RoundedCornerShape(6.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                    )
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Process",
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("Analyze with AI", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                if (pdfScanStatus.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (pdfScanStatus.contains("failed", true) || pdfScanStatus.contains("Error", true))
                                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                                else
                                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = pdfScanStatus,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = if (pdfScanStatus.contains("failed", true) || pdfScanStatus.contains("Error", true))
                                                    MaterialTheme.colorScheme.error
                                                else
                                                    MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = { viewModel.clearPdfStatus() },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Clear Status",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }

                                        if (isScanningPdf || pdfScanProgress > 0f) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            LinearProgressIndicator(
                                                progress = { pdfScanProgress.coerceIn(0f, 1f) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp)),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Category:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(vertical = 1.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            adminCategories.forEach { cat ->
                                                val isSelected = selectedCatForQuizId == cat.documentId
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                                        .clickable { selectedCatForQuizId = cat.documentId }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = cat.name,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Quiz:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(vertical = 1.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            adminQuizzes.forEach { qz ->
                                                val isSelected = selectedQuizForQId == qz.documentId
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                                        .clickable { selectedQuizForQId = qz.documentId }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = qz.title,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                OutlinedTextField(
                                    value = qText,
                                    onValueChange = { qText = it },
                                    label = { Text("Question Text", fontSize = 10.sp) },
                                    textStyle = TextStyle(fontSize = 11.sp),
                                    maxLines = 2,
                                    modifier = Modifier.fillMaxWidth().testTag("add_question_text_input")
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    OutlinedTextField(
                                        value = qOptA,
                                        onValueChange = { qOptA = it },
                                        label = { Text("Option A", fontSize = 10.sp) },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 11.sp),
                                        modifier = Modifier.weight(1f).testTag("add_option_a_input")
                                    )
                                    OutlinedTextField(
                                        value = qOptB,
                                        onValueChange = { qOptB = it },
                                        label = { Text("Option B", fontSize = 10.sp) },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 11.sp),
                                        modifier = Modifier.weight(1f).testTag("add_option_b_input")
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    OutlinedTextField(
                                        value = qOptC,
                                        onValueChange = { qOptC = it },
                                        label = { Text("Option C", fontSize = 10.sp) },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 11.sp),
                                        modifier = Modifier.weight(1f).testTag("add_option_c_input")
                                    )
                                    OutlinedTextField(
                                        value = qOptD,
                                        onValueChange = { qOptD = it },
                                        label = { Text("Option D", fontSize = 10.sp) },
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 11.sp),
                                        modifier = Modifier.weight(1f).testTag("add_option_d_input")
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1.2f)) {
                                        OutlinedTextField(
                                            value = qExplanation,
                                            onValueChange = { qExplanation = it },
                                            label = { Text("Explanation (Optional)", fontSize = 10.sp) },
                                            singleLine = true,
                                            textStyle = TextStyle(fontSize = 11.sp),
                                            modifier = Modifier.fillMaxWidth().testTag("add_question_explanation_input")
                                        )
                                    }
                                    Column(modifier = Modifier.weight(0.8f)) {
                                        Text("Correct Key:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            listOf("A", "B", "C", "D").forEach { okey ->
                                                val isSelected = qCorrect == okey
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(28.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                                        .clickable { qCorrect = okey },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = okey,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Button(
                                    onClick = {
                                        val activeQuizId = if (selectedQuizForQId != "") {
                                            selectedQuizForQId
                                        } else if (editingQuestion != null) {
                                            editingQuestion!!.quizId
                                        } else if (adminQuizzes.isNotEmpty()) {
                                            val autoId = adminQuizzes.first().documentId
                                            selectedQuizForQId = autoId
                                            autoId
                                        } else {
                                            ""
                                        }

                                        if (activeQuizId == "") {
                                            android.widget.Toast.makeText(context, "Please select or create a Quiz first!", android.widget.Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        if (qText.isBlank()) {
                                            android.widget.Toast.makeText(context, "Please enter question text", android.widget.Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        if (qOptA.isBlank() || qOptB.isBlank()) {
                                            android.widget.Toast.makeText(context, "Please enter at least Option A and Option B", android.widget.Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        if (isSavingQuestion) return@Button

                                        isSavingQuestion = true
                                        val optA = qOptA.trim()
                                        val optB = qOptB.trim()
                                        val optC = if (qOptC.isNotBlank()) qOptC.trim() else "N/A"
                                        val optD = if (qOptD.isNotBlank()) qOptD.trim() else "N/A"
                                        val explanation = qExplanation.trim()

                                        val currentEdit = editingQuestion
                                        if (currentEdit != null) {
                                            viewModel.updateQuestion(
                                                currentEdit.copy(
                                                    quizId = activeQuizId,
                                                    text = qText.trim(),
                                                    optionA = optA,
                                                    optionB = optB,
                                                    optionC = optC,
                                                    optionD = optD,
                                                    correctOption = qCorrect,
                                                    explanation = explanation
                                                )
                                            ) {
                                                editingQuestion = null
                                                qText = ""
                                                qOptA = ""
                                                qOptB = ""
                                                qOptC = ""
                                                qOptD = ""
                                                qExplanation = ""
                                                isSavingQuestion = false
                                                android.widget.Toast.makeText(context, "Question updated successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            viewModel.addQuestion(
                                                activeQuizId,
                                                qText.trim(),
                                                optA,
                                                optB,
                                                optC,
                                                optD,
                                                qCorrect,
                                                explanation
                                            ) {
                                                qText = ""
                                                qOptA = ""
                                                qOptB = ""
                                                qOptC = ""
                                                qOptD = ""
                                                qExplanation = ""
                                                isSavingQuestion = false
                                                android.widget.Toast.makeText(context, "Question added successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("submit_question_button"),
                                    enabled = !isSyncing && !isSavingQuestion && qText.isNotBlank() && qOptA.isNotBlank() && qOptB.isNotBlank()
                                ) {
                                    Text(if (editingQuestion != null) "Update Question" else "Add Question", fontWeight = FontWeight.Bold)
                                }

                                if (editingQuestion != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedButton(
                                        onClick = {
                                            editingQuestion = null
                                            qText = ""
                                            qOptA = ""
                                            qOptB = ""
                                            qOptC = ""
                                            qOptD = ""
                                            qExplanation = ""
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("cancel_question_edit_button")
                                    ) {
                                        Text("Cancel Edit", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Existing Questions in selected quiz with deleting operations
                        Text(
                            text = "Questions in Selected Quiz (${adminQuestions.size}/120)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp)
                                .padding(bottom = 4.dp)
                        )

                        if (selectedQuizForQId == "") {
                            Text(
                                "Please select Category and Quiz from chips above to view its questions.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            if (adminQuestions.isEmpty()) {
                                Text("No questions in this quiz yet.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                            }
                            val reversedQuestions = adminQuestions.asReversed()
                            reversedQuestions.forEachIndexed { displayIndex, question ->
                                val originalIndex = adminQuestions.size - 1 - displayIndex
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 600.dp)
                                        .padding(bottom = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.padding(bottom = 3.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color.Black, shape = RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = "Q${originalIndex + 1}",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                Text(text = question.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
                                            Text(
                                                text = "A: ${question.optionA} | B: ${question.optionB} | C: ${question.optionC} | D: ${question.optionD}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Correct Answer: option ${question.correctOption}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (!question.explanation.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Explanation: ${question.explanation}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    editingQuestion = question
                                                    selectedQuizForQId = question.quizId
                                                    qText = question.text
                                                    qOptA = question.optionA
                                                    qOptB = question.optionB
                                                    qOptC = question.optionC
                                                    qOptD = question.optionD
                                                    qCorrect = question.correctOption
                                                    qExplanation = question.explanation
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Question",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            IconButton(
                                                onClick = { questionToDelete = question }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Question",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        CloudDatabaseSyncSection(viewModel = viewModel)
                    }
                    4 -> {
                        AdminAuditLogContent(viewModel = viewModel)
                    }
                    5 -> {
                        AdminUsersContent(viewModel = viewModel)
                    }
                    6 -> {
                        AdminContactUsManagementContent(viewModel = viewModel)
                    }
                    7 -> {
                        AdminPrivacyPolicyManagementContent(viewModel = viewModel)
                    }
                    8 -> {
                        AdminPostManagementContent(viewModel = viewModel)
                    }
                    9 -> {
                        AdminSocialDestinationManagementContent(viewModel = viewModel)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                // Database Restoration Utility
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Admin Recovery Actions",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Wipe custom changes and restore default high-vibrancy quiz categories (Science, History, Geopolitics, English).",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.restoreDefaultSeedData() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Reset & Seed Original Data", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        val catToDelete = categoryToDelete
        if (catToDelete != null) {
            AlertDialog(
                onDismissRequest = { categoryToDelete = null },
                title = { Text("Delete Category") },
                text = { Text("Are you sure you want to delete '${catToDelete.name}'? This will also cascade delete associated quizzes and questions.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteCategory(catToDelete)
                            categoryToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { categoryToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        val qToDelete = questionToDelete
        if (qToDelete != null) {
            AlertDialog(
                onDismissRequest = { questionToDelete = null },
                title = { Text("Delete Question") },
                text = { Text("Are you sure you want to delete this question? This will permanently remove it from local storage.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteQuestion(qToDelete)
                            questionToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { questionToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
        }
    }

// Inline workaround helper to avoid standard naming collisions for compose remember variables state
@Composable
inline fun <T> varRemember(crossinline calculation: @DisallowComposableCalls () -> T): T = remember { calculation() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditQuizDialog(
    quiz: com.example.data.Quiz,
    categories: List<com.example.data.Category>,
    onDismiss: () -> Unit,
    onSave: (com.example.data.Quiz) -> Unit
) {
    var title by remember { mutableStateOf(quiz.title) }
    var description by remember { mutableStateOf(quiz.description) }
    
    val initialHours = quiz.timeLimitSeconds / 3600
    val initialMins = (quiz.timeLimitSeconds % 3600) / 60
    val initialSecs = quiz.timeLimitSeconds % 60
    
    var hours by remember { mutableStateOf(String.format("%02d", initialHours)) }
    var minutes by remember { mutableStateOf(String.format("%02d", initialMins)) }
    var seconds by remember { mutableStateOf(String.format("%02d", initialSecs)) }
    var isShuffle by remember { mutableStateOf(quiz.shuffleQuestions) }
    var selectedCategoryId by remember { mutableStateOf(quiz.categoryId) }
    var marksText by remember { mutableStateOf(formatDecimal(quiz.marksPerQuestion)) }
    var negativeText by remember { mutableStateOf(formatDecimal(quiz.negativeMarking)) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Quiz Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Quiz Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_quiz_title_input")
                )
                
                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_quiz_desc_input")
                )
                
                // Adjacent text fields for Mark and Negative Marking
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = marksText,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() || it == '.' }
                            if (filtered.count { it == '.' } <= 1) {
                                marksText = filtered
                            }
                        },
                        label = { Text("Mark", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("edit_quiz_marks_input")
                    )

                    OutlinedTextField(
                        value = negativeText,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() || it == '.' }
                            if (filtered.count { it == '.' } <= 1) {
                                negativeText = filtered
                            }
                        },
                        label = { Text("Negative Marking", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("edit_quiz_negative_marking_input")
                    )
                }
                
                // Category (row of chips)
                Text("Assign to Category:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategoryId == cat.documentId
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategoryId = cat.documentId },
                            label = { Text(cat.name) }
                        )
                    }
                }
                
                // Time Limit
                Text(
                    text = "Timer Limit (HH:MM:SS)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { if (it.length <= 2) hours = it.filter { char -> char.isDigit() } },
                        label = { Text("HH") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("edit_quiz_hours_input")
                    )
                    Text(":", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { if (it.length <= 2) minutes = it.filter { char -> char.isDigit() } },
                        label = { Text("MM") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("edit_quiz_minutes_input")
                    )
                    Text(":", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { if (it.length <= 2) seconds = it.filter { char -> char.isDigit() } },
                        label = { Text("SS") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("edit_quiz_seconds_input")
                    )
                }
                
                // Shuffle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .clickable { isShuffle = !isShuffle }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Shuffle",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Shuffle Questions",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Shuffle questions automatically each attempt",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isShuffle,
                        onCheckedChange = { isShuffle = it },
                        modifier = Modifier.testTag("edit_quiz_shuffle_toggle")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val h = hours.toIntOrNull() ?: 0
                    val m = minutes.toIntOrNull() ?: 0
                    val s = seconds.toIntOrNull() ?: 0
                    val timeSec = (h * 3600) + (m * 60) + s
                    val finalTimeSec = if (timeSec > 0) timeSec else 300
                    onSave(
                        quiz.copy(
                            title = title,
                            description = description,
                            categoryId = selectedCategoryId,
                            timeLimitSeconds = finalTimeSec,
                            shuffleQuestions = isShuffle,
                            marksPerQuestion = marksText.toFloatOrNull() ?: 1.0f,
                            negativeMarking = negativeText.toFloatOrNull() ?: 0.0f
                        )
                    )
                },
                enabled = title.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AdminQuizCard(
    quiz: com.example.data.Quiz,
    viewModel: QuizViewModel,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val categories by viewModel.adminCategoriesList.collectAsState()

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Quiz") },
            text = { Text("Are you sure you want to delete '${quiz.title}'? This will also delete all associated questions in Firestore.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteQuiz(quiz)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditDialog) {
        EditQuizDialog(
            quiz = quiz,
            categories = categories,
            onDismiss = { showEditDialog = false },
            onSave = { updatedQuiz ->
                viewModel.updateQuiz(updatedQuiz)
                showEditDialog = false
            }
        )
    }

    val category = categories.find { it.documentId == quiz.categoryId }
    val quizColor = category?.let { rememberCategoryColor(it, viewModel) } ?: Color(0xFF6366F1)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .padding(bottom = 4.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = 1.2.dp,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    quizColor,
                    quizColor.copy(alpha = 0.25f)
                )
            )
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showEditDialog = true }
            ) {
                Text(text = quiz.title, fontWeight = FontWeight.Bold)
                val hours = quiz.timeLimitSeconds / 3600
                val mins = (quiz.timeLimitSeconds % 3600) / 60
                val secs = quiz.timeLimitSeconds % 60
                val timeStr = String.format("%02d:%02d:%02d", hours, mins, secs)
                Text(
                    text = "${quiz.description} ($timeStr limit)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF111827)
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Interactive Shuffle Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (quiz.shuffleQuestions) Color(0xFFE0F2FE) else Color(0xFFF1F5F9))
                            .clickable { viewModel.toggleQuizShuffleStatus(quiz) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Shuffle",
                                tint = if (quiz.shuffleQuestions) Color(0xFF0284C7) else Color(0xFF64748B),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (quiz.shuffleQuestions) "Shuffle" else "No Shuffle",
                                color = if (quiz.shuffleQuestions) Color(0xFF0369A1) else Color(0xFF475569),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Interactive Status Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (quiz.isDraft) Color(0xFFF1F5F9) else Color(0xFFDCFCE7))
                            .clickable { viewModel.toggleQuizDraftStatus(quiz) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (quiz.isDraft) Color(0xFF64748B) else Color(0xFF16A34A))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (quiz.isDraft) "Draft" else "Published",
                                color = if (quiz.isDraft) Color(0xFF475569) else Color(0xFF15803D),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Move Up Action Button
                    IconButton(
                        onClick = onMoveUp,
                        enabled = !isFirst,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("move_up_${quiz.documentId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move Quiz Up",
                            tint = if (!isFirst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Move Down Action Button
                    IconButton(
                        onClick = onMoveDown,
                        enabled = !isLast,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("move_down_${quiz.documentId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Move Quiz Down",
                            tint = if (!isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Edit Action Button
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Quiz Settings",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Delete Action Button
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Quiz",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudDatabaseSyncSection(viewModel: QuizViewModel) {
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncStatusMsg by viewModel.syncStatusMessage.collectAsState()
    val lastSync by viewModel.lastSyncTime.collectAsState()
    val firestoreLogs by viewModel.firestoreLogs.collectAsState()

    // 1. INTEGRATION STATUS CARD
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Cloud Server Database",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF1E293B)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 1.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Connected & Active (Real-Time)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }

            Text(
                text = "Your application is fully integrated with Google Cloud. As an Admin, any changes you make—such as adding, editing, or deleting categories, quizzes, and questions—are automatically propagated to the cloud database and synchronized live on all student devices instantly.",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = Color(0xFF475569),
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Connection Info Table
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Sync Mode:", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                    Text(text = "Server Real-Time Listeners", fontSize = 11.sp, color = Color(0xFF334155), fontWeight = FontWeight.SemiBold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Authentication:", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                    Text(text = "Secure Anonymous Auth Session", fontSize = 11.sp, color = Color(0xFF334155), fontWeight = FontWeight.SemiBold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Admin Role Status:", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = "Verified Admin Access", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // 2. MANUAL RECOVERY & BACKUP ACTIONS CARD
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "MANUAL DATA RECOVERY & BACKUP",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Text(
                text = "While synchronization is automated, you can use these controls to manually pull a fresh backup copy from server, or force-publish your entire local database state back up to the cloud.",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(bottom = 10.dp)
            )

            if (syncStatusMsg.isNotEmpty()) {
                val isError = syncStatusMsg.contains("Error", ignoreCase = true) || syncStatusMsg.contains("failed", ignoreCase = true)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(
                            if (isError) Color(0xFFFEF2F2) else Color(0xFFF0FDF4),
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            if (isError) Color(0xFFFCA5A5) else Color(0xFF86EFAC),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp)
                ) {
                    Text(
                        text = syncStatusMsg,
                        fontSize = 12.sp,
                        color = if (isError) Color(0xFF991B1B) else Color(0xFF166534),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.fetchFromFirestoreCloud() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("fetch_from_firestore_button"),
                    enabled = !isSyncing,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Restore / Fetch", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Button(
                    onClick = { viewModel.uploadToFirestoreCloud() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("publish_to_firestore_button"),
                    enabled = !isSyncing,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Backup / Publish", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    var consoleTab by varRemember { mutableStateOf(1) } // 0: Raw Console, 1: Latency Interceptor
    val interceptorLogs by viewModel.interceptorLogs.collectAsState()

    // 3. FIRESTORE DIAGNOSTIC TERMINAL
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .padding(bottom = 24.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)), // Deep Dark Slate/Black
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E)) // Pulsing Green Dot
                    )
                    
                    // Console Tab Switcher buttons
                    Row(
                        modifier = Modifier
                            .background(Color(0xFF1E293B), RoundedCornerShape(6.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "INTERCEPTOR",
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (consoleTab == 1) Color(0xFF475569) else Color.Transparent)
                                .clickable { consoleTab = 1 }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = if (consoleTab == 1) Color.White else Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "RAW SYSTEM",
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (consoleTab == 0) Color(0xFF475569) else Color.Transparent)
                                .clickable { consoleTab = 0 }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = if (consoleTab == 0) Color.White else Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                TextButton(
                    onClick = { viewModel.clearDiagnosticLogs() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Clear Logs", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(Color(0xFF020617), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (consoleTab == 1) {
                    if (interceptorLogs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Firestore Logging Interceptor active.\nWrites, reads, and metadata state changes will appear here with calculated millisecond latency in real-time.",
                                color = Color(0xFF475569),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                lineHeight = 16.sp
                            )
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(interceptorLogs.size) { idx ->
                                val log = interceptorLogs[idx]
                                val opColor = when (log.operation) {
                                    "WRITE", "BATCH_WRITE" -> Color(0xFFF59E0B) // Amber
                                    "READ" -> Color(0xFFA855F7) // Purple
                                    "LISTENER" -> Color(0xFF06B6D4) // Cyan
                                    else -> Color(0xFF10B981) // Green
                                }
                                val sourceColor = when {
                                    log.source.contains("PENDING") -> Color(0xFFF59E0B) // Amber
                                    log.source.contains("CACHE") -> Color(0xFF38BDF8) // Bright Cyan
                                    else -> Color(0xFF22C55E) // Green
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0F172A).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(4.dp))
                                        .padding(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "[${log.timeString}]",
                                                color = Color(0xFF64748B),
                                                fontSize = 9.sp,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = log.operation,
                                                color = opColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = log.source,
                                                color = sourceColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${log.latencyMs}ms",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Path: ${log.path}",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 10.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = log.details,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 9.sp,
                                        lineHeight = 12.sp,
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (firestoreLogs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Terminal active. Perform any CRUD actions (add/delete category, quiz, or question) or manual sync to see live server transaction logging.",
                                color = Color(0xFF475569),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(firestoreLogs.size) { idx ->
                                val log = firestoreLogs[idx]
                                val logColor = when {
                                    log.contains("FAILED", ignoreCase = true) || log.contains("failed", ignoreCase = true) || log.contains("Error", ignoreCase = true) -> Color(0xFFEF4444) // Red
                                    log.contains("SUCCESS", ignoreCase = true) || log.contains("success", ignoreCase = true) -> Color(0xFF22C55E) // Green
                                    log.contains("Bypassing", ignoreCase = true) -> Color(0xFFF59E0B) // Amber
                                    else -> Color(0xFF38BDF8) // Bright Cyan Info
                                }
                                Text(
                                    text = log,
                                    color = logColor,
                                    fontSize = 10.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getResizedBase64Image(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream) ?: return null
        inputStream.close()

        val maxDimension = 150 // 150px is plenty for a category icon/logo
        val width = originalBitmap.width
        val height = originalBitmap.height
        val (newWidth, newHeight) = if (width > height) {
            val ratio = width.toFloat() / height.toFloat()
            (maxDimension to (maxDimension / ratio).toInt())
        } else {
            val ratio = height.toFloat() / width.toFloat()
            ((maxDimension / ratio).toInt() to maxDimension)
        }

        val resizedBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
        val outputStream = java.io.ByteArrayOutputStream()
        resizedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, outputStream)
        val bytes = outputStream.toByteArray()
        
        // Recycle bitmaps
        if (originalBitmap != resizedBitmap) {
            originalBitmap.recycle()
        }
        resizedBitmap.recycle()
        
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        "data:image/jpeg;base64,$base64"
    } catch (e: Exception) {
        android.util.Log.e("CategoryLogo", "Error processing image to base64", e)
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAuditLogContent(
    viewModel: QuizViewModel,
    modifier: Modifier = Modifier
) {
    DisposableEffect(Unit) {
        viewModel.startObservingAuditLogsFirestore()
        viewModel.refreshAuditLogsFromFirestore()
        onDispose {
            viewModel.stopObservingAuditLogsFirestore()
        }
    }

    val auditLogs by viewModel.auditLogs.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedActionFilter by remember { mutableStateOf("ALL") }
    var showClearDialog by remember { mutableStateOf(false) }

    val totalCount = auditLogs.size
    val addedCount = auditLogs.count { it.actionType == "ADDED" }
    val modifiedCount = auditLogs.count { it.actionType == "MODIFIED" }
    val deletedCount = auditLogs.count { it.actionType == "DELETED" }
    val propagatedCount = auditLogs.count { it.status == "PROPAGATED_TO_CLOUDFIRESTORE" }
    val syncRatePercent = if (totalCount > 0) ((propagatedCount.toFloat() / totalCount.toFloat()) * 100).toInt() else 100

    val filteredLogs = remember(auditLogs, searchQuery, selectedActionFilter) {
        auditLogs.filter { log ->
            val matchesAction = selectedActionFilter == "ALL" || log.actionType.equals(selectedActionFilter, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    log.questionText.contains(searchQuery, ignoreCase = true) ||
                    log.quizTitle.contains(searchQuery, ignoreCase = true) ||
                    log.adminEmail.contains(searchQuery, ignoreCase = true) ||
                    log.details.contains(searchQuery, ignoreCase = true)
            matchesAction && matchesSearch
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Audit Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_audit_log_header_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = "Audit Log Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Question Audit Trail & History",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Admin-only record tracking question additions, modifications, and deletions with timestamps to verify Cloud propagation.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Summary Metric Cards Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AuditMetricChip(label = "Total Operations", value = "$totalCount", containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            AuditMetricChip(label = "Added", value = "$addedCount", containerColor = Color(0xFFDCFCE7), contentColor = Color(0xFF15803D))
            AuditMetricChip(label = "Modified", value = "$modifiedCount", containerColor = Color(0xFFDBEAFE), contentColor = Color(0xFF1D4ED8))
            AuditMetricChip(label = "Deleted", value = "$deletedCount", containerColor = Color(0xFFFEE2E2), contentColor = Color(0xFFB91C1C))
            AuditMetricChip(label = "Cloud Sync Rate", value = "$syncRatePercent%", containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
        }

        // Search & Clear Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("audit_log_search_input"),
                placeholder = { Text("Search question, quiz title, admin...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            IconButton(
                onClick = { viewModel.refreshAuditLogsFromFirestore() },
                modifier = Modifier.testTag("refresh_audit_logs_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sync Audit Logs from Cloud",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (auditLogs.isNotEmpty()) {
                IconButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.testTag("clear_audit_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Audit Log History",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Action Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val filterOptions = listOf("ALL", "ADDED", "MODIFIED", "DELETED")
            filterOptions.forEach { option ->
                val isSelected = selectedActionFilter == option
                val (chipBg, chipFg) = when (option) {
                    "ADDED" -> Color(0xFFDCFCE7) to Color(0xFF15803D)
                    "MODIFIED" -> Color(0xFFDBEAFE) to Color(0xFF1D4ED8)
                    "DELETED" -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
                    else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                }

                FilterChip(
                    selected = isSelected,
                    onClick = { selectedActionFilter = option },
                    label = {
                        Text(
                            text = when (option) {
                                "ALL" -> "All ($totalCount)"
                                "ADDED" -> "Added ($addedCount)"
                                "MODIFIED" -> "Modified ($modifiedCount)"
                                "DELETED" -> "Deleted ($deletedCount)"
                                else -> option
                            },
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (option == "ALL") MaterialTheme.colorScheme.primary else chipBg,
                        selectedLabelColor = if (option == "ALL") Color.White else chipFg
                    ),
                    modifier = Modifier.testTag("audit_filter_$option")
                )
            }
        }

        // Audit Trail Logs List
        if (filteredLogs.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .testTag("audit_log_empty_state"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.HistoryToggleOff,
                        contentDescription = "No Logs Found",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No audit logs match '$searchQuery'" else "No question audit log history found.",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "When an Admin adds, modifies, or deletes a question, detailed records with timestamps will automatically appear here.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredLogs.forEach { log ->
                    AuditLogItemCard(log = log)
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Question Audit History?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete all audit log history? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAuditLogs()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All Logs")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AuditMetricChip(
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = contentColor
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
fun AuditLogItemCard(
    log: QuestionAuditLog,
    modifier: Modifier = Modifier
) {
    val (actionBg, actionFg, actionIcon) = when (log.actionType.uppercase()) {
        "ADDED" -> Triple(Color(0xFFDCFCE7), Color(0xFF15803D), Icons.Default.AddCircle)
        "MODIFIED" -> Triple(Color(0xFFDBEAFE), Color(0xFF1D4ED8), Icons.Default.Edit)
        "DELETED" -> Triple(Color(0xFFFEE2E2), Color(0xFFB91C1C), Icons.Default.Delete)
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.Info)
    }

    val formattedTimestamp = remember(log.timestamp) {
        try {
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm:ss a", java.util.Locale.getDefault())
            sdf.format(java.util.Date(log.timestamp))
        } catch (e: Exception) {
            "Timestamp #${log.timestamp}"
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audit_log_card_${log.documentId}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header Row: Action Tag + Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = actionBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = actionIcon,
                            contentDescription = log.actionType,
                            tint = actionFg,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = log.actionType.uppercase(),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            color = actionFg
                        )
                    }
                }

                Text(
                    text = formattedTimestamp,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Question Snippet Text
            Text(
                text = "\"${log.questionText}\"",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3
            )

            // Context Metadata Row (Quiz title & Admin)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    val prefix = when {
                        log.quizId == "social_posts" || log.quizTitle.contains("post", ignoreCase = true) -> "Post"
                        log.quizId == "user_profile" -> "Profile"
                        else -> "Quiz"
                    }
                    Text(
                        text = "$prefix: ${log.quizTitle}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1
                    )
                }

                if (log.adminEmail.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Admin: ${log.adminEmail}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                }
            }

            if (log.details.isNotEmpty()) {
                Text(
                    text = log.details,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Bottom Propagation Status Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isPropagated = log.status == "PROPAGATED_TO_CLOUDFIRESTORE"
                Surface(
                    color = if (isPropagated) Color(0xFFF0FDF4) else Color(0xFFFEF2F2),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPropagated) Icons.Default.CheckCircle else Icons.Default.CloudSync,
                            contentDescription = "Sync Status",
                            tint = if (isPropagated) Color(0xFF16A34A) else Color(0xFFDC2626),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPropagated) "✓ Propagated to Cloud Server" else "Saved Locally (Pending Sync)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPropagated) Color(0xFF15803D) else Color(0xFFB91C1C)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersContent(
    viewModel: QuizViewModel,
    modifier: Modifier = Modifier
) {
    val users by viewModel.allRegisteredUsers.collectAsState()
    val filter by viewModel.adminUsersFilter.collectAsState()
    val hasMore by viewModel.adminUsersHasMore.collectAsState()
    val attemptsMap by viewModel.adminUserAttemptsMap.collectAsState()
    val loading by viewModel.adminUsersLoading.collectAsState()
    val error by viewModel.adminUsersError.collectAsState()

    var searchQueryInput by remember(filter.searchQuery) { mutableStateOf(filter.searchQuery) }
    var expandedUserId by remember { mutableStateOf<String?>(null) }
    var attemptToDelete by remember { mutableStateOf<QuizAttempt?>(null) }
    var userToResetProgress by remember { mutableStateOf<RegisteredUser?>(null) }

    DisposableEffect(Unit) {
        viewModel.fetchAdminUsersPage(reset = true)
        onDispose {
            viewModel.clearAllUserAttemptsListeners()
        }
    }

    val totalUsers = users.size
    val studentCount = users.count { it.role.equals("student", ignoreCase = true) || it.role.equals("user", ignoreCase = true) }
    val adminCount = users.count { it.role.equals("admin", ignoreCase = true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Stats Summary Cards Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Loaded Users", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(text = "$totalUsers", fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E8FF))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Students", fontSize = 11.sp, color = Color(0xFF7E22CE), fontWeight = FontWeight.Bold)
                    Text(text = "$studentCount", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF6B21A8))
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Admins", fontSize = 11.sp, color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                    Text(text = "$adminCount", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFFB45309))
                }
            }
        }

        // Search Bar & Search Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQueryInput,
                onValueChange = { searchQueryInput = it },
                placeholder = { Text("Search UID, Email, Name...", fontSize = 12.sp) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("admin_users_search_input"),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Users") },
                trailingIcon = {
                    if (searchQueryInput.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQueryInput = ""
                            viewModel.setAdminUsersFilter(filter.copy(searchQuery = ""))
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = { viewModel.setAdminUsersFilter(filter.copy(searchQuery = searchQueryInput)) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Search")
            }
        }

        // Filter Chips Panel
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Status Filter Row
                Text("Account Status:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL" to "All", "ACTIVE" to "Active", "BLOCKED" to "Blocked").forEach { (code, label) ->
                        FilterChip(
                            selected = filter.statusFilter == code,
                            onClick = { viewModel.setAdminUsersFilter(filter.copy(statusFilter = code)) },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                // Registration Date & Sort Order
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Registration Date:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = filter.dateRange == "ALL",
                                onClick = { viewModel.setAdminUsersFilter(filter.copy(dateRange = "ALL")) },
                                label = { Text("All Time", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = filter.dateRange == "TODAY",
                                onClick = { viewModel.setAdminUsersFilter(filter.copy(dateRange = "TODAY")) },
                                label = { Text("Today", fontSize = 10.sp) }
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sort Order:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = filter.sortOrder == "NEWEST",
                                onClick = { viewModel.setAdminUsersFilter(filter.copy(sortOrder = "NEWEST")) },
                                label = { Text("Newest", fontSize = 10.sp) }
                            )
                            FilterChip(
                                selected = filter.sortOrder == "OLDEST",
                                onClick = { viewModel.setAdminUsersFilter(filter.copy(sortOrder = "OLDEST")) },
                                label = { Text("Oldest", fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }
        }

        if (error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Error: $error",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Users Directory List
        if (loading && users.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else if (users.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = "No Users",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No users match current filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                users.forEach { user ->
                    val isExpanded = expandedUserId == user.uid
                    UserDirectoryItemCard(
                        user = user,
                        isExpanded = isExpanded,
                        attempts = attemptsMap[user.uid] ?: emptyList(),
                        onExpandToggle = {
                            if (isExpanded) {
                                expandedUserId = null
                                viewModel.stopObservingAttemptsForUser(user.uid)
                            } else {
                                expandedUserId = user.uid
                                viewModel.loadAttemptsForUser(user.uid, user.email)
                            }
                        },
                        onDeleteAttempt = { attempt ->
                            attemptToDelete = attempt
                        },
                        onResetAllProgress = {
                            userToResetProgress = user
                        },
                        viewModel = viewModel
                    )
                }

                if (hasMore) {
                    Button(
                        onClick = { viewModel.fetchAdminUsersPage(reset = false) },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Load Next Batch", fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // Double Confirmation Dialog to prevent accidental deletion
    if (attemptToDelete != null) {
        val attempt = attemptToDelete!!
        AlertDialog(
            onDismissRequest = { attemptToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Permanent Delete Attempt", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Are you sure you want to permanently delete this attempt?",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Quiz: ${attempt.quizTitle}\nScore: ${attempt.score.toInt()}/${attempt.totalQuestions} (${(attempt.score / attempt.totalQuestions * 100).toInt()}%)\nDate: ${java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(attempt.dateMillis))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                            .fillMaxWidth()
                    )
                    Text(
                        text = "⚠️ This action is final. This record will be permanently deleted from Firestore database. It cannot be recovered or restored.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val matchedUser = users.find {
                            it.uid == attempt.userId ||
                            it.email.equals(attempt.userId, ignoreCase = true) ||
                            it.uid.equals(attempt.userId.replace(".", "_"), ignoreCase = true)
                        }
                        val targetEmail = matchedUser?.email ?: if (attempt.userId.contains("@")) attempt.userId else ""
                        viewModel.deleteSingleAttempt(attempt, targetEmail)
                        attemptToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { attemptToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation dialog to safely reset/delete all progress for a user
    if (userToResetProgress != null) {
        val targetUser = userToResetProgress!!
        AlertDialog(
            onDismissRequest = { userToResetProgress = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Reset All User Progress", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Are you sure you want to delete and reset ALL progress for ${targetUser.displayName} (${targetUser.email})?",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "This will permanently wipe all completed quiz attempts, test scores, drill analytics, and live progress records from Cloud Firestore and local database. The user's screen will immediately update to 0 attempts.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "⚠️ This action is final and will instantly synchronize with the user's screen.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllAttemptsForUser(targetUser.uid, targetUser.email)
                        userToResetProgress = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_reset_all_progress_button")
                ) {
                    Text("Reset All Progress", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { userToResetProgress = null },
                    modifier = Modifier.testTag("cancel_reset_all_progress_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun UserDirectoryItemCard(
    user: RegisteredUser,
    isExpanded: Boolean,
    attempts: List<QuizAttempt>,
    onExpandToggle: () -> Unit,
    onDeleteAttempt: (QuizAttempt) -> Unit,
    onResetAllProgress: () -> Unit,
    viewModel: QuizViewModel
) {
    val totalQuizzes = attempts.size
    val averageScore = if (attempts.isNotEmpty()) {
        attempts.map { (it.score / it.totalQuestions) * 100 }.average().toInt()
    } else 0

    val dateFormat = remember { java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()) }
    val lastLoginStr = if (user.lastLoginAt > 0) dateFormat.format(java.util.Date(user.lastLoginAt)) else "Never"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_user_card_${user.uid}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else if (user.blocked) Color(0xFFEF4444).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header clickable area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpandToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (user.blocked) Color(0xFFFEE2E2) else if (user.role.equals("admin", ignoreCase = true)) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (user.blocked) Icons.Default.Block else if (user.role.equals("admin", ignoreCase = true)) Icons.Default.Shield else Icons.Default.Person,
                                contentDescription = "User Avatar",
                                tint = if (user.blocked) Color(0xFFEF4444) else if (user.role.equals("admin", ignoreCase = true)) Color(0xFFD97706) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = user.displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (user.blocked) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (user.role.equals("admin", ignoreCase = true)) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = user.role.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (user.role.equals("admin", ignoreCase = true)) Color(0xFFB45309) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (user.blocked) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFFEE2E2)
                                ) {
                                    Text(
                                        text = "BLOCKED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF991B1B),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = user.email,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Last active",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = lastLoginStr,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand/Collapse details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Brief User Stats Overview
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Quizzes Done", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$totalQuizzes", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Avg Accuracy", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$averageScore%", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Admin Action Controls (Export PDF, Export CSV, Block/Unblock User)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Export CSV Button
                        Button(
                            onClick = { viewModel.exportUserProgressCSV(user, attempts) },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("admin_export_csv_${user.uid}"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "CSV",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Export PDF Button
                        Button(
                            onClick = { viewModel.exportUserProgressPDF(user, attempts) },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("admin_export_pdf_${user.uid}"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "PDF",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Block/Unblock Button
                        val isSelf = user.email.lowercase() == viewModel.currentUserEmail.value?.lowercase()
                        if (!isSelf) {
                            Button(
                                onClick = { viewModel.toggleUserBlockedStatus(user) },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(36.dp)
                                    .testTag("admin_block_user_${user.uid}"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (user.blocked) Color(0xFF10B981) else Color(0xFFEF4444),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (user.blocked) Icons.Default.LockOpen else Icons.Default.Block,
                                    contentDescription = if (user.blocked) "Unblock" else "Block",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (user.blocked) "Unblock User" else "Block User",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Developer/Admin Progress Manipulation Controls (Reset Progress, Simulate Progress)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Generate Fake/Mock Progress Button
                        Button(
                            onClick = { viewModel.generateMockAttemptsForUser(user.uid, user.email) },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("admin_simulate_progress_${user.uid}"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Simulate",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Simulate Progress", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Clear/Delete All Progress Button
                        Button(
                            onClick = { onResetAllProgress() },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("admin_clear_progress_${user.uid}"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Reset Progress",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Progress", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Individual Quiz Attempts History",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (attempts.isEmpty()) {
                        Text(
                            text = "No recorded quiz attempts found for this user.",
                            fontSize = 11.sp,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            attempts.forEach { attempt ->
                                QuizAttemptHistoryRow(
                                    attempt = attempt,
                                    onDelete = { onDeleteAttempt(attempt) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuizAttemptHistoryRow(
    attempt: QuizAttempt,
    onDelete: () -> Unit
) {
    val dateTimeFormat = remember { java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault()) }
    val formattedDate = dateTimeFormat.format(java.util.Date(attempt.dateMillis))
    val pct = if (attempt.totalQuestions > 0) (attempt.score / attempt.totalQuestions * 100).toInt() else 0

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attempt.quizTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = attempt.categoryName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "•  $formattedDate",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Score Box
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        pct >= 80 -> Color(0xFFDCFCE7) // success
                        pct >= 50 -> Color(0xFFFEF3C7) // warning
                        else -> Color(0xFFFEE2E2) // error
                    }
                ) {
                    Text(
                        text = "${attempt.score.toInt()}/${attempt.totalQuestions} ($pct%)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            pct >= 80 -> Color(0xFF15803D)
                            pct >= 50 -> Color(0xFFB45309)
                            else -> Color(0xFFB91C1C)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Granular trash button to delete this single attempt
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("delete_attempt_button_${attempt.documentId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Single Attempt",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BlockedAccountScreen(
    viewModel: QuizViewModel,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFFFEE2E2), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "Blocked",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Text(
                        text = "Account Suspended",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Text(
                        text = "Your account has been restricted by an administrator. You currently do not have permission to access Speed English quizzes or study modules.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Support: sd504212@gmail.com",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            viewModel.signOut()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = "Sign Out", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out / Switch Account", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// DYNAMIC CONTACT US & PRIVACY POLICY DIALOGS & MANAGERS
// ==========================================

@Composable
fun UserPrivacyPolicyDialog(
    viewModel: QuizViewModel,
    onDismiss: () -> Unit
) {
    val privacyPolicy by viewModel.privacyPolicy.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PrivacyTip,
                    contentDescription = "Privacy Policy",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = privacyPolicy.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = privacyPolicy.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun UserContactUsDialog(
    viewModel: QuizViewModel,
    onDismiss: () -> Unit
) {
    val contactMethods by viewModel.contactMethods.collectAsState()
    val enabledContacts = remember(contactMethods) { contactMethods.filter { it.isEnabled }.sortedBy { it.displayOrder } }
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SupportAgent,
                    contentDescription = "Contact Us",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Contact Us",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Reach out to us through any of the channels below. Tap a contact method to launch:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                if (enabledContacts.isEmpty()) {
                    Text(
                        text = "No contact methods are currently available. Please check back later.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    enabledContacts.forEach { contact ->
                        val platformIcon = ContactPlatformHelper.getPlatformIcon(contact.platform)
                        val platformColor = ContactPlatformHelper.getPlatformColor(contact.platform)

                        Card(
                            onClick = {
                                ContactPlatformHelper.launchContactIntent(context, contact)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(platformColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = platformIcon,
                                        contentDescription = contact.platform,
                                        tint = platformColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (contact.description.isNotBlank()) {
                                        Text(
                                            text = contact.description,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = contact.value,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = "Open Link",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AboutSpeedEnglishDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "About",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "About Speed English",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.icon_option_five_1782049925584),
                        contentDescription = "Speed English Logo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Speed English",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "SSC & Banking Master Drills • v1.0.0",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Speed English is a comprehensive mobile learning application engineered for competitive exam aspirants (SSC, Banking, Railway, Defense). Master English grammar, idioms, vocabulary, and spotting errors with timed drills and live performance analytics.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminContactUsManagementContent(viewModel: QuizViewModel) {
    val contactMethods by viewModel.contactMethods.collectAsState()

    var selectedPlatform by remember { mutableStateOf("WhatsApp") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var contactValue by remember { mutableStateOf("") }
    var displayOrderText by remember { mutableStateOf((contactMethods.size + 1).toString()) }
    var isEnabled by remember { mutableStateOf(true) }
    var editingDocId by remember { mutableStateOf<String?>(null) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    var contactToDelete by remember { mutableStateOf<com.example.data.ContactMethod?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    fun onPlatformSelect(platform: String) {
        selectedPlatform = platform
        if (title.isBlank()) {
            title = when (platform) {
                "WhatsApp" -> "WhatsApp Support"
                "Telegram" -> "Telegram Channel"
                "Gmail" -> "Customer Support Email"
                "Website" -> "Official Website"
                "Facebook" -> "Facebook Page"
                "Instagram" -> "Instagram Profile"
                "X" -> "X (Twitter) Handle"
                "YouTube" -> "YouTube Channel"
                "Phone" -> "Phone Support"
                "SMS" -> "SMS Helpline"
                else -> "$platform Support"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SupportAgent,
                            contentDescription = "Contact Us",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (editingDocId == null) "Add New Contact Method" else "Edit Contact Method",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (editingDocId != null) {
                        TextButton(
                            onClick = {
                                editingDocId = null
                                title = ""
                                description = ""
                                contactValue = ""
                                selectedPlatform = "WhatsApp"
                                displayOrderText = (contactMethods.size + 1).toString()
                                isEnabled = true
                            }
                        ) {
                            Text("Cancel Edit", fontSize = 12.sp)
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = isDropdownExpanded,
                    onExpandedChange = { isDropdownExpanded = !isDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedPlatform,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Platform / Icon") },
                        leadingIcon = {
                            Icon(
                                imageVector = ContactPlatformHelper.getPlatformIcon(selectedPlatform),
                                contentDescription = selectedPlatform,
                                tint = ContactPlatformHelper.getPlatformColor(selectedPlatform),
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        ContactPlatformHelper.PLATFORMS.forEach { platform ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = ContactPlatformHelper.getPlatformIcon(platform),
                                            contentDescription = platform,
                                            tint = ContactPlatformHelper.getPlatformColor(platform),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(platform, fontSize = 13.sp)
                                    }
                                },
                                onClick = {
                                    onPlatformSelect(platform)
                                    isDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Display Title") },
                    placeholder = { Text("e.g. WhatsApp Support") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("e.g. Chat with us for instant help") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                val valueLabel = when (selectedPlatform.lowercase()) {
                    "whatsapp", "phone", "sms" -> "Phone Number (with country code)"
                    "gmail", "email" -> "Email Address"
                    "website" -> "Website URL"
                    "telegram", "instagram", "x", "twitter", "youtube", "github", "linkedin" -> "Username / Handle or Full URL"
                    else -> "URL / Account ID / Contact Detail"
                }

                OutlinedTextField(
                    value = contactValue,
                    onValueChange = { contactValue = it },
                    label = { Text(valueLabel) },
                    placeholder = { Text("e.g. +919876543210 or support@speedenglish.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = displayOrderText,
                        onValueChange = { if (it.all { char -> char.isDigit() }) displayOrderText = it },
                        label = { Text("Display Order") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 8.dp)
                    ) {
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isEnabled) "Enabled" else "Disabled", fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = {
                        if (title.isBlank() || contactValue.isBlank()) {
                            statusMsg = "Please fill in Title and Contact Value"
                            return@Button
                        }
                        val order = displayOrderText.toIntOrNull() ?: (contactMethods.size + 1)
                        val item = com.example.data.ContactMethod(
                            documentId = editingDocId ?: "",
                            platform = selectedPlatform,
                            title = title.trim(),
                            description = description.trim(),
                            value = contactValue.trim(),
                            displayOrder = order,
                            isEnabled = isEnabled
                        )
                        viewModel.saveContactMethod(item) { success, err ->
                            if (success) {
                                statusMsg = "Contact method saved successfully!"
                                editingDocId = null
                                title = ""
                                description = ""
                                contactValue = ""
                                selectedPlatform = "WhatsApp"
                                displayOrderText = (contactMethods.size + 1).toString()
                                isEnabled = true
                            } else {
                                statusMsg = "Error saving: ${err ?: "Unknown error"}"
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (editingDocId == null) Icons.Default.Add else Icons.Default.Save,
                        contentDescription = "Save",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (editingDocId == null) "Add Contact Method" else "Update Contact Method", fontWeight = FontWeight.Bold)
                }

                if (statusMsg != null) {
                    Text(
                        text = statusMsg ?: "",
                        fontSize = 11.sp,
                        color = if (statusMsg?.contains("Error") == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Text(
            text = "CONFIGURED CONTACT METHODS (${contactMethods.size})",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )

        if (contactMethods.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Text(
                    text = "No contact methods created yet.",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            contactMethods.forEach { contact ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (contact.isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ContactPlatformHelper.getPlatformColor(contact.platform).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = ContactPlatformHelper.getPlatformIcon(contact.platform),
                                contentDescription = contact.platform,
                                tint = ContactPlatformHelper.getPlatformColor(contact.platform),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = contact.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "#${contact.displayOrder}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            if (contact.description.isNotBlank()) {
                                Text(
                                    text = contact.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = contact.value,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Switch(
                            checked = contact.isEnabled,
                            onCheckedChange = { checked ->
                                viewModel.toggleContactMethodEnabled(contact.documentId, checked)
                            },
                            modifier = Modifier.scale(0.85f)
                        )

                        IconButton(
                            onClick = {
                                editingDocId = contact.documentId
                                selectedPlatform = contact.platform
                                title = contact.title
                                description = contact.description
                                contactValue = contact.value
                                displayOrderText = contact.displayOrder.toString()
                                isEnabled = contact.isEnabled
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { contactToDelete = contact },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (contactToDelete != null) {
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = { Text("Delete Contact Method?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${contactToDelete?.title}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val target = contactToDelete
                        if (target != null) {
                            viewModel.deleteContactMethod(target.documentId) { success, _ ->
                                if (success) {
                                    statusMsg = "Contact method deleted."
                                }
                            }
                        }
                        contactToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AdminPrivacyPolicyManagementContent(viewModel: QuizViewModel) {
    val privacyPolicy by viewModel.privacyPolicy.collectAsState()

    var policyTitle by remember(privacyPolicy) { mutableStateOf(privacyPolicy.title) }
    var policyContent by remember(privacyPolicy) { mutableStateOf(privacyPolicy.content) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PrivacyTip,
                        contentDescription = "Privacy Policy",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Privacy Policy Management",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "Edit and publish the Privacy Policy. Changes will take effect immediately in real-time across all user sessions.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = policyTitle,
                    onValueChange = { policyTitle = it },
                    label = { Text("Privacy Policy Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = policyContent,
                    onValueChange = { policyContent = it },
                    label = { Text("Policy Text Content") },
                    minLines = 10,
                    maxLines = 18,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Button(
                    onClick = {
                        if (policyTitle.isBlank() || policyContent.isBlank()) {
                            statusMsg = "Title and Content cannot be empty."
                            return@Button
                        }
                        isSaving = true
                        viewModel.savePrivacyPolicy(policyTitle, policyContent) { success, err ->
                            isSaving = false
                            if (success) {
                                statusMsg = "Privacy Policy published successfully!"
                            } else {
                                statusMsg = "Error saving: ${err ?: "Unknown error"}"
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isSaving) "Publishing..." else "Save & Publish Privacy Policy", fontWeight = FontWeight.Bold)
                }

                if (statusMsg != null) {
                    Text(
                        text = statusMsg ?: "",
                        fontSize = 11.sp,
                        color = if (statusMsg?.contains("Error") == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun AdminSocialDestinationManagementContent(viewModel: QuizViewModel) {
    val destination by viewModel.socialDestination.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedPlatform by remember(destination) { mutableStateOf(destination.platform.ifBlank { "telegram" }) }
    var groupUrl by remember(destination) { mutableStateOf(destination.url) }
    var isLinkEnabled by remember(destination) { mutableStateOf(destination.enabled) }
    var isSaving by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Active Destination Status Preview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (destination.url.isNotBlank() && destination.enabled) {
                    Color(0xFFECFDF5)
                } else if (destination.url.isNotBlank()) {
                    Color(0xFFFFFBEB)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                }
            ),
            border = BorderStroke(
                1.dp,
                if (destination.url.isNotBlank() && destination.enabled) Color(0xFFA7F3D0)
                else if (destination.url.isNotBlank()) Color(0xFFFDE68A)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Destination Link",
                            tint = if (destination.url.isNotBlank() && destination.enabled) Color(0xFF059669) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "User Post Destination",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Status Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (destination.url.isNotBlank() && destination.enabled) Color(0xFF10B981)
                                else if (destination.url.isNotBlank()) Color(0xFFF59E0B)
                                else Color.Gray
                    ) {
                        Text(
                            text = if (destination.url.isNotBlank() && destination.enabled) "Enabled"
                                   else if (destination.url.isNotBlank()) "Disabled"
                                   else "Not Configured",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (destination.url.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Platform: ",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (destination.platform.equals("facebook", ignoreCase = true)) "Facebook Group" else "Telegram Group/Channel",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(destination.url))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Invalid URL", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text(
                            text = "Link: ",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = destination.url,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open Link",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else {
                    Text(
                        text = "No destination link configured. The Quiz Result 'Post' button will notify users that posting destination is not configured.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Configuration Form Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configure External Group Link",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Select the target platform and provide your official group/channel URL. When users finish a quiz and tap 'Post', they will be redirected directly to this group with their scorecard.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Platform Choice
                Text(
                    text = "Select Platform",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isTele = selectedPlatform.equals("telegram", ignoreCase = true)
                    val isFb = selectedPlatform.equals("facebook", ignoreCase = true)

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedPlatform = "telegram" },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isTele) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (isTele) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Telegram",
                                tint = if (isTele) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Telegram",
                                fontWeight = if (isTele) FontWeight.Bold else FontWeight.Normal,
                                color = if (isTele) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedPlatform = "facebook" },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isFb) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (isFb) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Groups,
                                contentDescription = "Facebook",
                                tint = if (isFb) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Facebook",
                                fontWeight = if (isFb) FontWeight.Bold else FontWeight.Normal,
                                color = if (isFb) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Group Link Field
                OutlinedTextField(
                    value = groupUrl,
                    onValueChange = { groupUrl = it },
                    label = {
                        Text(
                            if (selectedPlatform == "facebook") "Facebook Group Link (e.g. https://facebook.com/groups/...)"
                            else "Telegram Group/Channel Link (e.g. https://t.me/...)",
                            fontSize = 11.sp
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                )

                // Enable/Disable Toggle Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Link Active Status",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (isLinkEnabled) "Active — Users can tap Post on Quiz Result" else "Disabled — Users will be informed link is unavailable",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isLinkEnabled,
                        onCheckedChange = { isLinkEnabled = it }
                    )
                }

                // Save Button
                Button(
                    onClick = {
                        if (groupUrl.isBlank()) {
                            statusMsg = "Please enter a valid group link."
                            return@Button
                        }
                        if (!groupUrl.startsWith("http://") && !groupUrl.startsWith("https://") && !groupUrl.startsWith("tg://")) {
                            statusMsg = "Link must start with https:// or http://"
                            return@Button
                        }
                        isSaving = true
                        viewModel.saveSocialDestination(
                            platform = selectedPlatform,
                            url = groupUrl.trim(),
                            enabled = isLinkEnabled
                        ) { success, err ->
                            isSaving = false
                            if (success) {
                                statusMsg = "✓ Destination link updated successfully!"
                            } else {
                                statusMsg = "Error saving: ${err ?: "Unknown error"}"
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isSaving) "Saving..." else "Save / Update Destination", fontWeight = FontWeight.Bold)
                }

                // Quick Action Buttons Row (Enable/Disable & Delete)
                if (destination.url.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isSaving = true
                                val newEnabled = !destination.enabled
                                viewModel.toggleSocialDestinationEnabled(newEnabled) { success, err ->
                                    isSaving = false
                                    if (success) {
                                        isLinkEnabled = newEnabled
                                        statusMsg = if (newEnabled) "✓ Destination Enabled!" else "✓ Destination Disabled."
                                    } else {
                                        statusMsg = "Error: ${err ?: "Unknown"}"
                                    }
                                }
                            },
                            enabled = !isSaving,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (destination.enabled) "Disable Link" else "Enable Link", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = !isSaving,
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Link", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                }

                if (statusMsg != null) {
                    Text(
                        text = statusMsg ?: "",
                        fontSize = 11.sp,
                        color = if (statusMsg?.contains("Error") == true || statusMsg?.contains("Please") == true || statusMsg?.contains("must") == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Destination Link?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove the external group destination link? The Quiz Result Post button will indicate no destination is configured.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteSocialDestination { success, err ->
                            if (success) {
                                groupUrl = ""
                                isLinkEnabled = false
                                statusMsg = "✓ Destination link deleted successfully."
                            } else {
                                statusMsg = "Error deleting: ${err ?: "Unknown error"}"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}


