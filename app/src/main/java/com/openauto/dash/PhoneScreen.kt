@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.openauto.dash

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.openauto.dash.link.RecentCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale

/*
 * Dashwheel's own phone screen, over the dashboard: the contacts this unit
 * holds (the phonebook its Bluetooth synced from the phone) with the
 * favourites first, the linked phone's last calls, and a keypad. The phone
 * key of every look and the Quick dial's Phone button open it (issue #118:
 * the unit's own phone app takes the whole screen, and on some units its
 * keypad does nothing). A tap on a name waits three seconds in the strip,
 * as on the Quick dial; the call then goes the way [dialNumber] finds (the
 * linked phone, the unit's Bluetooth, its dialer). The unit's own phone app
 * stays one tap away, for what it alone does (its own call log, its settings).
 */

/** Whether the Phone screen is up, and whether a dashboard is there to show it. */
internal object PhoneScreen {
    private val _shown = MutableStateFlow(false)
    val shown: StateFlow<Boolean> = _shown.asStateFlow()
    /** Dashboards on screen that would show the dialog (main thread). */
    private var hosts = 0

    /** A dashboard that shows the dialog came on screen ([on]) or left. */
    fun hosted(on: Boolean) {
        hosts = (hosts + if (on) 1 else -1).coerceAtLeast(0)
    }

    /**
     * Opens the screen. Where no dashboard can show it (a bare preview, the
     * second screen), the unit's own phone app as before, else [orElse].
     */
    fun open(context: Context, orElse: (() -> Unit)? = null) {
        if (hosts > 0) _shown.value = true else UnitPhoneApp.open(context, orElse)
    }

    fun close() {
        _shown.value = false
    }
}

/** A letter of the contacts list and the names under it. */
internal data class ContactSection(val letter: String, val contacts: List<UnitContact>)

/** The letter [name] is listed under: its first letter without its accent, in upper case; "#" for a number or a sign. */
internal fun indexLetter(name: String): String {
    val first = name.trimStart().firstOrNull() ?: return "#"
    val plain = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).firstOrNull { it.isLetter() } ?: return "#"
    return plain.uppercase(Locale.getDefault())
}

/** [contacts] (already by name) cut into their letters, "#" last. */
internal fun contactSections(contacts: List<UnitContact>): List<ContactSection> {
    val byLetter = LinkedHashMap<String, MutableList<UnitContact>>()
    for (c in contacts) byLetter.getOrPut(indexLetter(c.name)) { mutableListOf() } += c
    val (signs, letters) = byLetter.entries.partition { it.key == "#" }
    return (letters + signs).map { ContactSection(it.key, it.value) }
}

/** The row of [sections][index]'s header in a list that starts with [before] rows (the favourites). */
internal fun sectionRow(sections: List<ContactSection>, index: Int, before: Int): Int =
    before + sections.take(index).sumOf { 1 + it.contacts.size }

/** The longest number the keypad takes: the longest international numbers, with room for a pause. */
internal const val KEYPAD_MAX = 24

/** [number] with [key] typed after it, unless it is full. */
internal fun keypadType(number: String, key: String): String = if (number.length >= KEYPAD_MAX) number else number + key

/** The text of the Recents tab when there is nothing to list: the phone sent nothing, or an empty log. */
internal fun recentsEmptyText(calls: List<RecentCall>?): Int =
    if (calls == null) R.string.phone_recents_empty else R.string.phone_recents_none

private enum class PhoneTab(val icon: ImageVector, val label: Int) {
    CONTACTS(Icons.Filled.Contacts, R.string.phone_tab_contacts),
    RECENTS(Icons.Filled.History, R.string.phone_tab_recents),
    KEYPAD(Icons.Filled.Dialpad, R.string.phone_tab_keypad)
}

/** The screen itself, as a dialog over the dashboard (AutomotiveDashboard hosts it while [PhoneScreen.shown]). */
@Composable
internal fun PhoneDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(PhoneTab.CONTACTS) }
    val pending = rememberPendingAction()
    val unitApp = remember { UnitPhoneApp.exists(context) }
    fun call(name: String, number: String) = pending.arm(context.getString(R.string.phone_calling, name)) { dialNumber(context, number) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(
            shape = DashShape.Large,
            color = DashColors.Card.copy(alpha = 1f),
            // Docked app windows are drawn above dialogs on this unit; the ones under it step aside.
            modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(0.94f).fillMaxHeight(0.94f).keepClearOfWindows()
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // An upright screen: the tabs as icons, the unit's app as one too, so the header stays on one line.
                val narrow = maxWidth < 640.dp
                Column(modifier = Modifier.fillMaxSize().padding(DashSpace.Md)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (!narrow) {
                            Text(
                                stringResource(R.string.settings_section_phone).uppercase(Locale.getDefault()),
                                color = DashColors.AccentInk, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
                                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = DashSpace.Xs)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(DashSpace.Sm)) {
                            PhoneTab.entries.forEach { t -> TabKey(t, selected = tab == t, iconOnly = narrow) { tab = t } }
                        }
                        Spacer(Modifier.weight(1f))
                        if (unitApp && narrow) {
                            IconButton(onClick = { onDismiss(); UnitPhoneApp.open(context) }, modifier = Modifier.size(DashSize.Touch)) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.phone_unit_app), tint = DashColors.Muted)
                            }
                        } else if (unitApp) {
                            TextButton(onClick = { onDismiss(); UnitPhoneApp.open(context) }, modifier = Modifier.height(DashSize.Touch)) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = DashColors.Muted, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.phone_unit_app), color = DashColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(DashSize.Touch)) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.dash_close), tint = DashColors.TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(DashSpace.Sm))
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (tab) {
                            PhoneTab.CONTACTS -> ContactsPane(::call)
                            PhoneTab.RECENTS -> RecentsPane(::call)
                            PhoneTab.KEYPAD -> KeypadPane { number -> dialNumber(context, number) }
                        }
                    }
                }
                PendingActionStrip(pending, Modifier.align(Alignment.BottomCenter).padding(DashSpace.Md))
            }
        }
    }
}

@Composable
private fun TabKey(tab: PhoneTab, selected: Boolean, iconOnly: Boolean, onClick: () -> Unit) {
    val ink = if (selected) DashColors.OnAccent else DashColors.TextSecondary
    Row(
        modifier = Modifier
            .height(DashSize.Touch)
            .clip(DashShape.Medium)
            .background(if (selected) DashColors.Accent else DashColors.CardHi)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(tab.icon, contentDescription = if (iconOnly) stringResource(tab.label) else null, tint = ink, modifier = Modifier.size(20.dp))
        if (!iconOnly) {
            Spacer(Modifier.width(8.dp))
            Text(stringResource(tab.label), color = ink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

// --- Contacts ---------------------------------------------------------------------

/**
 * The favourites (the linked phone's, else the unit's starred contacts, as
 * the Quick dial shows them) and then every contact the unit holds, by
 * letter, with the letters down the side to jump by.
 */
@Composable
private fun ContactsPane(call: (name: String, number: String) -> Unit) {
    val context = LocalContext.current
    val source = rememberQuickDialSource()
    val perm = source.access
    LaunchedEffect(perm.granted) {
        if (perm.granted) UnitContacts.start(context)
    }
    DisposableEffect(Unit) {
        UnitContacts.wantAll(true)
        onDispose { UnitContacts.wantAll(false) }
    }
    val all by UnitContacts.contacts.collectAsState()
    val contacts = if (perm.granted) all.orEmpty() else emptyList()
    val favourites = source.favourites
    val sections = remember(contacts) { contactSections(contacts) }
    when {
        contacts.isEmpty() && favourites.isEmpty() && !perm.granted -> NeedsAccess(
            Icons.Filled.Contacts, stringResource(R.string.phone_contacts_needs_access),
            stringResource(if (perm.blocked) R.string.dash_open_settings else R.string.info_quickdial_allow), perm.request
        )
        contacts.isEmpty() && favourites.isEmpty() -> Centered(stringResource(R.string.phone_contacts_empty))
        else -> Row(modifier = Modifier.fillMaxSize()) {
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            // The favourites block: its header and its rows, before the first letter.
            val before = if (favourites.isEmpty()) 0 else 1 + favourites.size
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (favourites.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.phone_favourites)) }
                    items(favourites) { f ->
                        val bmp = f.photo
                        val photo: (@Composable () -> Unit)? = if (bmp == null) null else {
                            { Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        }
                        ContactLine(f.name, f.number, photo, starred = true, enabled = f.number != null) { f.number?.let { call(f.name, it) } }
                    }
                }
                sections.forEach { s ->
                    item { SectionHeader(s.letter) }
                    items(s.contacts) { c -> ContactLine(c.name, c.number, null, starred = c.starred) { call(c.name, c.number) } }
                }
            }
            if (sections.size > 1) {
                Spacer(Modifier.width(DashSpace.Sm))
                LetterIndex(sections.map { it.letter }) { i -> scope.launch { listState.animateScrollToItem(sectionRow(sections, i, before)) } }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(Locale.getDefault()), color = DashColors.AccentInk, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = DashSpace.Md, top = DashSpace.Md, bottom = DashSpace.Xs)
    )
}

/** One contact: a face or initials, the name, the number; a tap arms the call. */
@Composable
private fun ContactLine(
    name: String, number: String?, photo: (@Composable () -> Unit)?, starred: Boolean, enabled: Boolean = true, onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DashSize.MenuRow)
            .clip(DashShape.Medium)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = DashSpace.Md, vertical = DashSpace.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(DashColors.AccentBrush).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (photo != null) photo() else Text(
                initials(name), color = DashColors.OnAccent, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1
            )
        }
        Spacer(Modifier.width(DashSpace.Md))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = DashColors.TextPrimary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (number != null && number != name) {
                Text(number, color = DashColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (starred) Icon(Icons.Filled.Star, contentDescription = null, tint = DashColors.Warning, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(DashSpace.Sm))
        Icon(Icons.Filled.Call, contentDescription = null, tint = if (enabled) DashColors.Good else DashColors.Muted, modifier = Modifier.size(22.dp))
    }
}

/** The first letters of up to two words, as the Quick dial's discs show them. */
private fun initials(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")

/** The letters down the right edge: a tap on one scrolls the list to it. */
@Composable
private fun LetterIndex(letters: List<String>, onLetter: (Int) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxHeight().width(28.dp)) {
        // Sized to the height: 27 letters in a short dialog still get a row each.
        val row = (maxHeight / letters.size).coerceAtMost(DashSize.Touch)
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            letters.forEachIndexed { i, l ->
                Box(modifier = Modifier.height(row).fillMaxWidth().clickable { onLetter(i) }, contentAlignment = Alignment.Center) {
                    Text(l, color = DashColors.AccentInk, fontWeight = FontWeight.Bold, fontSize = (row.value * 0.55f).coerceIn(10f, 14f).sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Centered(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(DashSpace.Xl), contentAlignment = Alignment.Center) {
        Text(text, color = DashColors.Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    }
}

// --- Recents ----------------------------------------------------------------------

/** The linked phone's last calls, newest first (a head unit has no call log Dashwheel may read). */
@Composable
private fun RecentsPane(call: (name: String, number: String) -> Unit) {
    val context = LocalContext.current
    val calls = PhoneLink.lists.collectAsState().value.calls
    val timeFmt = rememberTimeFormat()
    if (calls.isNullOrEmpty()) {
        Centered(stringResource(recentsEmptyText(calls)))
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(calls) { c ->
            val (icon, tint, kind) = when (c.type) {
                RecentCall.Type.MISSED -> Triple(Icons.Filled.PhoneMissed, DashColors.Warning, R.string.phone_recent_missed)
                RecentCall.Type.INCOMING -> Triple(Icons.Filled.CallReceived, DashColors.Good, R.string.phone_recent_incoming)
                RecentCall.Type.OUTGOING -> Triple(Icons.Filled.CallMade, DashColors.TextSecondary, R.string.phone_recent_outgoing)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DashSize.MenuRow)
                    .clip(DashShape.Medium)
                    .clickable { call(c.name ?: c.number, c.number) }
                    .padding(horizontal = DashSpace.Md, vertical = DashSpace.Xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(DashSpace.Md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        c.name ?: c.number, color = DashColors.TextPrimary, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (c.type == RecentCall.Type.MISSED) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (c.name != null) "${stringResource(kind)} · ${c.number}" else stringResource(kind),
                        color = DashColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(DashSpace.Sm))
                Text(
                    if (DateUtils.isToday(c.at)) timeFmt.format(java.util.Date(c.at))
                    else DateUtils.formatDateTime(context, c.at, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR),
                    color = DashColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1
                )
                Spacer(Modifier.width(DashSpace.Sm))
                Icon(Icons.Filled.Call, contentDescription = null, tint = DashColors.Good, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// --- Keypad -----------------------------------------------------------------------

private val KEYPAD_ROWS = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("*", "0", "#"))

/** The number typed, big keys (a long press on 0 gives +, on the delete key clears), Call. */
@Composable
private fun KeypadPane(onCall: (String) -> Unit) {
    var number by remember { mutableStateOf("") }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Four rows of keys, the number above and Call below, in whatever height the dialog has.
        val key = ((maxHeight - 56.dp - DashSize.TouchPrimary - DashSpace.Sm * 6) / 4).coerceIn(DashSize.Touch, 72.dp)
        val gap = DashSpace.Sm
        Column(
            modifier = Modifier.fillMaxHeight().align(Alignment.Center).widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            Row(modifier = Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(DashSize.Touch))
                Text(
                    number, color = DashColors.TextPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(DashSize.Touch)
                        .clip(CircleShape)
                        .combinedClickable(enabled = number.isNotEmpty(), onClick = { number = number.dropLast(1) }, onLongClick = { number = "" }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.phone_keypad_delete),
                        tint = if (number.isEmpty()) DashColors.Muted.copy(alpha = 0.4f) else DashColors.TextSecondary, modifier = Modifier.size(26.dp)
                    )
                }
            }
            KEYPAD_ROWS.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { k ->
                        Box(
                            modifier = Modifier
                                .size(width = key * 1.4f, height = key)
                                .clip(DashShape.Medium)
                                .background(DashColors.CardHi)
                                .combinedClickable(
                                    onClick = { number = keypadType(number, k) },
                                    onLongClick = if (k == "0") ({ number = keypadType(number, "+") }) else null
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(k, color = DashColors.TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                if (k == "0") Text("+", color = DashColors.Muted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            Button(
                onClick = { onCall(number) },
                enabled = number.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = DashColors.Good, contentColor = DashColors.OnAccent),
                shape = DashShape.Medium,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                modifier = Modifier.height(DashSize.TouchPrimary).widthIn(min = 200.dp)
            ) {
                Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(DashSpace.Sm))
                Text(stringResource(R.string.phone_keypad_call), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
