package pe.edu.upeu.examen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import pe.edu.upeu.ecorecicla.di.AppContainer
import pe.edu.upeu.ecorecicla.presentation.EcoRutasApp

@Composable
fun App() {
    MaterialTheme {
        val sesionRepo = AppContainer.sesionRepository
        val rutaRepo = AppContainer.rutaRepository
        val pesajeRepo = AppContainer.pesajeRepository
        val syncRepo = AppContainer.syncRepository

        if (sesionRepo != null && rutaRepo != null && pesajeRepo != null && syncRepo != null) {
            EcoRutasApp(
                sesionRepo = sesionRepo,
                rutaRepo = rutaRepo,
                pesajeRepo = pesajeRepo,
                syncRepo = syncRepo
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Iniciando EcoRutas...")
            }
        }
    }
}
