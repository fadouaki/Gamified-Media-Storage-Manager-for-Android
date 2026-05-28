package compress.joshattic.us.swipetoclean.ui.screens.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val cardOffsetX = remember { Animatable(0f) }
    var showBranding by remember { mutableStateOf(false) }
    var showTagline by remember { mutableStateOf(false) }
    var showIcons by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Phase 1: Card slides in to center
        cardOffsetX.animateTo(
            targetValue = 0f,
            animationSpec = tween(600, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)),
        )
        delay(200)

        // Phase 2: Card swipes off-screen to the right, revealing branding
        showBranding = true
        cardOffsetX.animateTo(
            targetValue = 1200f,
            animationSpec = tween(500, easing = FastOutSlowInEasing),
        )
        delay(300)

        // Phase 3: Tagline fades in
        showTagline = true
        delay(400)

        // Phase 4: Feature icons appear
        showIcons = true
        delay(700)

        // Navigate to next screen
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460),
                    ),
                    center = Offset(400f, 800f),
                    radius = 1200f,
                )
            ),
    ) {
        SplashParticles()

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Card swipe area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Surface(
                    modifier = Modifier
                        .offset { IntOffset(cardOffsetX.value.roundToInt(), 0) }
                        .size(220.dp),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp,
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF533483), Color(0xFFE94560))
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = "Swipe to Clean Logo",
                                modifier = Modifier.size(64.dp),
                                tint = Color.White,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Icon(
                                Icons.Default.SwipeRight,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = Color.White.copy(alpha = 0.9f),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Swipe ->",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Branding — revealed after card swipes away
            AnimatedVisibility(
                visible = showBranding,
                enter = fadeIn(tween(600)) + slideInHorizontally(
                    animationSpec = tween(600),
                    initialOffsetX = { it }
                ),
                exit = fadeOut(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Swipe to",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Light,
                        color = Color.White.copy(alpha = 0.9f),
                        letterSpacing = 2.sp,
                    )
                    Text(
                        text = "CLEAN",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFE94560),
                        letterSpacing = 8.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tagline
            AnimatedVisibility(
                visible = showTagline,
                enter = fadeIn(tween(800)),
            ) {
                Text(
                    text = "Declutter your gallery.\nOne swipe at a time.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.87f),
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Feature dots
            AnimatedVisibility(
                visible = showIcons,
                enter = fadeIn(tween(600)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    FeatureDot(Icons.Default.SwipeRight, "Swipe", Color(0xFF4CAF50))
                    FeatureDot(Icons.Default.Delete, "Clean", Color(0xFFE94560))
                    FeatureDot(Icons.Default.AutoAwesome, "Enjoy", Color(0xFF2196F3))
                }
            }
        }
    }
}

@Composable
private fun FeatureDot(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f))
                .border(1.5.dp, color.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp), tint = color)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SplashParticles() {
    val particles = listOf(
        Offset(80f, 200f) to 6f,
        Offset(300f, 100f) to 4f,
        Offset(250f, 700f) to 5f,
        Offset(100f, 500f) to 3f,
        Offset(350f, 400f) to 4f,
        Offset(180f, 300f) to 5f,
    )
    particles.forEach { (offset, radius) ->
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .size((radius * 2).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
        )
    }
}
