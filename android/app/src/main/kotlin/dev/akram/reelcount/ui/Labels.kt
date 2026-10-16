package dev.akram.reelcount.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.akram.reelcount.R

@Composable
fun appLabel(packageName: String): String =
    when (packageName) {
        "com.facebook.katana" -> stringResource(R.string.app_facebook)
        "com.facebook.lite" -> stringResource(R.string.app_facebook_lite)
        "com.instagram.android" -> stringResource(R.string.app_instagram)
        "com.google.android.youtube" -> stringResource(R.string.app_youtube)
        else -> packageName
    }
