package com.skillswapai.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Forest = Color(0xFF173B2A)
private val ForestLight = Color(0xFF244B36)
private val Cream = Color(0xFFF0E8D5)
private val Muted = Color(0xFFA9B8AA)
private val SurfaceDark = Color(0xFF1C2921)
private val BackgroundDark = Color(0xFF101812)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 24, 18)
        window.navigationBarColor = android.graphics.Color.rgb(16, 24, 18)
        setContent { SkillSwapApp() }
    }
}

@Composable
private fun SkillSwapApp() {
    var tab by remember { mutableStateOf("Discover") }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF9BC69A),
            onPrimary = BackgroundDark,
            background = BackgroundDark,
            surface = SurfaceDark,
            onSurface = Cream,
            onBackground = Cream
        )
    ) {
        Scaffold(
            containerColor = BackgroundDark,
            bottomBar = {
                NavigationBar(containerColor = SurfaceDark, contentColor = Cream) {
                    val items = listOf(
                        Triple("Discover", Icons.Default.Explore, "Discover"),
                        Triple("Swaps", Icons.Default.SwapHoriz, "Swaps"),
                        Triple("Rooms", Icons.Default.Groups, "Rooms"),
                        Triple("Ranks", Icons.Default.Leaderboard, "Ranks")
                    )
                    items.forEach { (label, icon, route) ->
                        NavigationBarItem(
                            selected = tab == route,
                            onClick = { tab = route },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Cream,
                                selectedTextColor = Cream,
                                indicatorColor = ForestLight,
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        ) { padding ->
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "native-tab-transition",
                modifier = Modifier.padding(padding)
            ) { selected ->
                when (selected) {
                    "Discover" -> DiscoverScreen(onAccount = { tab = "Account" })
                    "Account" -> AccountScreen(onBack = { tab = "Discover" })
                    else -> PlaceholderScreen(selected)
                }
            }
        }
    }
}

@Composable
private fun DiscoverScreen(onAccount: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("SKILLSWAP AI", color = Color(0xFF9BC69A), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Learn. Share. Grow.", color = Cream, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
                Surface(
                    onClick = onAccount,
                    shape = CircleShape,
                    color = ForestLight,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Account", tint = Cream, modifier = Modifier.size(27.dp))
                    }
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Forest).padding(20.dp)
            ) {
                Text("YOUR NEXT SKILL STARTS HERE", color = Color(0xFFB9D6B2), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                Spacer(Modifier.height(8.dp))
                Text("Trade what you know\nfor what you want to learn.", color = Cream, fontSize = 23.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp)
                Spacer(Modifier.height(8.dp))
                Text("No money. Just skills, curiosity and community.", color = Muted, fontSize = 13.sp)
            }
        }
        item { SectionTitle("Explore the community", "Find your next skill partner") }
        item { FeatureCard("Discover members", "Find people who can teach what you want to learn.", "Explore", Icons.Default.Explore) }
        item { FeatureCard("Your swaps", "Keep track of requests and accepted skill exchanges.", "Coming in next build", Icons.Default.SwapHoriz) }
        item { FeatureCard("Mission Rooms", "A focused space for each accepted skill swap.", "Coming in next build", Icons.Default.Groups) }
        item {
            Text(
                "Native Android edition · Early build",
                color = Muted,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun FeatureCard(title: String, body: String, tag: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SurfaceDark).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(ForestLight), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Cream)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Cream, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(body, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            Text(tag, color = Color(0xFF9BC69A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Cream, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("This native screen is being built next.", color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun AccountScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("‹  Back", color = Color(0xFF9BC69A), modifier = Modifier.clickable { onBack() })
        Text("Your account", color = Cream, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Your profile dashboard will live here.", color = Muted, fontSize = 14.sp)
        Text("The native account screen will connect to your existing SkillSwap profile in the next stage.", color = Muted, fontSize = 13.sp)
    }
}
