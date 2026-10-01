package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.example.data.ContactMethod

object ContactPlatformHelper {

    val PLATFORMS = listOf(
        "WhatsApp",
        "Telegram",
        "Gmail",
        "Website",
        "Facebook",
        "Instagram",
        "X",
        "YouTube",
        "Discord",
        "LinkedIn",
        "Reddit",
        "Messenger",
        "Signal",
        "Snapchat",
        "TikTok",
        "Pinterest",
        "GitHub",
        "Skype",
        "Phone",
        "SMS",
        "Other"
    )

    // Official Brand ImageVector definitions with accurate curveTo pathing
    val WhatsAppIcon: ImageVector = ImageVector.Builder(
        name = "WhatsApp", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).path(fill = SolidColor(Color(0xFF25D366))) {
        moveTo(12.0f, 2.0f)
        curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
        curveTo(2.0f, 13.81f, 2.49f, 15.51f, 3.34f, 17.0f)
        lineTo(2.0f, 22.0f)
        lineTo(7.13f, 20.69f)
        curveTo(8.58f, 21.53f, 10.24f, 22.0f, 12.0f, 22.0f)
        curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
        curveTo(22.0f, 6.48f, 17.52f, 2.0f, 12.0f, 2.0f)
        close()
        moveTo(16.5f, 15.5f)
        curveTo(16.2f, 16.3f, 15.0f, 16.9f, 14.2f, 17.0f)
        curveTo(13.6f, 17.1f, 12.8f, 17.1f, 10.1f, 16.0f)
        curveTo(6.7f, 14.6f, 4.4f, 11.1f, 4.2f, 10.9f)
        curveTo(4.1f, 10.7f, 2.9f, 9.1f, 2.9f, 7.5f)
        curveTo(2.9f, 5.9f, 3.7f, 5.1f, 4.0f, 4.8f)
        curveTo(4.3f, 4.5f, 4.7f, 4.5f, 5.0f, 4.5f)
        curveTo(5.2f, 4.5f, 5.4f, 4.5f, 5.6f, 4.5f)
        curveTo(5.8f, 4.5f, 6.1f, 4.4f, 6.4f, 5.1f)
        curveTo(6.7f, 5.8f, 7.4f, 7.5f, 7.5f, 7.7f)
        curveTo(7.6f, 7.9f, 7.6f, 8.1f, 7.5f, 8.3f)
        curveTo(7.4f, 8.5f, 7.3f, 8.7f, 7.1f, 8.9f)
        curveTo(6.9f, 9.1f, 6.7f, 9.4f, 6.5f, 9.6f)
        curveTo(6.3f, 9.8f, 6.1f, 10.0f, 6.4f, 10.5f)
        curveTo(6.7f, 11.0f, 7.7f, 12.6f, 9.1f, 13.9f)
        curveTo(11.0f, 15.6f, 12.5f, 16.1f, 13.0f, 16.3f)
        curveTo(13.5f, 16.5f, 13.8f, 16.4f, 14.1f, 16.1f)
        curveTo(14.4f, 15.8f, 15.4f, 14.6f, 15.7f, 14.1f)
        curveTo(16.0f, 13.6f, 16.3f, 13.7f, 16.7f, 13.8f)
        curveTo(17.1f, 14.0f, 19.2f, 15.0f, 19.6f, 15.2f)
        curveTo(20.0f, 15.4f, 20.3f, 15.5f, 20.4f, 15.7f)
        curveTo(20.5f, 15.9f, 20.5f, 16.9f, 19.7f, 17.5f)
        close()
    }.build()

    val TelegramIcon: ImageVector = ImageVector.Builder(
        name = "Telegram", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).path(fill = SolidColor(Color(0xFF229ED9))) {
        moveTo(12.0f, 2.0f)
        curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
        curveTo(2.0f, 17.52f, 6.48f, 22.0f, 12.0f, 22.0f)
        curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
        curveTo(22.0f, 6.48f, 17.52f, 2.0f, 12.0f, 2.0f)
        close()
        moveTo(16.64f, 8.8f)
        lineTo(14.87f, 17.14f)
        curveTo(14.74f, 17.93f, 14.27f, 18.12f, 13.61f, 17.75f)
        lineTo(10.91f, 15.76f)
        lineTo(9.61f, 17.01f)
        curveTo(9.47f, 17.15f, 9.35f, 17.27f, 9.07f, 17.27f)
        lineTo(9.26f, 13.52f)
        lineTo(16.09f, 7.35f)
        curveTo(16.39f, 7.09f, 16.03f, 6.94f, 15.63f, 7.21f)
        lineTo(7.18f, 12.53f)
        lineTo(3.55f, 11.4f)
        curveTo(2.76f, 11.15f, 2.75f, 10.61f, 3.71f, 10.23f)
        lineTo(17.88f, 4.77f)
        curveTo(18.54f, 4.53f, 19.11f, 4.99f, 18.89f, 5.8f)
        close()
    }.build()

    val GmailIcon: ImageVector = ImageVector.Builder(
        name = "Gmail", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).path(fill = SolidColor(Color(0xFFEA4335))) {
        moveTo(20.0f, 4.0f)
        lineTo(4.0f, 4.0f)
        curveTo(2.9f, 4.0f, 2.01f, 4.9f, 2.01f, 6.0f)
        lineTo(2.0f, 18.0f)
        curveTo(2.0f, 19.1f, 2.9f, 20.0f, 4.0f, 20.0f)
        lineTo(20.0f, 20.0f)
        curveTo(21.1f, 20.0f, 22.0f, 19.1f, 22.0f, 18.0f)
        lineTo(22.0f, 6.0f)
        curveTo(22.0f, 4.9f, 21.1f, 4.0f, 20.0f, 4.0f)
        close()
        moveTo(20.0f, 8.0f)
        lineTo(12.0f, 13.0f)
        lineTo(4.0f, 8.0f)
        lineTo(4.0f, 6.0f)
        lineTo(12.0f, 11.0f)
        lineTo(20.0f, 6.0f)
        lineTo(20.0f, 8.0f)
        close()
    }.build()

    val FacebookIcon: ImageVector = ImageVector.Builder(
        name = "Facebook", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).path(fill = SolidColor(Color(0xFF1877F2))) {
        moveTo(22.0f, 12.0f)
        curveTo(22.0f, 6.48f, 17.52f, 2.0f, 12.0f, 2.0f)
        curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
        curveTo(2.0f, 16.99f, 5.66f, 21.12f, 10.44f, 21.87f)
        lineTo(10.44f, 14.89f)
        lineTo(7.9f, 14.89f)
        lineTo(7.9f, 12.0f)
        lineTo(10.44f, 12.0f)
        lineTo(10.44f, 9.79f)
        curveTo(10.44f, 7.28f, 11.93f, 5.9f, 14.22f, 5.9f)
        curveTo(15.31f, 5.9f, 16.45f, 6.1f, 16.45f, 6.1f)
        lineTo(16.45f, 8.56f)
        lineTo(15.19f, 8.56f)
        curveTo(13.95f, 8.56f, 13.56f, 9.33f, 13.56f, 10.12f)
        lineTo(13.56f, 12.0f)
        lineTo(16.33f, 12.0f)
        lineTo(15.89f, 14.89f)
        lineTo(13.56f, 14.89f)
        lineTo(13.56f, 21.87f)
        curveTo(18.34f, 21.12f, 22.0f, 16.99f, 22.0f, 12.0f)
        close()
    }.build()

    val InstagramIcon: ImageVector = ImageVector.Builder(
        name = "Instagram", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).path(fill = SolidColor(Color(0xFFE4405F))) {
        moveTo(12.0f, 2.16f)
        curveTo(15.2f, 2.16f, 15.58f, 2.18f, 16.85f, 2.23f)
        curveTo(20.2f, 2.38f, 21.62f, 4.38f, 21.77f, 7.15f)
        curveTo(21.82f, 8.42f, 21.84f, 8.8f, 21.84f, 12.0f)
        curveTo(21.84f, 15.2f, 21.82f, 15.58f, 21.77f, 16.85f)
        curveTo(21.62f, 19.61f, 20.2f, 21.62f, 16.85f, 21.77f)
        curveTo(15.58f, 21.82f, 15.2f, 21.84f, 12.0f, 21.84f)
        curveTo(8.8f, 21.84f, 8.42f, 21.82f, 7.15f, 21.77f)
        curveTo(3.79f, 21.62f, 2.38f, 19.62f, 2.23f, 16.85f)
        curveTo(2.18f, 15.58f, 2.16f, 15.2f, 2.16f, 12.0f)
        curveTo(2.16f, 8.8f, 2.18f, 8.42f, 2.23f, 7.15f)
        curveTo(2.38f, 4.39f, 3.79f, 2.38f, 7.15f, 2.23f)
        curveTo(8.42f, 2.18f, 8.8f, 2.16f, 12.0f, 2.16f)
        close()
        moveTo(12.0f, 7.0f)
        curveTo(9.24f, 7.0f, 7.0f, 9.24f, 7.0f, 12.0f)
        curveTo(7.0f, 14.76f, 9.24f, 17.0f, 12.0f, 17.0f)
        curveTo(14.76f, 17.0f, 17.0f, 14.76f, 17.0f, 12.0f)
        curveTo(17.0f, 9.24f, 14.76f, 7.0f, 12.0f, 7.0f)
        close()
        moveTo(12.0f, 15.0f)
        curveTo(10.34f, 15.0f, 9.0f, 13.66f, 9.0f, 12.0f)
        curveTo(9.0f, 10.34f, 10.34f, 9.0f, 12.0f, 9.0f)
        curveTo(13.66f, 9.0f, 15.0f, 10.34f, 15.0f, 12.0f)
        curveTo(15.0f, 13.66f, 13.66f, 15.0f, 12.0f, 15.0f)
        close()
        moveTo(17.25f, 5.5f)
        curveTo(16.56f, 5.5f, 16.0f, 6.06f, 16.0f, 6.75f)
        curveTo(16.0f, 7.44f, 16.56f, 8.0f, 17.25f, 8.0f)
        curveTo(17.94f, 8.0f, 18.5f, 7.44f, 18.5f, 6.75f)
        curveTo(18.5f, 6.06f, 17.94f, 5.5f, 17.25f, 5.5f)
        close()
    }.build()

    val XIcon: ImageVector = ImageVector.Builder(
        name = "X", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).path(fill = SolidColor(Color(0xFF0F172A))) {
        moveTo(18.244f, 2.25f)
        lineTo(21.552f, 2.25f)
        lineTo(14.325f, 10.51f)
        lineTo(22.827f, 21.75f)
        lineTo(16.17f, 21.75f)
        lineTo(10.956f, 14.933f)
        lineTo(5.0f, 21.75f)
        lineTo(1.688f, 21.75f)
        lineTo(9.418f, 12.914f)
        lineTo(1.254f, 2.25f)
        lineTo(8.08f, 2.25f)
        lineTo(12.793f, 8.481f)
        close()
        moveTo(17.083f, 19.77f)
        lineTo(18.916f, 19.77f)
        lineTo(7.084f, 4.126f)
        lineTo(5.117f, 4.126f)
        close()
    }.build()

    val YouTubeIcon: ImageVector = ImageVector.Builder(
        name = "YouTube", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).path(fill = SolidColor(Color(0xFFFF0000))) {
        moveTo(21.58f, 7.19f)
        curveTo(21.33f, 6.26f, 20.6f, 5.52f, 19.67f, 5.27f)
        curveTo(17.99f, 4.81f, 12.0f, 4.81f, 12.0f, 4.81f)
        curveTo(12.0f, 4.81f, 6.01f, 4.81f, 4.33f, 5.27f)
        curveTo(3.4f, 5.52f, 2.67f, 6.26f, 2.42f, 7.19f)
        curveTo(1.96f, 8.87f, 1.96f, 12.0f, 1.96f, 12.0f)
        curveTo(1.96f, 12.0f, 1.96f, 15.13f, 2.42f, 16.81f)
        curveTo(2.67f, 17.74f, 3.4f, 18.48f, 4.33f, 18.73f)
        curveTo(6.01f, 19.19f, 12.0f, 19.19f, 12.0f, 19.19f)
        curveTo(12.0f, 19.19f, 17.99f, 19.19f, 19.67f, 18.73f)
        curveTo(20.6f, 18.48f, 21.33f, 17.74f, 21.58f, 16.81f)
        curveTo(22.04f, 15.13f, 22.04f, 12.0f, 22.04f, 12.0f)
        curveTo(22.04f, 12.0f, 22.04f, 8.87f, 21.58f, 7.19f)
        close()
        moveTo(10.0f, 15.0f)
        lineTo(10.0f, 9.0f)
        lineTo(15.2f, 12.0f)
        lineTo(10.0f, 15.0f)
        close()
    }.build()

    val DiscordIcon: ImageVector = ImageVector.Builder(
        name = "Discord", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).path(fill = SolidColor(Color(0xFF5865F2))) {
        moveTo(20.317f, 4.37f)
        curveTo(18.758f, 3.65f, 17.085f, 3.12f, 15.333f, 2.82f)
        curveTo(15.111f, 3.19f, 14.857f, 3.73f, 14.68f, 4.15f)
        curveTo(12.821f, 3.87f, 10.975f, 3.87f, 9.141f, 4.15f)
        curveTo(8.963f, 3.73f, 8.709f, 3.19f, 8.487f, 2.82f)
        curveTo(6.732f, 3.12f, 5.056f, 3.65f, 3.498f, 4.37f)
        curveTo(0.354f, 9.07f, -0.493f, 13.65f, 0.222f, 18.17f)
        curveTo(2.302f, 19.71f, 4.316f, 20.64f, 6.291f, 21.25f)
        curveTo(6.783f, 20.58f, 7.228f, 19.86f, 7.618f, 19.11f)
        curveTo(6.901f, 18.84f, 6.216f, 18.5f, 5.564f, 18.1f)
        curveTo(5.733f, 17.97f, 5.898f, 17.84f, 6.059f, 17.71f)
        curveTo(9.992f, 19.53f, 14.25f, 19.53f, 18.125f, 17.71f)
        curveTo(18.287f, 17.84f, 18.452f, 17.97f, 18.621f, 18.1f)
        curveTo(17.967f, 18.5f, 17.281f, 18.84f, 16.564f, 19.11f)
        curveTo(16.955f, 19.86f, 17.399f, 20.58f, 17.892f, 21.25f)
        curveTo(19.868f, 20.64f, 21.883f, 19.71f, 23.963f, 18.17f)
        curveTo(24.819f, 12.37f, 22.564f, 7.83f, 20.317f, 4.37f)
        close()
        moveTo(8.02f, 15.33f)
        curveTo(6.832f, 15.33f, 5.856f, 14.23f, 5.856f, 12.89f)
        curveTo(5.856f, 11.55f, 6.811f, 10.46f, 8.02f, 10.46f)
        curveTo(9.239f, 10.46f, 10.204f, 11.56f, 10.183f, 12.89f)
        curveTo(10.183f, 14.23f, 9.229f, 15.33f, 8.02f, 15.33f)
        close()
        moveTo(16.02f, 15.33f)
        curveTo(14.832f, 15.33f, 13.856f, 14.23f, 13.856f, 12.89f)
        curveTo(13.856f, 11.55f, 14.811f, 10.46f, 16.02f, 10.46f)
        curveTo(17.239f, 10.46f, 18.204f, 11.56f, 18.183f, 12.89f)
        curveTo(18.183f, 14.23f, 17.229f, 15.33f, 16.02f, 15.33f)
        close()
    }.build()

    val LinkedInIcon: ImageVector = ImageVector.Builder(
        name = "LinkedIn", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).path(fill = SolidColor(Color(0xFF0A66C2))) {
        moveTo(19.0f, 3.0f)
        lineTo(5.0f, 3.0f)
        curveTo(3.9f, 3.0f, 3.0f, 3.9f, 3.0f, 5.0f)
        lineTo(3.0f, 19.0f)
        curveTo(3.0f, 20.1f, 3.9f, 21.0f, 5.0f, 21.0f)
        lineTo(19.0f, 21.0f)
        curveTo(20.1f, 21.0f, 21.0f, 20.1f, 21.0f, 19.0f)
        lineTo(21.0f, 5.0f)
        curveTo(21.0f, 3.9f, 20.1f, 3.0f, 19.0f, 3.0f)
        close()
        moveTo(8.5f, 18.0f)
        lineTo(6.0f, 18.0f)
        lineTo(6.0f, 10.0f)
        lineTo(8.5f, 10.0f)
        lineTo(8.5f, 18.0f)
        close()
        moveTo(7.25f, 8.8f)
        curveTo(6.45f, 8.8f, 5.8f, 8.15f, 5.8f, 7.35f)
        curveTo(5.8f, 6.55f, 6.45f, 5.9f, 7.25f, 5.9f)
        curveTo(8.05f, 5.9f, 8.7f, 6.55f, 8.7f, 7.35f)
        curveTo(8.7f, 8.15f, 8.05f, 8.8f, 7.25f, 8.8f)
        close()
        moveTo(18.0f, 18.0f)
        lineTo(15.5f, 18.0f)
        lineTo(15.5f, 13.8f)
        curveTo(15.5f, 12.8f, 14.7f, 12.0f, 13.7f, 12.0f)
        curveTo(12.7f, 12.0f, 12.0f, 12.8f, 12.0f, 13.8f)
        lineTo(12.0f, 18.0f)
        lineTo(9.5f, 18.0f)
        lineTo(9.5f, 10.0f)
        lineTo(12.0f, 10.0f)
        lineTo(12.0f, 11.2f)
        curveTo(12.6f, 10.4f, 13.6f, 9.8f, 14.8f, 9.8f)
        curveTo(16.6f, 9.8f, 18.0f, 11.3f, 18.0f, 13.2f)
        lineTo(18.0f, 18.0f)
        close()
    }.build()

    val GitHubIcon: ImageVector = ImageVector.Builder(
        name = "GitHub", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).path(fill = SolidColor(Color(0xFF24292E))) {
        moveTo(12.0f, 2.0f)
        curveTo(6.477f, 2.0f, 2.0f, 6.484f, 2.0f, 12.017f)
        curveTo(2.0f, 16.447f, 4.865f, 20.203f, 8.839f, 21.527f)
        curveTo(9.339f, 21.618f, 9.522f, 21.311f, 9.522f, 21.047f)
        curveTo(9.522f, 20.812f, 9.513f, 20.187f, 9.509f, 19.362f)
        curveTo(6.726f, 19.967f, 6.139f, 18.021f, 6.139f, 18.021f)
        curveTo(5.684f, 16.868f, 5.029f, 16.562f, 5.029f, 16.562f)
        curveTo(4.12f, 15.942f, 5.097f, 15.955f, 5.097f, 15.955f)
        curveTo(6.102f, 16.026f, 6.63f, 16.988f, 6.63f, 16.988f)
        curveTo(7.523f, 18.517f, 8.971f, 18.075f, 9.541f, 17.819f)
        curveTo(9.632f, 17.172f, 9.891f, 16.731f, 10.177f, 16.481f)
        curveTo(7.955f, 16.228f, 5.619f, 15.368f, 5.619f, 11.537f)
        curveTo(5.619f, 10.447f, 6.009f, 9.556f, 6.65f, 8.857f)
        curveTo(6.547f, 8.605f, 6.205f, 7.587f, 6.748f, 6.215f)
        curveTo(6.748f, 6.215f, 7.586f, 5.946f, 9.493f, 7.235f)
        curveTo(10.289f, 7.013f, 11.144f, 6.903f, 11.993f, 6.899f)
        curveTo(12.842f, 6.903f, 13.698f, 7.013f, 14.496f, 7.235f)
        curveTo(16.401f, 5.946f, 17.237f, 6.215f, 17.237f, 6.215f)
        curveTo(17.782f, 7.587f, 17.44f, 8.605f, 17.338f, 8.857f)
        curveTo(17.981f, 9.556f, 18.368f, 10.447f, 18.368f, 11.537f)
        curveTo(18.368f, 15.378f, 16.028f, 16.223f, 13.799f, 16.473f)
        curveTo(14.158f, 16.782f, 14.478f, 17.393f, 14.478f, 18.327f)
        curveTo(14.478f, 19.664f, 14.466f, 20.741f, 14.466f, 21.047f)
        curveTo(14.466f, 21.314f, 14.647f, 21.623f, 15.155f, 21.524f)
        curveTo(19.127f, 20.198f, 21.989f, 16.444f, 21.989f, 12.017f)
        curveTo(21.989f, 6.484f, 17.513f, 2.0f, 12.0f, 2.0f)
        close()
    }.build()

    fun getPlatformIcon(platform: String): ImageVector {
        return when (platform.lowercase().trim()) {
            "whatsapp" -> WhatsAppIcon
            "telegram" -> TelegramIcon
            "gmail", "email" -> GmailIcon
            "facebook" -> FacebookIcon
            "instagram" -> InstagramIcon
            "x", "twitter" -> XIcon
            "youtube" -> YouTubeIcon
            "discord" -> DiscordIcon
            "linkedin" -> LinkedInIcon
            "github" -> GitHubIcon
            "website", "web" -> Icons.Default.Language
            "phone", "call" -> Icons.Default.Phone
            "sms", "text" -> Icons.Default.Sms
            "skype" -> Icons.Default.Call
            else -> Icons.Default.ContactSupport
        }
    }

    fun getPlatformColor(platform: String): Color {
        return when (platform.lowercase().trim()) {
            "whatsapp" -> Color(0xFF25D366)
            "telegram" -> Color(0xFF229ED9)
            "gmail", "email" -> Color(0xFFEA4335)
            "website", "web" -> Color(0xFF2563EB)
            "facebook" -> Color(0xFF1877F2)
            "instagram" -> Color(0xFFE4405F)
            "x", "twitter" -> Color(0xFF0F172A)
            "youtube" -> Color(0xFFFF0000)
            "discord" -> Color(0xFF5865F2)
            "linkedin" -> Color(0xFF0A66C2)
            "reddit" -> Color(0xFFFF4500)
            "messenger" -> Color(0xFF0084FF)
            "signal" -> Color(0xFF3A76F0)
            "snapchat" -> Color(0xFFEAB308)
            "tiktok" -> Color(0xFF000000)
            "pinterest" -> Color(0xFFE60023)
            "github" -> Color(0xFF24292E)
            "skype" -> Color(0xFF00AFF0)
            "phone", "call" -> Color(0xFF10B981)
            "sms", "text" -> Color(0xFF8B5CF6)
            else -> Color(0xFF6366F1)
        }
    }

    fun formatContactUrl(contact: ContactMethod): String {
        val rawValue = contact.value.trim()
        if (rawValue.isBlank()) return ""

        if (rawValue.startsWith("http://") ||
            rawValue.startsWith("https://") ||
            rawValue.startsWith("mailto:") ||
            rawValue.startsWith("tel:") ||
            rawValue.startsWith("sms:") ||
            rawValue.startsWith("skype:")
        ) {
            return rawValue
        }

        val platform = contact.platform.lowercase().trim()
        val sanitizedValue = rawValue.replace(" ", "")

        return when (platform) {
            "whatsapp" -> {
                val cleanPhone = sanitizedValue.replace("+", "").replace("-", "")
                if (cleanPhone.all { it.isDigit() }) {
                    "https://wa.me/$cleanPhone"
                } else {
                    "https://wa.me/$sanitizedValue"
                }
            }
            "telegram" -> {
                val cleanUser = sanitizedValue.replace("@", "").replace("https://t.me/", "")
                "https://t.me/$cleanUser"
            }
            "gmail", "email" -> {
                "mailto:$sanitizedValue"
            }
            "phone", "call" -> {
                "tel:$sanitizedValue"
            }
            "sms", "text" -> {
                "sms:$sanitizedValue"
            }
            "instagram" -> {
                val cleanUser = sanitizedValue.replace("@", "").replace("https://instagram.com/", "")
                "https://instagram.com/$cleanUser"
            }
            "x", "twitter" -> {
                val cleanUser = sanitizedValue.replace("@", "").replace("https://x.com/", "")
                "https://x.com/$cleanUser"
            }
            "youtube" -> {
                if (sanitizedValue.startsWith("@")) {
                    "https://youtube.com/$sanitizedValue"
                } else {
                    "https://youtube.com/@$sanitizedValue"
                }
            }
            "facebook" -> {
                "https://facebook.com/$sanitizedValue"
            }
            "linkedin" -> {
                "https://linkedin.com/in/$sanitizedValue"
            }
            "reddit" -> {
                "https://reddit.com/r/$sanitizedValue"
            }
            "github" -> {
                "https://github.com/$sanitizedValue"
            }
            "skype" -> {
                "skype:$sanitizedValue?chat"
            }
            "discord" -> {
                if (sanitizedValue.startsWith("http")) sanitizedValue else "https://discord.gg/$sanitizedValue"
            }
            else -> {
                "https://$sanitizedValue"
            }
        }
    }

    fun launchContactIntent(context: Context, contact: ContactMethod) {
        val targetUrl = formatContactUrl(contact)
        if (targetUrl.isBlank()) {
            Toast.makeText(context, "No URL or contact value specified", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val webUrl = if (targetUrl.startsWith("mailto:")) {
                    targetUrl
                } else if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                    "https://${contact.value.trim()}"
                } else {
                    targetUrl
                }
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            } catch (ex: Exception) {
                Toast.makeText(context, "Could not open contact link: ${ex.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
