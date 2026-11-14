package com.skillswapai.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL

private const val SUPABASE_URL = "https://qmjmfjgqfmrrdlgmjksh.supabase.co"
private const val SUPABASE_KEY = "sb_publishable_gOwrGejFZia29Yl8GlzI7A_RNenYsud"
val Forest = Color(0xFF173B2A)
val ForestLight = Color(0xFF244B36)
val Cream = Color(0xFFF0E8D5)
val Muted = Color(0xFFA9B8AA)
val SurfaceDark = Color(0xFF1C2921)
val BackgroundDark = Color(0xFF101812)

data class Member(val id: String, val name: String, val teaches: String, val learns: String, val about: String)
data class Swap(
    val id: String, val senderId: String, val recipientId: String, val skill: String,
    val status: String, val senderCompleted: Boolean, val recipientCompleted: Boolean,
    val senderName: String, val recipientName: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 24, 18)
        window.navigationBarColor = android.graphics.Color.rgb(16, 24, 18)
        setContent { SkillSwapApp(this) }
    }
}

private fun securePrefs(activity: ComponentActivity) =
    EncryptedSharedPreferences.create(
        activity,
        "skillswap_secure_session",
        MasterKey.Builder(activity).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

private fun request(path: String, method: String = "GET", token: String? = null, body: JSONObject? = null, prefer: String? = null): JSONObject {
    val connection = URL("$SUPABASE_URL$path").openConnection() as HttpURLConnection
    try {
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("apikey", SUPABASE_KEY)
        connection.setRequestProperty("Authorization", "Bearer ${token ?: SUPABASE_KEY}")
        connection.setRequestProperty("Content-Type", "application/json")
        if (prefer != null) connection.setRequestProperty("Prefer", prefer)
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        val parsed: Any = if (text.isBlank()) JSONObject() else JSONTokener(text).nextValue()
        val json = if (parsed is JSONObject) parsed else JSONObject().put("data", parsed)
        if (code !in 200..299) {
            val message = json.optString("msg").ifBlank { json.optString("message") }
                .ifBlank { json.optString("error_description") }
                .ifBlank { json.optString("error") }
                .ifBlank { "Request failed ($code)" }
            throw IllegalStateException(message)
        }
        return json
    } finally { connection.disconnect() }
}

private suspend fun authRequest(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
    request(path, "POST", body = body)
}

@Composable
private fun SkillSwapApp(activity: ComponentActivity) {
    val prefs = remember { securePrefs(activity) }
    val scope = rememberCoroutineScope()
    var accessToken by remember { mutableStateOf<String?>(null) }
    var userId by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("Discover") }
    var authMode by remember { mutableStateOf("Sign in") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var teaches by remember { mutableStateOf("") }
    var learns by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var members by remember { mutableStateOf<List<Member>>(emptyList()) }
    var swaps by remember { mutableStateOf<List<Swap>>(emptyList()) }
    var selectedRoom by remember { mutableStateOf<Swap?>(null) }
    var roomMessages by remember { mutableStateOf<List<RoomMessage>>(emptyList()) }
    var messageDraft by remember { mutableStateOf("") }
    var leaderboard by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var notifications by remember { mutableStateOf<List<AppNotification>>(emptyList()) }
    var showTutorial by remember { mutableStateOf(!prefs.getBoolean("tutorial_done", false)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Sign in to continue to SkillSwap.") }
    var needsProfile by remember { mutableStateOf(false) }

    fun loadMembers(token: String) {
        scope.launch {
            busy = true
            try {
                val result = withContext(Dispatchers.IO) {
                    val connection = URL("$SUPABASE_URL/rest/v1/profiles?select=id,display_name,teaches,learns,about&order=created_at.desc").openConnection() as HttpURLConnection
                    try {
                        connection.setRequestProperty("apikey", SUPABASE_KEY)
                        connection.setRequestProperty("Authorization", "Bearer $token")
                        connection.connectTimeout = 15000
                        connection.readTimeout = 15000
                        val code = connection.responseCode
                        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                        val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                        if (code !in 200..299) throw IllegalStateException(JSONObject(raw).optString("message", "Could not load members ($code)"))
                        val array = JSONArray(raw)
                        (0 until array.length()).map { i ->
                            val item = array.getJSONObject(i)
                            Member(item.optString("id"), item.optString("display_name"), item.optString("teaches"), item.optString("learns"), item.optString("about"))
                        }
                    } finally { connection.disconnect() }
                }
                members = result.filter { it.id != userId }
                message = "Connected · ${result.size} community profiles"
            } catch (e: Exception) { message = e.message ?: "Could not load members." }
            busy = false
        }
    }

    fun loadSwaps(token: String) {
        val currentUser = userId ?: return
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val query = "/rest/v1/swap_requests?select=id,sender_id,recipient_id,skill,status,sender_completed,recipient_completed,sender:profiles!swap_requests_sender_id_fkey(display_name),recipient:profiles!swap_requests_recipient_id_fkey(display_name)&or=(sender_id.eq.$currentUser,recipient_id.eq.$currentUser)&order=created_at.desc"
                    val data = request(query, token = token).optJSONArray("data") ?: JSONArray()
                    (0 until data.length()).map { i ->
                        val item = data.getJSONObject(i)
                        Swap(
                            id = item.optString("id"), senderId = item.optString("sender_id"),
                            recipientId = item.optString("recipient_id"), skill = item.optString("skill"),
                            status = item.optString("status"), senderCompleted = item.optBoolean("sender_completed"),
                            recipientCompleted = item.optBoolean("recipient_completed"),
                            senderName = item.optJSONObject("sender")?.optString("display_name").orEmpty().ifBlank { "Member" },
                            recipientName = item.optJSONObject("recipient")?.optString("display_name").orEmpty().ifBlank { "Member" }
                        )
                    }
                }
                swaps = result
            } catch (e: Exception) { message = e.message ?: "Could not load your swaps." }
        }
    }

    fun sendSwapRequest(recipientId: String) {
        val token = accessToken ?: return
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    request("/rest/v1/rpc/create_swap_request", "POST", token, JSONObject().put("p_recipient_id", recipientId))
                }
                message = "Swap request sent!"
                loadSwaps(token)
            } catch (e: Exception) { message = e.message ?: "Could not send swap request." }
            busy = false
        }
    }

    fun respondToSwap(requestId: String, status: String) {
        val token = accessToken ?: return
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    request("/rest/v1/rpc/respond_to_swap_request", "POST", token,
                        JSONObject().put("p_request_id", requestId).put("p_status", status))
                }
                message = if (status == "accepted") "Swap accepted. You're matched!" else "Request declined."
                loadSwaps(token)
            } catch (e: Exception) { message = e.message ?: "Could not update this request." }
            busy = false
        }
    }

    fun markSwapComplete(requestId: String) {
        val token = accessToken ?: return
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    request("/rest/v1/rpc/mark_swap_complete", "POST", token, JSONObject().put("p_request_id", requestId))
                }
                message = "Your progress has been saved."
                loadSwaps(token)
            } catch (e: Exception) { message = e.message ?: "Could not update swap progress." }
            busy = false
        }
    }


    fun loadRoomMessages(requestId: String, token: String) {
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val data = request("/rest/v1/swap_messages?select=id,request_id,sender_id,body,created_at&request_id=eq.$requestId&order=created_at.asc&limit=50", token = token).optJSONArray("data") ?: JSONArray()
                    (0 until data.length()).map { i ->
                        val item = data.getJSONObject(i)
                        RoomMessage(item.optString("id"), item.optString("request_id"), item.optString("sender_id"), item.optString("body"), item.optString("created_at"))
                    }
                }
                roomMessages = result
            } catch (e: Exception) { message = e.message ?: "Could not load room messages." }
        }
    }

    fun sendRoomMessage(requestId: String) {
        val token = accessToken ?: return
        val bodyText = messageDraft.trim()
        if (bodyText.isBlank() || bodyText.length > 1000) return
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    request("/rest/v1/swap_messages", "POST", token,
                        JSONObject().put("request_id", requestId).put("sender_id", userId).put("body", bodyText),
                        "return=minimal")
                }
                messageDraft = ""
                loadRoomMessages(requestId, token)
            } catch (e: Exception) { message = e.message ?: "Could not send this message." }
            busy = false
        }
    }

    fun loadLeaderboard(token: String) {
        scope.launch {
            busy = true
            try {
                val result = withContext(Dispatchers.IO) {
                    val data = request("/rest/v1/swap_xp_awards?select=user_id,points,profiles!swap_xp_awards_user_id_fkey(display_name)&order=created_at.desc&limit=1000", token = token).optJSONArray("data") ?: JSONArray()
                    val totals = linkedMapOf<String, Pair<String, Int>>()
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        val id = item.optString("user_id")
                        val name = item.optJSONObject("profiles")?.optString("display_name").orEmpty().ifBlank { "Member" }
                        val previous = totals[id]
                        totals[id] = name to ((previous?.second ?: 0) + item.optInt("points"))
                    }
                    totals.map { (id, pair) -> LeaderboardEntry(id, pair.first, pair.second) }.sortedByDescending { it.points }
                }
                leaderboard = result
                message = "Leaderboard updated."
            } catch (e: Exception) { message = e.message ?: "Could not load leaderboard." }
            busy = false
        }
    }

    fun loadNotifications(token: String) {
        scope.launch {
            busy = true
            try {
                val result = withContext(Dispatchers.IO) {
                    val data = request("/rest/v1/notifications?select=id,type,title,body,is_read,created_at,request_id&order=created_at.desc&limit=50", token = token).optJSONArray("data") ?: JSONArray()
                    (0 until data.length()).map { i ->
                        val item = data.getJSONObject(i)
                        AppNotification(item.optString("id"), item.optString("type"), item.optString("title"), item.optString("body"),
                            item.optBoolean("is_read"), item.optString("created_at"), if (item.isNull("request_id")) null else item.optString("request_id"))
                    }
                }
                notifications = result
            } catch (e: Exception) { message = e.message ?: "Could not load notifications." }
            busy = false
        }
    }

    fun markNotificationRead(id: String) {
        val token = accessToken ?: return
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    request("/rest/v1/notifications?id=eq.$id", "PATCH", token, JSONObject().put("is_read", true), "return=minimal")
                }
                notifications = notifications.map { if (it.id == id) it.copy(isRead = true) else it }
            } catch (e: Exception) { message = e.message ?: "Could not update notification." }
        }
    }

    fun changeAccountPassword(currentPassword: String, newPassword: String) {
        val token = accessToken ?: return
        if (currentPassword.isBlank() || newPassword.length < 8) {
            message = "Enter your current password and a new password with at least 8 characters."
            return
        }
        scope.launch {
            busy = true
            try {
                val verified = authRequest("/auth/v1/token?grant_type=password",
                    JSONObject().put("email", email).put("password", currentPassword))
                val freshToken = verified.optString("access_token").ifBlank { token }
                withContext(Dispatchers.IO) {
                    request("/auth/v1/user", "PUT", freshToken, JSONObject().put("password", newPassword))
                }
                message = "Password updated successfully."
            } catch (e: Exception) { message = e.message ?: "Password could not be changed." }
            busy = false
        }
    }

    fun establishSession(session: JSONObject) {
        accessToken = session.optString("access_token").takeIf { it.isNotBlank() }
        val refresh = session.optString("refresh_token")
        userId = session.optJSONObject("user")?.optString("id")
        email = session.optJSONObject("user")?.optString("email") ?: emailInput
        if (!accessToken.isNullOrBlank()) {
            prefs.edit().putString("refresh_token", refresh).apply()
            val token = accessToken!!
            scope.launch {
                busy = true
                try {
                    val profile = withContext(Dispatchers.IO) {
                        val url = "$SUPABASE_URL/rest/v1/profiles?select=display_name,teaches,learns,about&id=eq.$userId"
                        val c = URL(url).openConnection() as HttpURLConnection
                        try {
                            c.setRequestProperty("apikey", SUPABASE_KEY)
                            c.setRequestProperty("Authorization", "Bearer $token")
                            val raw = c.inputStream.bufferedReader().use { it.readText() }
                            val a = JSONArray(raw)
                            if (a.length() == 0) null else a.getJSONObject(0)
                        } finally { c.disconnect() }
                    }
                    if (profile == null) {
                        needsProfile = true
                        tab = "Account"
                        message = "Let's set up your skill profile."
                    } else {
                        displayName = profile.optString("display_name")
                        teaches = profile.optString("teaches")
                        learns = profile.optString("learns")
                        about = profile.optString("about")
                        needsProfile = false
                        tab = "Discover"
                        loadMembers(token)
                        loadSwaps(token)
                    }
                } catch (e: Exception) { message = e.message ?: "Signed in, but profile loading failed." }
                busy = false
            }
        } else {
            message = "Account created. Check your email for a confirmation link, then sign in."
        }
    }

    LaunchedEffect(Unit) {
        val refresh = prefs.getString("refresh_token", null)
        if (!refresh.isNullOrBlank()) {
            busy = true
            try {
                val session = authRequest("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refresh))
                establishSession(session)
            } catch (_: Exception) {
                prefs.edit().remove("refresh_token").apply()
                message = "Please sign in to continue."
            }
            busy = false
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF9BC69A), onPrimary = BackgroundDark, background = BackgroundDark, surface = SurfaceDark, onSurface = Cream, onBackground = Cream)) {
        if (accessToken == null) {
            AuthScreen(authMode, { authMode = it }, emailInput, { emailInput = it }, passwordInput, { passwordInput = it }, busy, message, {
                if (emailInput.isBlank() || passwordInput.length < 8) { message = "Enter a valid email and a password with at least 8 characters." }
                else scope.launch {
                    busy = true
                    message = if (authMode == "Sign in") "Signing in…" else "Creating account…"
                    try {
                        val path = if (authMode == "Sign in") "/auth/v1/token?grant_type=password" else "/auth/v1/signup"
                        val session = authRequest(path, JSONObject().put("email", emailInput.trim()).put("password", passwordInput))
                        establishSession(session)
                    } catch (e: Exception) { message = e.message ?: "Authentication failed." }
                    busy = false
                }
            })
        } else if (needsProfile) {
            ProfileSetupScreen(displayName, { displayName = it }, teaches, { teaches = it }, learns, { learns = it }, about, { about = it }, busy, message, {
                if (displayName.isBlank() || teaches.isBlank() || learns.isBlank()) message = "Add your name and both skills to continue."
                else scope.launch {
                    busy = true
                    try {
                        val body = JSONObject().put("id", userId).put("display_name", displayName.trim()).put("teaches", teaches.trim()).put("learns", learns.trim()).put("category", "Creative").put("about", about.trim()).put("swap_mode", "online").put("avatar_emoji", "🎓").put("skills_tags", JSONArray())
                        withContext(Dispatchers.IO) { request("/rest/v1/profiles?on_conflict=id", "POST", accessToken, body, "resolution=merge-duplicates,return=representation") }
                        needsProfile = false
                        message = "Profile saved!"
                        loadMembers(accessToken!!)
                        tab = "Discover"
                    } catch (e: Exception) { message = e.message ?: "Could not save profile." }
                    busy = false
                }
            }, onSignOut = { prefs.edit().remove("refresh_token").apply(); accessToken = null; userId = null; needsProfile = false })
        } else {
            Scaffold(containerColor = BackgroundDark, bottomBar = {
                NavigationBar(containerColor = SurfaceDark, contentColor = Cream) {
                    listOf(Triple("Discover", Icons.Default.Explore, "Discover"), Triple("Swaps", Icons.Default.SwapHoriz, "Swaps"), Triple("Rooms", Icons.Default.Groups, "Rooms"), Triple("Ranks", Icons.Default.Leaderboard, "Ranks")).forEach { (label, icon, route) ->
                        NavigationBarItem(selected = tab == route, onClick = { tab = route }, icon = { Icon(icon, label) }, label = { Text(label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Cream, selectedTextColor = Cream, indicatorColor = ForestLight, unselectedIconColor = Muted, unselectedTextColor = Muted))
                    }
                }
            }) { padding ->
                when (tab) {
                    "Discover" -> DiscoverScreen(
                        Modifier.padding(padding), displayName, members, busy, message,
                        swaps.filter { it.senderId == userId && it.status in listOf("pending", "accepted") }.map { it.recipientId }.toSet(),
                        { tab = "Account" }, { loadMembers(accessToken!!) }, { sendSwapRequest(it) }
                    )
                    "Swaps" -> SwapsScreen(
                        Modifier.padding(padding), swaps, userId.orEmpty(), busy, message,
                        { id, status -> respondToSwap(id, status) },
                        { id -> markSwapComplete(id) },
                        { accessToken?.let { loadSwaps(it) } }
                    )
                    "Account" -> AccountScreen(Modifier.padding(padding), displayName, email, teaches, learns, about, message, { needsProfile = true }, { prefs.edit().remove("refresh_token").apply(); accessToken = null; userId = null; members = emptyList(); tab = "Discover"; message = "Signed out." })
                    else -> PlaceholderScreen(tab, Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun AuthScreen(mode: String, setMode: (String) -> Unit, email: String, setEmail: (String) -> Unit, password: String, setPassword: (String) -> Unit, busy: Boolean, message: String, submit: () -> Unit) {
    Column(Modifier.fillMaxSize().background(BackgroundDark).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("SKILLSWAP AI", color = Color(0xFF9BC69A), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Learn. Share. Grow.", color = Cream, fontSize = 31.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("Swap what you know for what you want to learn. No money, just teamwork.", color = Muted)
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Sign in", "Create account").forEach { TextButton(onClick = { setMode(it) }) { Text(it, color = if (mode == it) Cream else Muted) } }
        }
        OutlinedTextField(email, setEmail, label = { Text("Email address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, setPassword, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(onClick = submit, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Please wait…" else mode) }
        Spacer(Modifier.height(12.dp))
        Text(message, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ProfileSetupScreen(name: String, setName: (String) -> Unit, teach: String, setTeach: (String) -> Unit, learn: String, setLearn: (String) -> Unit, about: String, setAbout: (String) -> Unit, busy: Boolean, message: String, save: () -> Unit, onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize().background(BackgroundDark).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("YOUR SKILL PROFILE", color = Color(0xFF9BC69A), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Let's get you started.", color = Cream, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Tell the community what you can teach and what you'd like to learn.", color = Muted)
        OutlinedTextField(name, setName, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(teach, setTeach, label = { Text("I can teach") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(learn, setLearn, label = { Text("I want to learn") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(about, setAbout, label = { Text("About me (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Button(onClick = save, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Saving…" else "Save my profile") }
        Text(message, color = Muted, fontSize = 12.sp)
        TextButton(onClick = onSignOut) { Text("Sign out", color = Muted) }
    }
}

@Composable
private fun DiscoverScreen(
    modifier: Modifier, name: String, members: List<Member>, busy: Boolean, message: String,
    requestedMemberIds: Set<String>, onAccount: () -> Unit, onRefresh: () -> Unit,
    onRequestSwap: (String) -> Unit
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SKILLSWAP AI", color = Color(0xFF9BC69A), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Hey, $name!", color = Cream, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onAccount) { Icon(Icons.Default.AccountCircle, "Account", tint = Cream) }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Forest).padding(20.dp)) {
                Text("YOUR NEXT SKILL STARTS HERE", color = Color(0xFFB9D6B2), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Trade what you know\nfor what you want to learn.", color = Cream, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("No money. Just skills, curiosity and community.", color = Muted, fontSize = 13.sp)
            }
        }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Explore the community", color = Cream, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text(message, color = Muted, fontSize = 11.sp) }; TextButton(onClick = onRefresh) { Text("Refresh") } } }
        if (members.isEmpty()) item { Text(if (busy) "Loading members…" else "No other profiles yet. Invite a friend to join SkillSwap!", color = Muted, modifier = Modifier.padding(12.dp)) }
        items(members, key = { it.id }) { member ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SurfaceDark).padding(16.dp)) {
                Text(member.name, color = Cream, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Can teach: ${member.teaches}", color = Color(0xFF9BC69A), fontSize = 13.sp)
                Text("Wants to learn: ${member.learns}", color = Muted, fontSize = 13.sp)
                if (member.about.isNotBlank()) Text(member.about, color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                val alreadyRequested = member.id in requestedMemberIds
                Button(onClick = { onRequestSwap(member.id) }, enabled = !busy && !alreadyRequested, modifier = Modifier.fillMaxWidth()) {
                    Text(if (alreadyRequested) "Request sent / swap active" else "Request a skill swap")
                }
            }
        }
    }
}

@Composable
private fun SwapsScreen(
    modifier: Modifier, swaps: List<Swap>, userId: String, busy: Boolean, message: String,
    onRespond: (String, String) -> Unit, onComplete: (String) -> Unit, onRefresh: () -> Unit
) {
    val incoming = swaps.filter { it.recipientId == userId && it.status == "pending" }
    val outgoing = swaps.filter { it.senderId == userId && it.status == "pending" }
    val active = swaps.filter { it.status == "accepted" }
    val history = swaps.filter { it.status == "declined" || it.status == "completed" }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YOUR SWAPS", color = Color(0xFF9BC69A), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Swap hub", color = Cream, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
            Text(message, color = Muted, fontSize = 12.sp)
        }
        item { Text("Incoming requests · ${incoming.size}", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        if (incoming.isEmpty()) item { Text("No new requests right now.", color = Muted, fontSize = 13.sp) }
        items(incoming, key = { "in-${it.id}" }) { SwapCard(it, userId, busy, onRespond, onComplete) }
        item { Text("Sent by you · ${outgoing.size}", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        if (outgoing.isEmpty()) item { Text("You haven't sent any pending requests.", color = Muted, fontSize = 13.sp) }
        items(outgoing, key = { "out-${it.id}" }) { SwapCard(it, userId, busy, onRespond, onComplete) }
        item { Text("Active swaps · ${active.size}", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        if (active.isEmpty()) item { Text("Accepted swaps will appear here.", color = Muted, fontSize = 13.sp) }
        items(active, key = { "active-${it.id}" }) { SwapCard(it, userId, busy, onRespond, onComplete) }
        item { Text("History · ${history.size}", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        if (history.isEmpty()) item { Text("Completed and declined requests will appear here.", color = Muted, fontSize = 13.sp) }
        items(history, key = { "history-${it.id}" }) { SwapCard(it, userId, busy, onRespond, onComplete) }
    }
}

@Composable
private fun SwapCard(
    swap: Swap, userId: String, busy: Boolean,
    onRespond: (String, String) -> Unit, onComplete: (String) -> Unit
) {
    val sentByMe = swap.senderId == userId
    val partner = if (sentByMe) swap.recipientName else swap.senderName
    val myCompleted = if (sentByMe) swap.senderCompleted else swap.recipientCompleted
    val partnerCompleted = if (sentByMe) swap.recipientCompleted else swap.senderCompleted
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SurfaceDark).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(partner, color = Cream, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text(swap.status.replaceFirstChar { it.uppercase() }, color = Color(0xFF9BC69A), fontSize = 12.sp)
        }
        Text("Skill to learn: ${swap.skill}", color = Cream, fontSize = 14.sp)
        Text(if (sentByMe) "You sent this request" else "They requested a swap with you", color = Muted, fontSize = 12.sp)
        when (swap.status) {
            "pending" -> if (!sentByMe) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onRespond(swap.id, "accepted") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Accept") }
                    OutlinedButton(onClick = { onRespond(swap.id, "declined") }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Decline") }
                }
            } else Text("Waiting for their response…", color = Muted, fontSize = 13.sp)
            "accepted" -> {
                Text("Your completion: ${if (myCompleted) "done" else "not marked"} · Partner: ${if (partnerCompleted) "done" else "not marked"}", color = Muted, fontSize = 12.sp)
                if (!myCompleted) Button(onClick = { onComplete(swap.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Mark my part complete") }
                else Text(if (partnerCompleted) "Both marked complete!" else "Your part is saved. Waiting for your partner.", color = Color(0xFF9BC69A), fontSize = 12.sp)
            }
            "completed" -> Text("Swap completed successfully.", color = Color(0xFF9BC69A), fontSize = 13.sp)
            "declined" -> Text("This request was declined.", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AccountScreen(modifier: Modifier, name: String, email: String, teach: String, learn: String, about: String, message: String, edit: () -> Unit, signOut: () -> Unit) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("‹  Account", color = Color(0xFF9BC69A), fontSize = 14.sp)
        Text(name, color = Cream, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(email, color = Muted)
        Text("I can teach: $teach", color = Cream)
        Text("I want to learn: $learn", color = Cream)
        if (about.isNotBlank()) Text(about, color = Muted)
        Text(message, color = Muted, fontSize = 12.sp)
        Button(onClick = edit) { Text("Edit profile") }
        OutlinedButton(onClick = signOut) { Text("Sign out") }
    }
}

@Composable
private fun PlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Cream, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("This feature is next on the native build roadmap.", color = Muted, fontSize = 14.sp)
    }
}
