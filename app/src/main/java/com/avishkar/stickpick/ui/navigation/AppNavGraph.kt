package com.avishkar.stickpick.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.avishkar.stickpick.ui.screens.*
import com.avishkar.stickpick.viewmodel.MainViewModel

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val DOWNLOADING = "downloading"
    const val PREVIEW = "preview"
    const val CONVERTING = "converting"
    const val MY_PACKS = "my_packs"
    const val SETTINGS = "settings"

    val TAB_ROUTES = setOf(HOME, MY_PACKS, SETTINGS)
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { inclusive = (route == Routes.HOME) }
        launchSingleTop = true
    }
}

private val tabEnter: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
    fadeIn(tween(180))
}
private val tabExit: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
    fadeOut(tween(120))
}
private val flowEnter: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
    slideInHorizontally(tween(300)) { it / 3 } + fadeIn(tween(300))
}
private val flowExit: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
    slideOutHorizontally(tween(250)) { -it / 3 } + fadeOut(tween(250))
}
private val flowPopEnter: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
    slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(300))
}
private val flowPopExit: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
    slideOutHorizontally(tween(250)) { it / 3 } + fadeOut(tween(250))
}

@Composable
fun AppNavGraph(navController: NavHostController, vm: MainViewModel = viewModel()) {
    val onboardingDone by vm.onboardingComplete.collectAsState()
    val isReady by vm.isReady.collectAsState()

    if (!isReady) return

    val startDest = Routes.MY_PACKS

    val tabNavigate: (String) -> Unit = { route ->
        if (route == Routes.HOME) vm.resetWorkflow()
        navController.navigateTab(route)
    }

    NavHost(
        navController = navController,
        startDestination = startDest,
        enterTransition = flowEnter,
        exitTransition = flowExit,
        popEnterTransition = flowPopEnter,
        popExitTransition = flowPopExit
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(vm = vm, onComplete = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
        composable(
            Routes.HOME,
            enterTransition = tabEnter, exitTransition = tabExit,
            popEnterTransition = tabEnter, popExitTransition = tabExit
        ) {
            HomeScreen(vm = vm, onNavigate = tabNavigate,
                onFetchComplete = { navController.navigate(Routes.DOWNLOADING) })
        }
        composable(Routes.DOWNLOADING) {
            DownloadingScreen(vm = vm,
                onComplete = { navController.navigate(Routes.PREVIEW) { popUpTo(Routes.HOME) } },
                onCancel = { vm.resetWorkflow(); navController.popBackStack(Routes.HOME, false) })
        }
        composable(Routes.PREVIEW) {
            PreviewScreen(vm = vm,
                onConvert = { navController.navigate(Routes.CONVERTING) },
                onBack = { vm.resetWorkflow(); navController.popBackStack(Routes.HOME, false) })
        }
        composable(Routes.CONVERTING) {
            ConvertingScreen(vm = vm,
                onComplete = { navController.navigate(Routes.MY_PACKS) { popUpTo(Routes.HOME) { inclusive = false } } })
        }
        composable(
            Routes.MY_PACKS,
            enterTransition = tabEnter, exitTransition = tabExit,
            popEnterTransition = tabEnter, popExitTransition = tabExit
        ) {
            MyPacksScreen(vm = vm, onNavigate = tabNavigate)
        }
        composable(
            Routes.SETTINGS,
            enterTransition = tabEnter, exitTransition = tabExit,
            popEnterTransition = tabEnter, popExitTransition = tabExit
        ) {
            SettingsScreen(vm = vm, onBack = { navController.popBackStack() }, onNavigate = tabNavigate)
        }
    }
}
