package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.di.HookAppContainer
import com.example.ui.screens.CadenceScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ResultsScreen
import com.example.ui.viewmodel.CadenceViewModel
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.ResultsViewModel

object HookRoutes {
    const val HOME = "home"
    const val CADENCE = "cadence"
    const val RESULTS_PATTERN = "results/{videoId}"
    fun results(videoId: String) = "results/$videoId"
}

@Composable
fun HookNavGraph(container: HookAppContainer) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = HookRoutes.HOME
    ) {
        composable(HookRoutes.HOME) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.provideFactory(container.repository)
            )
            HomeScreen(
                viewModel = homeViewModel,
                onOpenResults = { videoId ->
                    navController.navigate(HookRoutes.results(videoId))
                },
                onOpenCadence = {
                    navController.navigate(HookRoutes.CADENCE)
                }
            )
        }

        composable(
            route = HookRoutes.RESULTS_PATTERN,
            arguments = listOf(navArgument("videoId") { type = NavType.StringType })
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId").orEmpty()
            val resultsViewModel: ResultsViewModel = viewModel(
                key = "results_$videoId",
                factory = ResultsViewModel.provideFactory(videoId, container.repository)
            )
            ResultsScreen(
                viewModel = resultsViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(HookRoutes.CADENCE) {
            val cadenceViewModel: CadenceViewModel = viewModel(
                factory = CadenceViewModel.provideFactory(container.repository)
            )
            CadenceScreen(
                viewModel = cadenceViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
