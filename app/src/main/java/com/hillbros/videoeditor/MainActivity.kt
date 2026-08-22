package com.hillbros.videoeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.hillbros.videoeditor.ui.screens.EditorScreen
import com.hillbros.videoeditor.ui.screens.ProjectsScreen
import com.hillbros.videoeditor.ui.theme.HillBrosTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HillBrosTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = ROUTE_PROJECTS) {
                        composable(ROUTE_PROJECTS) {
                            ProjectsScreen(
                                onOpenProject = { projectId ->
                                    navController.navigate("$ROUTE_EDITOR/$projectId")
                                },
                            )
                        }
                        composable(
                            route = "$ROUTE_EDITOR/{$ARG_PROJECT_ID}",
                            arguments = listOf(
                                navArgument(ARG_PROJECT_ID) { type = NavType.StringType },
                            ),
                        ) { backStackEntry ->
                            val projectId =
                                backStackEntry.arguments?.getString(ARG_PROJECT_ID).orEmpty()
                            EditorScreen(
                                projectId = projectId,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val ROUTE_PROJECTS = "projects"
        const val ROUTE_EDITOR = "editor"
        const val ARG_PROJECT_ID = "projectId"
    }
}
