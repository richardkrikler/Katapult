package com.gezimos.katapult.lockscreen

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gezimos.katapult.R
import com.gezimos.katapult.service.NotificationListener
import com.gezimos.katapult.ui.AppIconCircle
import com.gezimos.katapult.ui.EinkColorFilter
import com.gezimos.katapult.ui.EinkColorFilterDark
import com.gezimos.katapult.ui.LatoFamily
import com.gezimos.katapult.ui.LocalIconFilter
import com.gezimos.katapult.ui.LocalInk
import com.gezimos.katapult.ui.LocalSurface
import com.gezimos.katapult.ui.NoIndication
import com.gezimos.katapult.ui.PagePadding
import com.gezimos.katapult.ui.rememberAppIcon
import com.gezimos.katapult.util.PrefsManager

/**
 * The list LockscreenWidgetService opens when it catches a swipe down from the status bar,
 * in place of MuditaOS's quick settings. Tap a row to open it (via NotificationOpenActivity),
 * long-press to clear it.
 */
class NotificationListActivity : ComponentActivity() {
    private val items = mutableStateOf<List<StatusBarNotification>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = PrefsManager(this).darkMode
        setContent {
            CompositionLocalProvider(
                LocalIndication provides NoIndication,
                LocalInk provides if (dark) Color.White else Color.Black,
                LocalSurface provides if (dark) Color.Black else Color.White,
                LocalIconFilter provides if (dark) EinkColorFilterDark else EinkColorFilter,
            ) {
                NotificationList(
                    items = items.value,
                    onOpen = { sbn ->
                        startActivity(NotificationOpenActivity.intent(this, sbn.packageName, sbn.key))
                        finish()
                    },
                    onClear = { NotificationListener.cancel(it.key) },
                    onClearAll = {
                        items.value.forEach { NotificationListener.cancel(it.key) }
                        finish()
                    },
                    onClose = ::finish,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        refresh()
        NotificationListener.onCountsChangedList = { runOnUiThread { refresh() } }
    }

    override fun onStop() {
        super.onStop()
        NotificationListener.onCountsChangedList = null
        // A shade, not a place to come back to.
        finish()
    }

    private fun refresh() {
        items.value = NotificationListener.listAll()
    }
}

private const val CLOSE_SWIPE_DP = 80

@Composable
private fun NotificationList(
    items: List<StatusBarNotification>,
    onOpen: (StatusBarNotification) -> Unit,
    onClear: (StatusBarNotification) -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit,
) {
    val ink = LocalInk.current
    val listState = rememberLazyListState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalSurface.current)
            // Swipe up closes, like pushing a shade back, once there's nothing left to scroll.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val atEnd = !listState.canScrollForward
                    var dy = 0f
                    do {
                        val event = awaitPointerEvent()
                        event.changes.firstOrNull()?.let { dy += it.positionChangeIgnoreConsumed().y }
                    } while (event.changes.any { it.pressed })
                    if (atEnd && dy < -CLOSE_SWIPE_DP.dp.toPx()) onClose()
                }
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(PagePadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.notification_list_title),
                color = ink,
                fontFamily = LatoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
            if (items.isNotEmpty()) {
                Text(
                    stringResource(R.string.notification_list_clear_all),
                    color = ink,
                    fontFamily = LatoFamily,
                    fontSize = 16.sp,
                    modifier = Modifier.clickable(onClick = onClearAll).padding(8.dp),
                )
            }
        }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.notification_list_empty), color = ink, fontFamily = LatoFamily, fontSize = 18.sp)
            }
        } else {
            // ponytail: scrolls rather than pages; page it like All Apps if scrolling smears the e-ink panel
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(items, key = { it.key }) { sbn ->
                    NotificationRow(sbn, onOpen, onClear)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(ink))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationRow(
    sbn: StatusBarNotification,
    onOpen: (StatusBarNotification) -> Unit,
    onClear: (StatusBarNotification) -> Unit,
) {
    val context = LocalContext.current
    val ink = LocalInk.current
    val extras = sbn.notification.extras
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
    val app = try {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
    } catch (_: Exception) {
        sbn.packageName
    }
    val time = DateUtils.getRelativeTimeSpanString(sbn.notification.`when`, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
    val iconSize = 36.dp
    val icon = rememberAppIcon(sbn.packageName, "", "", with(LocalDensity.current) { iconSize.roundToPx() }, 0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onOpen(sbn) }, onLongClick = { onClear(sbn) })
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AppIconCircle(icon, iconSize, borderWidth = 1.5.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("$app · $time", color = ink, fontFamily = LatoFamily, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (title.isNotEmpty()) {
                Text(
                    title,
                    color = ink,
                    fontFamily = LatoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (text.isNotEmpty()) {
                Text(text, color = ink, fontFamily = LatoFamily, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
