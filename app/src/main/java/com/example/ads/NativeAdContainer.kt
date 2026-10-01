package com.example.ads

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Reusable Native Ad Container.
 *
 * Guarantees zero UI jumping by pre-allocating an identical-height container with
 * a subtle shimmer skeleton during [NativeAdState.Loading], seamlessly swapping to
 * the registered [NativeAdView] when [NativeAdState.Loaded], rendering a Speed Math-style
 * preview when [NativeAdState.TestPreview], and gracefully collapsing on [NativeAdState.Failed].
 */
@Composable
fun NativeAdContainer(
    placement: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val adState by NativeAdManager.getStateFlow(placement).collectAsState()

    // Request ad on entry; uses cached preloaded ad if already available
    LaunchedEffect(placement) {
        NativeAdManager.loadAdForPlacement(context, placement)
    }

    val containerHeight = 160.dp

    when (val state = adState) {
        is NativeAdState.Empty -> {
            // Hidden; no space allocated
        }
        is NativeAdState.Loading -> {
            // Pre-allocated skeleton container prevents any UI jumping when the ad finishes loading
            NativeAdSkeletonCard(
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .height(containerHeight)
                    .testTag("native_ad_skeleton_$placement")
            )
        }
        is NativeAdState.Loaded -> {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .height(containerHeight)
                    .testTag("native_ad_card_$placement"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                NativeAdAndroidView(
                    nativeAd = state.nativeAd,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        is NativeAdState.TestPreview -> {
            // Speed Math-style Native Ad Preview for reliable test mode display
            NativeAdPreviewCard(
                placement = placement,
                headline = state.headline,
                body = state.body,
                cta = state.callToAction,
                advertiser = state.advertiser,
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .height(containerHeight)
                    .testTag("native_ad_preview_$placement")
            )
        }
        is NativeAdState.Failed -> {
            // Gracefully collapses without breaking screen or showing raw error text
        }
        is NativeAdState.Destroyed -> {
            // Hidden
        }
    }
}

@Composable
fun NativeAdAndroidView(
    nativeAd: NativeAd,
    modifier: Modifier = Modifier
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = surfaceColor.red * 0.299f + surfaceColor.green * 0.587f + surfaceColor.blue * 0.114f < 0.5f
    val textColor = if (isDark) 0xFFF8FAFC.toInt() else 0xFF0F172A.toInt()
    val secondaryTextColor = if (isDark) 0xFF94A3B8.toInt() else 0xFF475569.toInt()

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val inflater = LayoutInflater.from(ctx)
            val adView = inflater.inflate(R.layout.layout_speed_math_native_ad, null, false) as NativeAdView
            // Explicitly set MatchParent layout params to prevent wrap_content measurement collapse
            adView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            populateNativeAdView(nativeAd, adView, textColor, secondaryTextColor)
            adView
        },
        update = { adView ->
            populateNativeAdView(nativeAd, adView, textColor, secondaryTextColor)
        }
    )
}

private fun populateNativeAdView(
    nativeAd: NativeAd,
    adView: NativeAdView,
    textColor: Int,
    secondaryTextColor: Int
) {
    // 1. Headline
    val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
    headlineView.text = nativeAd.headline ?: "Recommended App"
    headlineView.setTextColor(textColor)
    adView.headlineView = headlineView

    // 2. Body
    val bodyView = adView.findViewById<TextView>(R.id.ad_body)
    if (nativeAd.body != null) {
        bodyView.text = nativeAd.body
        bodyView.setTextColor(secondaryTextColor)
        bodyView.visibility = View.VISIBLE
    } else {
        bodyView.visibility = View.GONE
    }
    adView.bodyView = bodyView

    // 3. Call to Action Button
    val ctaView = adView.findViewById<Button>(R.id.ad_call_to_action)
    ctaView.text = nativeAd.callToAction ?: "Install"
    ctaView.visibility = View.VISIBLE
    adView.callToActionView = ctaView

    // 4. App Icon
    val iconView = adView.findViewById<ImageView>(R.id.ad_app_icon)
    if (nativeAd.icon?.drawable != null) {
        iconView.setImageDrawable(nativeAd.icon?.drawable)
        iconView.visibility = View.VISIBLE
    } else {
        iconView.visibility = View.GONE
    }
    adView.iconView = iconView

    // 5. Media View
    val mediaContainer = adView.findViewById<FrameLayout>(R.id.ad_media_container)
    val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
    if (nativeAd.mediaContent != null) {
        mediaView.mediaContent = nativeAd.mediaContent
        mediaContainer.visibility = View.VISIBLE
    } else {
        mediaContainer.visibility = View.GONE
    }
    adView.mediaView = mediaView

    // 6. Advertiser / Star Rating / Store
    val advertiserView = adView.findViewById<TextView>(R.id.ad_advertiser)
    val ratingStr = nativeAd.starRating?.let { "${it} ★ " } ?: ""
    val secondaryInfo = when {
        ratingStr.isNotEmpty() && nativeAd.store != null -> "$ratingStr• ${nativeAd.store}"
        ratingStr.isNotEmpty() -> "${ratingStr}Google Play"
        nativeAd.advertiser != null -> nativeAd.advertiser
        nativeAd.store != null -> nativeAd.store
        else -> "Google Play"
    }
    advertiserView.text = secondaryInfo
    advertiserView.setTextColor(secondaryTextColor)
    adView.advertiserView = advertiserView

    // Complete Google AdMob NativeAd registration
    adView.setNativeAd(nativeAd)
}

/**
 * Speed Math-style Native Ad Card for Test & Demo Mode.
 * Mirrors the exact visual style, spacing, MediaView graphic, App Icon, Headline, Star Rating, and CTA button.
 */
@Composable
fun NativeAdPreviewCard(
    placement: String,
    headline: String,
    body: String,
    cta: String,
    advertiser: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val openPlayStore: () -> Unit = {
        try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.gms.ads")
            ).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("NativeAdPreviewCard", "Error opening store: ${e.message}")
        }
    }

    var isMuted by remember { mutableStateOf(true) }

    Card(
        onClick = openPlayStore,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Media View simulation (Speed Math style video/image card)
            Box(
                modifier = Modifier
                    .width(135.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF0F172A),
                                Color(0xFF1E1B4B),
                                Color(0xFF312E81)
                            )
                        )
                    )
            ) {
                // Top-left Speaker Mute Icon (Speed Math video indicator)
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable { isMuted = !isMuted }
                        .align(Alignment.TopStart),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Center Reel / Video Content Indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4F46E5).copy(alpha = 0.35f))
                            .border(BorderStroke(1.dp, Color(0xFF818CF8)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Video Ad",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "VIDEO AD",
                        color = Color(0xFFFDE047),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.5.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right Content Area (Headline, Ratings, Body, CTA)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                // Top row: App Icon + Headline + Ad Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF4F46E5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = headline.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = headline,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = advertiser,
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Official Ad attribution badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFEF08A))
                            .border(BorderStroke(0.5.dp, Color(0xFFEAB308)), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "Ad",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF854D0E)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = body,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Call to action button
                androidx.compose.material3.Button(
                    onClick = openPlayStore,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4F46E5)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = cta,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun NativeAdSkeletonCard(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "ad_skeleton_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ad_skeleton_alpha"
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left media placeholder
            Box(
                modifier = Modifier
                    .width(135.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Right content placeholders
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                // Top row with icon & badge placeholder
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.4f)
                                .height(9.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Body lines
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // CTA button placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                )
            }
        }
    }
}
