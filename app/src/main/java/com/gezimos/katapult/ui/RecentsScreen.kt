package com.gezimos.katapult.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gezimos.katapult.MainViewModel
import com.gezimos.katapult.R
import com.gezimos.katapult.Screen
import com.gezimos.katapult.model.AppModel
import com.gezimos.katapult.util.UsageHelper

private val RecentRowHeight = 72.dp
private val RecentIconSize = 48.dp

// Sits left of home and shows one page only: older apps are quicker to find in All Apps.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecentsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val hasPermission = remember { UsageHelper.hasPermission(context) }

    // False positive: maxHeight is read below.
    @Suppress("UnusedBoxWithConstraintsScope")
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalSurface.current)
            .then(
                if (!viewModel.prefs.hideStatusBar) {
                    Modifier.windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                } else {
                    Modifier
                },
            )
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
            .padding(PagePadding),
    ) {
        val apps = viewModel.recentApps
        val fits = (maxHeight / RecentRowHeight).toInt().coerceAtLeast(1)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .swipeLeftToHome { viewModel.navigateTo(Screen.HOME) },
        ) {
            if (!hasPermission || apps.isEmpty()) {
                RecentsEmpty(hasPermission)
            } else {
                apps.take(fits).forEach { app ->
                    RecentRow(viewModel, app)
                }
            }
        }
    }
}

private fun Modifier.swipeLeftToHome(onHome: () -> Unit) =
    pointerInput(Unit) {
        var accumulated = 0f
        detectHorizontalDragGestures(
            onDragStart = { accumulated = 0f },
            onDragEnd = { if (accumulated < -17.5.dp.toPx()) onHome() },
            onHorizontalDrag = { _, dragAmount -> accumulated += dragAmount },
        )
    }

@Composable
private fun RecentsEmpty(hasPermission: Boolean) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(if (hasPermission) R.string.recent_apps_empty else R.string.recent_apps_need_usage),
            fontSize = 18.sp,
            fontFamily = LatoFamily,
            color = LocalInk.current,
        )
        if (!hasPermission) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .height(ArrowSize)
                    .border(2.5.dp, LocalInk.current, LocalSmallIconShape.current)
                    .clickable { UsageHelper.openSettings(context) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.perm_usage_access),
                    fontSize = 16.sp,
                    fontFamily = LatoFamily,
                    color = LocalInk.current,
                )
            }
        }
    }
}

@Composable
private fun RecentRow(
    viewModel: MainViewModel,
    app: AppModel,
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { RecentIconSize.roundToPx() }
    val bitmap = rememberAppIcon(app.packageName, app.activityName, "", sizePx, 0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RecentRowHeight)
            .clickable { viewModel.launchApp(context, app) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconCircle(bitmap = bitmap, size = RecentIconSize)
        Spacer(Modifier.width(16.dp))
        Text(
            text = app.label,
            modifier = Modifier.weight(1f),
            fontSize = 18.sp,
            fontFamily = LatoFamily,
            color = LocalInk.current,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
