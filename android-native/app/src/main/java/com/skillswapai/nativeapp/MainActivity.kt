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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val SUPABASE_URL = "https://qmjmfjgqfmrrdlgmjksh.supabase.co"
private const val SUPABASE_KEY = "sb_publishable_gOwrGejFZia29Yl8GlzI7A_RNenYsud"
private val Forest = Color(0xFF173B2A)
private val ForestLight = Color(0xFF244B36)
private val Cream = Color(0xFFF0E8D5)
private val Muted = Color(0xFFA9B8AA)
private val SurfaceDark = Color(0xFF1C2921)
private val BackgroundDark = Color(0xFF101812)

data class Member(val id: String, val name: String, val teaches: String, val learns: String, val about: String)

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
        val json = if (text.isBlank()) JSONObject() else JSONObject(text)
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
                        val body = JSONObject().put("id", userId).put("display_name", displayName.trim()).put("teaches", teaches.trim()).put("learns", learns.trim()).put("category", "General").put("about", about.trim()).put("swap_mode", "online").put("avatar_emoji", "🎓").put("skills_tags", JSONArray())
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
                    "Discover" -> DiscoverScreen(Modifier.padding(padding), displayName, members, busy, message, { tab = "Account" }, { loadMembers(accessToken!!) })
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
private fun DiscoverScreen(modifier: Modifier, name: String, members: List<Member>, busy: Boolean, message: String, onAccount: () -> Unit, onRefresh: () -> Unit) {
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
            }
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
