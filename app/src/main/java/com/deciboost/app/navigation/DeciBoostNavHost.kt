package com.deciboost.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.deciboost.feature.boost.BoostScreen
import com.deciboost.feature.settings.OnboardingScreen
import com.deciboost.feature.settings.OnboardingViewModel
import com.deciboost.feature.settings.AboutScreen
import com.deciboost.feature.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val BOOST = "boost"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
}

@Composable
fun DeciBoostNavHost() {
    val navController = rememberNavController()
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val onboardingComplete by onboardingViewModel.onboardingComplete.collectAsStateWithLifecycle()

    val startDestination = if (onboardingComplete) Routes.BOOST else Routes.ONBOARDING

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Routes.BOOST) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.BOOST) {
            BoostScreen(onNavigateToSettings = { navController.navigate(Routes.SETTINGS) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToAbout = { navController.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
