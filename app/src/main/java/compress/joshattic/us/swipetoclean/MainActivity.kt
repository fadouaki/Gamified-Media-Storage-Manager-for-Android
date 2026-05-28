package compress.joshattic.us.swipetoclean

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import compress.joshattic.us.swipetoclean.data.AppDatabase
import compress.joshattic.us.swipetoclean.navigation.Route
import compress.joshattic.us.swipetoclean.ui.screens.compressqueue.CompressQueueScreen
import compress.joshattic.us.swipetoclean.ui.screens.dashboard.DashboardScreen
import compress.joshattic.us.swipetoclean.ui.screens.onboarding.OnboardingScreen
import compress.joshattic.us.swipetoclean.ui.screens.reviewbin.ReviewBinScreen
import compress.joshattic.us.swipetoclean.ui.screens.splash.SplashScreen
import compress.joshattic.us.swipetoclean.ui.screens.swipearena.SwipeArenaScreen
import compress.joshattic.us.swipetoclean.ui.theme.SwipeToCleanTheme

class MainActivity : ComponentActivity() {

    private val database: AppDatabase by lazy {
        (application as SwipeToCleanApp).database
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions result handled in composable */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SwipeToCleanTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SwipeToCleanNavHost(database)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasStoragePermission()) {
            requestPermissions()
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        requestPermissionLauncher.launch(permissions)
    }
}

@Composable
fun SwipeToCleanNavHost(database: AppDatabase) {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    // Preferences for onboarding state
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val isOnboardingComplete = remember { prefs.getBoolean("onboarding_complete", false) }

    // Start on splash screen
    val startDestination = Route.Splash.route

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Route.Splash.route) {
            SplashScreen(
                onFinished = {
                    val nextRoute = if (isOnboardingComplete) Route.Dashboard.route else Route.Onboarding.route
                    navController.navigate(nextRoute) {
                        popUpTo(Route.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    // Mark onboarding as complete persistently
                    prefs.edit().putBoolean("onboarding_complete", true).apply()
                    
                    navController.navigate(Route.Dashboard.route) {
                        popUpTo(Route.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.Dashboard.route) {
            val lastIndex = remember { prefs.getInt("last_swipe_index", 0) }
            var showResumeDialog by remember { mutableStateOf(false) }

            DashboardScreen(
                database = database,
                onStartSwiping = {
                    if (lastIndex > 0) {
                        showResumeDialog = true
                    } else {
                        navController.navigate(Route.SwipeArena.createRoute(0))
                    }
                },
                onEmptyTrash = {
                    navController.navigate(Route.ReviewBin.createRoute("delete"))
                },
                onCompressVideos = {
                    navController.navigate(Route.CompressQueue.route)
                }
            )

            if (showResumeDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showResumeDialog = false },
                    title = { androidx.compose.material3.Text("Resume Session?") },
                    text = { androidx.compose.material3.Text("Would you like to continue from where you left off or start from the beginning?") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            showResumeDialog = false
                            navController.navigate(Route.SwipeArena.createRoute(lastIndex))
                        }) { androidx.compose.material3.Text("Continue") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            showResumeDialog = false
                            prefs.edit().putInt("last_swipe_index", 0).apply()
                            navController.navigate(Route.SwipeArena.createRoute(0))
                        }) { androidx.compose.material3.Text("Start Over") }
                    }
                )
            }
        }

        composable(
            route = Route.SwipeArena.route,
            arguments = listOf(navArgument("startIndex") { 
                type = NavType.IntType
                defaultValue = 0 
            })
        ) { backStackEntry ->
            val startIndex = backStackEntry.arguments?.getInt("startIndex") ?: 0
            SwipeArenaScreen(
                database = database,
                startIndex = startIndex,
                onNavigateBack = { navController.popBackStack() },
                onAllDone = { 
                    prefs.edit().putInt("last_swipe_index", 0).apply()
                    navController.popBackStack() 
                },
            )
        }

        composable(
            route = Route.ReviewBin.route,
            arguments = listOf(navArgument("filter") { type = NavType.StringType })
        ) { backStackEntry ->
            val filter = backStackEntry.arguments?.getString("filter") ?: "all"
            ReviewBinScreen(
                database = database,
                initialFilter = filter,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Route.CompressQueue.route) {
            CompressQueueScreen(
                database = database,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
