package org.openscanner.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Camera : Screen("camera?docId={docId}&mode={mode}") {
        fun createRoute(docId: Long = -1L, mode: String = "Single"): String = "camera?docId=$docId&mode=$mode"
    }
    object Crop : Screen("crop?imagePath={imagePath}&docId={docId}") {
        fun createRoute(imagePath: String, docId: Long): String = "crop?imagePath=$imagePath&docId=$docId"
    }
    object Filter : Screen("filter?imagePath={imagePath}&docId={docId}&border={border}") {
        fun createRoute(imagePath: String, docId: Long, border: String): String =
            "filter?imagePath=$imagePath&docId=$docId&border=$border"
    }
    object DocumentDetail : Screen("detail/{docId}") {
        fun createRoute(docId: Long): String = "detail/$docId"
    }
    object PageViewer : Screen("viewer/{pageId}") {
        fun createRoute(pageId: Long): String = "viewer/$pageId"
    }
    object Profile : Screen("profile")
}
