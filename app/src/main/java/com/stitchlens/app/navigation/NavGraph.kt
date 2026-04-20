package com.stitchlens.app.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.stitchlens.app.ui.screens.*
import com.stitchlens.app.viewmodel.ScanViewModel

object Routes {
    const val LANDING = "landing"
    const val CAMERA = "camera"
    const val REVIEW = "review"
    const val CROP = "crop/{pageIndex}"
    const val PREVIEW = "preview"
    const val SHARE = "share"
    const val RECENT = "recent"

    fun cropRoute(pageIndex: Int) = "crop/$pageIndex"
}

@Composable
fun StitchLensNavGraph(
    navController: NavHostController,
    viewModel: ScanViewModel = viewModel()
) {
    NavHost(navController = navController, startDestination = Routes.LANDING) {
        composable(Routes.LANDING) {
            LandingScreen(
                onStartScanning = { navController.navigate(Routes.CAMERA) },
                onRecentScans = { navController.navigate(Routes.RECENT) }
            )
        }
        composable(Routes.CAMERA) {
            CameraScreen(
                viewModel = viewModel,
                onDone = { navController.navigate(Routes.REVIEW) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.REVIEW) {
            ReviewScreen(
                viewModel = viewModel,
                onConfirm = { navController.navigate(Routes.PREVIEW) },
                onAddMore = { navController.navigate(Routes.CAMERA) },
                onCropPage = { pageIndex ->
                    navController.navigate(Routes.cropRoute(pageIndex))
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.CROP,
            arguments = listOf(navArgument("pageIndex") { type = NavType.IntType })
        ) { backStackEntry ->
            val pageIndex = backStackEntry.arguments?.getInt("pageIndex") ?: 0
            CropScreen(
                viewModel = viewModel,
                pageIndex = pageIndex,
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.PREVIEW) {
            PdfPreviewScreen(
                viewModel = viewModel,
                onShare = { navController.navigate(Routes.SHARE) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SHARE) {
            ShareScreen(
                viewModel = viewModel,
                onDone = {
                    viewModel.reset()
                    navController.popBackStack(Routes.LANDING, inclusive = false)
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.RECENT) {
            RecentScansScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
