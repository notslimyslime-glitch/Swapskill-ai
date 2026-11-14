package com.skillswapai.nativeapp

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class RoomMessage(val id: String, val requestId: String, val senderId: String, val body: String, val createdAt: String)
data class LeaderboardEntry(val userId: String, val name: String, val points: Int)
data class AppNotification(val id: String, val type: String, val title: String, val body: String, val isRead: Boolean, val createdAt: String, val requestId: String?)

@Composable
fun TutorialScreen(onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("WELCOME TO", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("SKILLSWAP AI", color = MaterialTheme.colorScheme.primary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text("Created by Mayank & Harsh", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(22.dp))
        TutorialStep("01", "Discover", "Find people who can teach the skill you want to learn.")
        TutorialStep("02", "Swap fairly", "Send a request, agree on what to learn, and help each other grow.")
        TutorialStep("03", "Mission Rooms", "Chat privately inside accepted swaps. Keep personal contact details private.")
        TutorialStep("04", "Earn XP", "Complete swaps together to earn points on the leaderboard.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Let's get started") }
    }
}

@Composable
private fun TutorialStep(number: String, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Text(number, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(42.dp))
        Column {
            Text(title, color = Cream, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(detail, color = Muted, fontSize = 13.sp)
        }
    }
}

private fun skillOverlap(wanted: String, offered: String): Float {
    val a = wanted.trim().lowercase()
    val b = offered.trim().lowercase()
    if (a.isBlank() || b.isBlank()) return 0f
    if (a == b) return 1f
    if (a.contains(b) || b.contains(a)) return 0.86f
    val aw = Regex("[a-z0-9]+").findAll(a).map { it.value }.toSet()
    val bw = Regex("[a-z0-9]+").findAll(b).map { it.value }.toSet()
    if (aw.isEmpty() || bw.isEmpty()) return 0f
    val common = aw.intersect(bw).size.toFloat()
    return (common / maxOf(aw.size, bw.size)) * 0.72f
}

@Composable
fun MatchmakingScreen(
    modifier: Modifier, members: List<Member>, userTeach: String, userLearn: String, busy: Boolean,
    requestedMemberIds: Set<String>, onRequest: (String) -> Unit, onEditSkills: () -> Unit
) {
    val matches = remember(members, userTeach, userLearn) {
        members.map { member ->
            val learnFit = skillOverlap(userLearn, member.teaches)
            val teachFit = skillOverlap(userTeach, member.learns)
            val score = (learnFit * 65f + teachFit * 35f).toInt().coerceIn(0, 100)
            Triple(member, score, learnFit > 0f && teachFit > 0f)
        }.filter { it.second >= 18 }.sortedByDescending { it.second }
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary)))
                .padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, "Matchmaking", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                Text("YOUR SKILL MATCHMAKER", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("People who fit your next move.", color = Cream, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("We compare what you want to learn with what others teach, then check whether they want to learn what you teach.", color = Muted, fontSize = 12.sp)
                TextButton(onClick = onEditSkills) { Text("Tune my skill card  →", color = Cream) }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Recommended matches", color = Cream, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("${matches.size} possible matches found", color = Muted, fontSize = 11.sp)
                }
                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
        if (userTeach.isBlank() || userLearn.isBlank()) {
            item {
                Text("Finish your skill card first so we can find people who fit.", color = Muted,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp))
            }
        } else if (matches.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No close matches yet", color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Try making your skill names more specific, or check again when more learners join.", color = Muted, fontSize = 12.sp)
                    OutlinedButton(onClick = onEditSkills) { Text("Edit skill card") }
                }
            }
        }
        items(matches, key = { it.first.id }) { (member, score, mutual) ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(member.name, color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(if (mutual) "Two-way skill fit" else "Can teach what you want to learn",
                            color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$score%", color = MaterialTheme.colorScheme.primary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text("match", color = Muted, fontSize = 9.sp)
                    }
                }
                LinearProgressIndicator(progress = { score / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.background)
                Text("Can teach: ${member.teaches}", color = Cream, fontSize = 13.sp)
                Text("Wants to learn: ${member.learns}", color = Muted, fontSize = 12.sp)
                if (member.about.isNotBlank()) Text(member.about, color = Muted, fontSize = 12.sp)
                val requested = member.id in requestedMemberIds
                Button(onClick = { onRequest(member.id) }, enabled = !busy && !requested, modifier = Modifier.fillMaxWidth()) {
                    Text(if (requested) "Request sent / swap active" else "Request this match")
                }
            }
        }
    }
}

@Composable
fun RoomsScreen(modifier: Modifier, swaps: List<Swap>, userId: String, onOpen: (Swap) -> Unit) {
    val active = swaps.filter { it.status == "accepted" }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("MISSION ROOMS", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("Your swap chats", color = Cream, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("Private rooms appear after a swap is accepted.", color = Muted, fontSize = 13.sp)
        }
        if (active.isEmpty()) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(20.dp)) {
                Text("No rooms yet", color = Cream, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Accept a swap request to unlock your first Mission Room.", color = Muted, fontSize = 13.sp)
            }
        }
        items(active, key = { it.id }) { swap ->
            val mine = swap.senderId == userId
            val partner = if (mine) swap.recipientName else swap.senderName
            Column(Modifier.fillMaxWidth().animateContentSize().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                Text(partner, color = Cream, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Skill: ${swap.skill}", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                Text("Your private swap room", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { onOpen(swap) }, modifier = Modifier.fillMaxWidth()) { Text("Open room") }
            }
        }
    }
}

@Composable
fun MissionRoomScreen(
    modifier: Modifier, swap: Swap, userId: String, messages: List<RoomMessage>,
    draft: String, setDraft: (String) -> Unit, busy: Boolean, sentMessageScrollKey: Int,
    onSend: () -> Unit, onRefresh: () -> Unit,
    planGoal: String, setPlanGoal: (String) -> Unit, planTasks: String, setPlanTasks: (String) -> Unit,
    onSavePlan: () -> Unit, accessToken: String, onBack: () -> Unit
) {
    DisposableEffect(swap.id, accessToken) {
        val realtime = SupabaseRoomRealtime(accessToken, swap.id) { onRefresh() }
        realtime.connect()
        onDispose { realtime.close() }
    }
    val chatListState = rememberLazyListState()
    LaunchedEffect(sentMessageScrollKey) {
        if (sentMessageScrollKey > 0 && messages.isNotEmpty()) chatListState.animateScrollToItem(messages.lastIndex)
    }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Cream) }
            Column(Modifier.weight(1f)) {
                val partner = if (swap.senderId == userId) swap.recipientName else swap.senderName
                Text(partner, color = Cream, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Mission Room · ${swap.skill}", color = Muted, fontSize = 11.sp)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh messages", tint = Cream) }
        }
        Text("Keep phone numbers, addresses and social handles private. Meet only in a public place with a trusted adult aware.",
            color = Color(0xFFB9D6B2), fontSize = 11.sp, modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondary).padding(12.dp))
        var planExpanded by remember(swap.id) { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 8.dp)) {
            TextButton(onClick = { planExpanded = !planExpanded }) { Text(if (planExpanded) "Hide session plan" else "Plan this swap", color = MaterialTheme.colorScheme.primary) }
            if (planExpanded) {
                OutlinedTextField(value = planGoal, onValueChange = setPlanGoal, label = { Text("Shared learning goal") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = planTasks, onValueChange = setPlanTasks, label = { Text("Tasks, one per line") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                Text("Both swap partners can update this shared plan.", color = Muted, fontSize = 11.sp)
                Button(onClick = onSavePlan, enabled = !busy && planGoal.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save shared plan") }
            }
        }
        LazyColumn(state = chatListState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (messages.isEmpty()) item { Text("This is the start of your mission. Say hello and plan your first session!", color = Muted, fontSize = 13.sp) }
            items(messages, key = { it.id }) { msg ->
                val mine = msg.senderId == userId
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Column(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp)).background(if (mine) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surface).padding(12.dp)) {
                        Text(if (mine) "You" else if (swap.senderId == userId) swap.recipientName else swap.senderName,
                            color = if (mine) Color(0xFFB9D6B2) else Cream, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(msg.body, color = Cream, fontSize = 14.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = draft, onValueChange = { if (it.length <= 1000) setDraft(it) },
                modifier = Modifier.weight(1f), placeholder = { Text("Write a message…") }, maxLines = 4)
            IconButton(onClick = onSend, enabled = !busy && draft.isNotBlank()) {
                Icon(Icons.Default.Send, "Send message", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun LeaderboardScreen(modifier: Modifier, entries: List<LeaderboardEntry>, busy: Boolean, message: String, onRefresh: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("COMMUNITY RANKS", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Skill champions", color = Cream, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
            Text("Earn XP by completing swaps together.", color = Muted, fontSize = 13.sp)
            if (message.isNotBlank()) Text(message, color = Muted, fontSize = 11.sp)
        }
        if (busy && entries.isEmpty()) item { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        if (!busy && entries.isEmpty()) item { Text("No XP yet. Complete a swap to claim the first spot!", color = Muted) }
        items(entries, key = { it.userId }) { entry ->
            val rank = entries.indexOf(entry) + 1
            val highlight = rank <= 3
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (highlight) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#$rank" },
                    color = Cream, fontWeight = FontWeight.Bold, fontSize = if (highlight) 21.sp else 15.sp, modifier = Modifier.width(48.dp))
                Text(entry.name, color = Cream, fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
                    fontSize = if (highlight) 17.sp else 15.sp, modifier = Modifier.weight(1f))
                Text("${entry.points} XP", color = Color(0xFFB9D6B2), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun NotificationsScreen(modifier: Modifier, notifications: List<AppNotification>, busy: Boolean,
    onRead: (String) -> Unit, onBack: () -> Unit, onRefresh: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR INBOX", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Notifications", color = Cream, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onBack) { Text("Back") }
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
        }
        if (notifications.isEmpty()) item { Text(if (busy) "Loading notifications…" else "You're all caught up.", color = Muted) }
        items(notifications, key = { it.id }) { note ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (note.isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondary).padding(15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(note.title, color = Cream, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (!note.isRead) Text("NEW", color = Color(0xFFB9D6B2), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(note.body, color = Muted, fontSize = 13.sp)
                if (!note.isRead) TextButton(onClick = { onRead(note.id) }) { Text("Mark as read") }
            }
        }
    }
}


@Composable
fun SettingsScreen(
    modifier: Modifier,
    accentChoice: String,
    onAccentChange: (String) -> Unit,
    animationsEnabled: Boolean,
    onAnimationsChange: (Boolean) -> Unit,
    hapticsEnabled: Boolean,
    onHapticsChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack) { Text("‹ Back to account", color = MaterialTheme.colorScheme.primary) }
        Text("MAKE IT YOURS", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("App settings", color = Cream, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text("A few little switches to make SkillSwap feel like your space.", color = Muted, fontSize = 13.sp)

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ACCENT COLOUR", color = Cream, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("Choose the highlight colour used across buttons and active controls.", color = Muted, fontSize = 12.sp)
            listOf(
                "Sage" to MaterialTheme.colorScheme.primary,
                "Ocean" to Color(0xFF8BC7D8),
                "Violet" to Color(0xFFC2A5F5),
                "Amber" to Color(0xFFE5BD78)
            ).forEach { (name, colour) ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (accentChoice == name) MaterialTheme.colorScheme.secondary else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = accentChoice == name, onClick = {
                        onAccentChange(name)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    })
                    Box(Modifier.size(13.dp).clip(RoundedCornerShape(50)).background(colour))
                    Spacer(Modifier.width(10.dp))
                    Text(name, color = Cream, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (accentChoice == name) Text("SELECTED", color = MaterialTheme.colorScheme.primary,
                        fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MOTION & FEEDBACK", color = Cream, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            SettingsSwitchRow("Smooth transitions", "Animate movement between app sections.", animationsEnabled) {
                onAnimationsChange(it)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.tertiary)
            SettingsSwitchRow("Haptic feedback", "Small vibration on navigation and setting changes.", hapticsEnabled) {
                onHapticsChange(it)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        Text("Your choices are saved on this device and stay after you close the app.", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SettingsSwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Cream, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(detail, color = Muted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}


private const val REALTIME_URL = "wss://qmjmfjgqfmrrdlgmjksh.supabase.co/realtime/v1/websocket"
private const val REALTIME_KEY = "sb_publishable_gOwrGejFZia29Yl8GlzI7A_RNenYsud"

class SupabaseRoomRealtime(
    private val accessToken: String,
    private val requestId: String,
    private val onInsert: () -> Unit
) {
    private val refs = AtomicInteger(1)
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val client = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).build()
    @Volatile private var socket: WebSocket? = null

    fun connect() {
        val request = Request.Builder().url("$REALTIME_URL?apikey=$REALTIME_KEY&vsn=1.0.0")
            .header("apikey", REALTIME_KEY).header("Authorization", "Bearer $accessToken").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val ref = refs.getAndIncrement().toString()
                val changes = JSONArray().put(JSONObject().put("event", "INSERT").put("schema", "public")
                    .put("table", "swap_messages").put("filter", "request_id=eq.$requestId"))
                val config = JSONObject().put("broadcast", JSONObject().put("ack", false).put("self", false))
                    .put("presence", JSONObject().put("key", "")).put("postgres_changes", changes)
                val payload = JSONObject().put("config", config).put("access_token", accessToken)
                webSocket.send(JSONObject().put("topic", "realtime:public:swap_messages").put("event", "phx_join")
                    .put("payload", payload).put("ref", ref).put("join_ref", ref).toString())
                scheduler.scheduleAtFixedRate({
                    val heartbeatRef = refs.getAndIncrement().toString()
                    socket?.send(JSONObject().put("topic", "phoenix").put("event", "heartbeat")
                        .put("payload", JSONObject()).put("ref", heartbeatRef).toString())
                }, 25, 25, TimeUnit.SECONDS)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try { if (JSONObject(text).optString("event") == "postgres_changes") onInsert() } catch (_: Exception) { }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { close() }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { close() }
        })
    }
    fun close() {
        scheduler.shutdownNow()
        socket?.close(1000, "Room closed")
        socket = null
    }
}
