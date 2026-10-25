package org.openscanner.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.openscanner.ui.camera.CameraScreen
import org.openscanner.ui.crop.CropScreen
import org.openscanner.ui.detail.DocumentDetailScreen
import org.openscanner.ui.filter.FilterScreen
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import org.openscanner.ui.home.HomeScreen
import org.openscanner.ui.navigation.Screen
import org.openscanner.ui.theme.OpenScannerTheme
import org.openscanner.ui.theme.ThemeManager
import org.openscanner.ui.viewer.PageViewerScreen
import org.openscanner.ui.profile.ProfileScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ThemeManager.init(this)

        setContent {
            val context = LocalContext.current
            val themeMode by ThemeManager.themeMode.collectAsState()
            val isDark = when (themeMode) {
                org.openscanner.ui.theme.AppThemeMode.LIGHT -> false
                org.openscanner.ui.theme.AppThemeMode.DARK -> true
                org.openscanner.ui.theme.AppThemeMode.SYSTEM -> ThemeManager.isDark(context)
            }

            OpenScannerTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                onNavigateToCamera = { docId, mode ->
                                    navController.navigate(Screen.Camera.createRoute(docId, mode))
                                },
                                onNavigateToDetail = { docId ->
                                    navController.navigate(Screen.DocumentDetail.createRoute(docId))
                                },
                                onNavigateToProfile = {
                                    navController.navigate(Screen.Profile.route)
                                }
                            )
                        }

                        composable(
                            route = Screen.Camera.route,
                            arguments = listOf(
                                navArgument("docId") { type = NavType.LongType; defaultValue = -1L },
                                navArgument("mode") { type = NavType.StringType; defaultValue = "Single" }
                            )
                        ) { backStackEntry ->
                            val docId = backStackEntry.arguments?.getLong("docId") ?: -1L
                            val mode = backStackEntry.arguments?.getString("mode") ?: "Single"
                            CameraScreen(
                                docId = docId,
                                initialMode = mode,
                                onPhotoCaptured = { imagePath, targetDocId ->
                                    val encodedPath = Uri.encode(imagePath)
                                    navController.navigate(Screen.Crop.createRoute(encodedPath, targetDocId))
                                },
                                onClose = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Screen.Crop.route,
                            arguments = listOf(
                                navArgument("imagePath") { type = NavType.StringType },
                                navArgument("docId") { type = NavType.LongType; defaultValue = -1L }
                            )
                        ) { backStackEntry ->
                            val imagePath = Uri.decode(backStackEntry.arguments?.getString("imagePath").orEmpty())
                            val docId = backStackEntry.arguments?.getLong("docId") ?: -1L
                            CropScreen(
                                imagePath = imagePath,
                                docId = docId,
                                onNavigateToFilter = { path, dId, border ->
                                    val encodedPath = Uri.encode(path)
                                    val encodedBorder = Uri.encode(border)
                                    navController.navigate(Screen.Filter.createRoute(encodedPath, dId, encodedBorder))
                                },
                                onClose = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Screen.Filter.route,
                            arguments = listOf(
                                navArgument("imagePath") { type = NavType.StringType },
                                navArgument("docId") { type = NavType.LongType; defaultValue = -1L },
                                navArgument("border") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val imagePath = Uri.decode(backStackEntry.arguments?.getString("imagePath").orEmpty())
                            val docId = backStackEntry.arguments?.getLong("docId") ?: -1L
                            val border = Uri.decode(backStackEntry.arguments?.getString("border").orEmpty())
                            FilterScreen(
                                imagePath = imagePath,
                                docId = docId,
                                borderStr = border,
                                onSaved = { savedDocId ->
                                    navController.navigate(Screen.DocumentDetail.createRoute(savedDocId)) {
                                        popUpTo(Screen.Home.route)
                                    }
                                },
                                onClose = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Screen.DocumentDetail.route,
                            arguments = listOf(navArgument("docId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val docId = backStackEntry.arguments?.getLong("docId") ?: -1L
                            DocumentDetailScreen(
                                docId = docId,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToCamera = { dId ->
                                    navController.navigate(Screen.Camera.createRoute(dId, "Batch"))
                                },
                                onNavigateToViewer = { pageId ->
                                    navController.navigate(Screen.PageViewer.createRoute(pageId))
                                }
                            )
                        }

                        composable(
                            route = Screen.PageViewer.route,
                            arguments = listOf(navArgument("pageId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val pageId = backStackEntry.arguments?.getLong("pageId") ?: -1L
                            PageViewerScreen(
                                pageId = pageId,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.Profile.route) {
                            ProfileScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
