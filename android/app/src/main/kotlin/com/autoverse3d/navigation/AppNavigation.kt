package com.autoverse3d.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.autoverse3d.feature.aiassistant.AssistantScreen
import com.autoverse3d.feature.evexplorer.EvLabScreen
import com.autoverse3d.feature.explore.ExploreScreen
import com.autoverse3d.feature.home.HomeScreen
import com.autoverse3d.feature.learning.LearnScreen
import com.autoverse3d.feature.learning.ModuleScreen
import com.autoverse3d.feature.mygarage.GarageScreen
import com.autoverse3d.feature.showroom.ShowroomScreen
import com.autoverse3d.feature.vehicleviewer.VehicleViewerScreen

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

/** Main navigation from IMPLEMENTATION_PLAN §4: Home, Showroom, Explore, My Garage, Learn, AI Assistant. */
private val topLevel = listOf(
    TopLevel("home", "Home", Icons.Filled.Home),
    TopLevel("showroom", "Showroom", Icons.Filled.DirectionsCar),
    TopLevel("explore", "Explore", Icons.Filled.Explore),
    TopLevel("garage", "Garage", Icons.Filled.Garage),
    TopLevel("learn", "Learn", Icons.Filled.School),
    TopLevel("assistant", "Assistant", Icons.Filled.AutoAwesome),
)

fun NavHostController.openViewer(vehicleId: String, mode: String = "exterior", configId: String? = null) =
    navigate("viewer/$vehicleId?mode=$mode&configId=${configId ?: ""}")

@Composable
fun AppNavigation(nav: NavHostController = rememberNavController()) {
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route == null || route.substringBefore("?").substringBefore("/") in topLevel.map { it.route }
    Scaffold(
        bottomBar = {
            if (showBar) NavigationBar {
                topLevel.forEach { item ->
                    NavigationBarItem(
                        selected = route?.substringBefore("?") == item.route,
                        onClick = {
                            nav.navigate(item.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label, maxLines = 1, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("nav_${item.route}"),
                        alwaysShowLabel = true,
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") {
                HomeScreen(
                    onOpenVehicle = { id, mode -> nav.openViewer(id, mode) },
                    onOpenLearn = { nav.navigate("learn") },
                    onOpenEvLab = { nav.navigate("evlab") },
                    onOpenAssistant = { nav.navigate("assistant") },
                )
            }
            composable("showroom") { ShowroomScreen(onOpenVehicle = { nav.openViewer(it) }) }
            composable("explore") { ExploreScreen(onOpenMode = { id, mode -> nav.openViewer(id, mode) }, onOpenEvLab = { nav.navigate("evlab") }) }
            composable("garage") {
                GarageScreen(onOpenConfiguration = { nav.openViewer(it.vehicleId, "customize", it.id) }, onCreate = { nav.openViewer("urbanx_suv", "customize") })
            }
            composable("learn") { LearnScreen(onOpenModule = { nav.navigate("module/$it") }) }
            composable("module/{moduleId}", listOf(navArgument("moduleId") { type = NavType.StringType })) { ModuleScreen(onBack = { nav.popBackStack() }) }
            composable("evlab") { EvLabScreen(onBack = { nav.popBackStack() }) }
            composable(
                "assistant?vehicleId={vehicleId}&componentId={componentId}",
                listOf(
                    navArgument("vehicleId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("componentId") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { AssistantScreen() }
            composable(
                "viewer/{vehicleId}?mode={mode}&configId={configId}",
                listOf(
                    navArgument("vehicleId") { type = NavType.StringType },
                    navArgument("mode") { type = NavType.StringType; defaultValue = "exterior" },
                    navArgument("configId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                VehicleViewerScreen(
                    onBack = { nav.popBackStack() },
                    onAskAssistant = { v, c -> nav.navigate("assistant?vehicleId=$v&componentId=${c ?: ""}") },
                )
            }
        }
    }
}
