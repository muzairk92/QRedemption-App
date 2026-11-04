package com.bingoinnovation.redemptiontester.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.bingoinnovation.redemptiontester.di.AppContainer
import com.bingoinnovation.redemptiontester.ui.history.HistoryScreen
import com.bingoinnovation.redemptiontester.ui.history.HistoryViewModel
import com.bingoinnovation.redemptiontester.ui.scan.ScanScreen
import com.bingoinnovation.redemptiontester.ui.scan.ScanViewModel
import com.bingoinnovation.redemptiontester.ui.settings.SettingsScreen
import com.bingoinnovation.redemptiontester.ui.settings.SettingsViewModel
import com.bingoinnovation.redemptiontester.ui.setup.CallerSetupScreen
import com.bingoinnovation.redemptiontester.ui.setup.CallerSetupViewModel
import com.bingoinnovation.redemptiontester.ui.ticket.TicketScreen
import com.bingoinnovation.redemptiontester.ui.ticket.TicketViewModel
import kotlinx.serialization.Serializable

/** Scan or type the Caller label. [replacing] is true when opened from Settings. */
@Serializable
data class CallerSetupRoute(val replacing: Boolean = false)

/** Home: scan a customer ticket. */
@Serializable
data object ScanRoute

/** Review and redeem one ticket. [attemptId] reopens an entry from the history. */
@Serializable
data class TicketRoute(val qr: String, val attemptId: String? = null)

@Serializable
data object HistoryRoute

@Serializable
data object SettingsRoute

@Composable
fun AppNavHost(
    container: AppContainer,
    startRoute: Any,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = startRoute) {

        composable<CallerSetupRoute> { entry ->
            val route = entry.toRoute<CallerSetupRoute>()
            CallerSetupScreen(
                viewModel = viewModel(factory = CallerSetupViewModel.factory(container)),
                onBack = if (route.replacing) ({ navController.popBackStack() }) else null,
                onSaved = {
                    if (route.replacing) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(ScanRoute) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable<ScanRoute> {
            ScanScreen(
                viewModel = viewModel(factory = ScanViewModel.factory(container)),
                onTicketScanned = { qr -> navController.navigate(TicketRoute(qr)) { launchSingleTop = true } },
                onOpenHistory = { navController.navigate(HistoryRoute) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
            )
        }

        composable<TicketRoute> { entry ->
            val route = entry.toRoute<TicketRoute>()
            TicketScreen(
                viewModel = viewModel(factory = TicketViewModel.factory(container, route.qr, route.attemptId)),
                onBack = { navController.popBackStack() },
                onScanNext = { navController.popBackStack(ScanRoute, inclusive = false) },
            )
        }

        composable<HistoryRoute> {
            HistoryScreen(
                viewModel = viewModel(factory = HistoryViewModel.factory(container)),
                onBack = { navController.popBackStack() },
                onOpenAttempt = { attempt ->
                    navController.navigate(TicketRoute(qr = attempt.ticketQr, attemptId = attempt.id)) {
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(
                viewModel = viewModel(factory = SettingsViewModel.factory(container)),
                onBack = { navController.popBackStack() },
                onReplaceCaller = {
                    navController.navigate(CallerSetupRoute(replacing = true)) { launchSingleTop = true }
                },
                onCallerRemoved = {
                    navController.navigate(CallerSetupRoute()) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }
    }
}
