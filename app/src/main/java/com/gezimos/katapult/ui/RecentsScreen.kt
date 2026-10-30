package com.gezimos.katapult.ui

import android.text.format.DateUtils
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlin.math.abs

private val RecentRowHeight = 72.dp
private val RecentIconSize = 48.dp

// Sits left of home; swipe right or the right arrow pages to older apps.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecentsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val hasPermission = remember { UsageHelper.hasPermission(context) }
    var page by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(1) }

    Column(
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
            .padding(PagePadding)
            .horizontalSwipe { toRight ->
                if (toRight) {
                    if (page < totalPages - 1) page++
                } else if (page > 0) {
                    page--
                } else {
                    viewModel.navigateTo(Screen.HOME)
                }
            },
    ) {
        // False positive: maxHeight is read below.
        @Suppress("UnusedBoxWithConstraintsScope")
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val apps = viewModel.recentApps
            val perPage = (maxHeight / RecentRowHeight).toInt().coerceAtLeast(1)
            totalPages = ((apps.size + perPage - 1) / perPage).coerceAtLeast(1)
            if (page >= totalPages) page = totalPages - 1
            if (!hasPermission || apps.isEmpty()) {
                RecentsEmpty(hasPermission)
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    apps.drop(page * perPage).take(perPage).forEach { (app, lastUsed) ->
                        RecentRow(viewModel, app, lastUsed)
                    }
                }
            }
        }

        if (!viewModel.prefs.hideArrowButtons && totalPages > 1) {
            RecentsPageRow(page, totalPages) { page = it }
        }
    }
}

private fun Modifier.horizontalSwipe(onSwipe: (toRight: Boolean) -> Unit) =
    pointerInput(Unit) {
        var accumulated = 0f
        detectHorizontalDragGestures(
            onDragStart = { accumulated = 0f },
            onDragEnd = { if (abs(accumulated) > 17.5.dp.toPx()) onSwipe(accumulated > 0) },
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
private fun RecentsPageRow(
    page: Int,
    totalPages: Int,
    onPage: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Arrows line up with the icon column and the right edge of RecentRow.
        Box(Modifier.width(RecentIconSize), contentAlignment = Alignment.Center) {
            if (page > 0) ArrowButton(R.drawable.ic_arrow_left) { onPage(page - 1) }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
            for (i in 0 until totalPages) {
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(8.dp)
                        .then(
                            if (i == page) {
                                Modifier.background(LocalInk.current, CircleShape)
                            } else {
                                Modifier.border(1.5.dp, LocalInk.current, CircleShape)
                            },
                        ),
                )
            }
        }
        Box(Modifier.width(RecentIconSize), contentAlignment = Alignment.CenterEnd) {
            if (page < totalPages - 1) ArrowButton(R.drawable.ic_arrow_right) { onPage(page + 1) }
        }
    }
}

@Composable
private fun RecentRow(
    viewModel: MainViewModel,
    app: AppModel,
    lastUsed: Long,
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val sizePx = with(LocalDensity.current) { RecentIconSize.roundToPx() }
    val bitmap = rememberAppIcon(app.packageName, app.activityName, "", sizePx, 0)
    val ago = if (now - lastUsed < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.recent_apps_just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(
            lastUsed,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
    }
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
        Column(Modifier.weight(1f)) {
            Text(
                text = app.label,
                fontSize = 18.sp,
                fontFamily = LatoFamily,
                color = LocalInk.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = ago,
                fontSize = 14.sp,
                fontFamily = LatoFamily,
                color = LocalInk.current,
                maxLines = 1,
            )
        }
    }
}
