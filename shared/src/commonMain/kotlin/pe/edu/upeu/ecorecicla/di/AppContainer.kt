package pe.edu.upeu.ecorecicla.di

import app.cash.sqldelight.db.SqlDriver
import pe.edu.upeu.ecorecicla.data.local.DatabaseManager
import pe.edu.upeu.ecorecicla.data.remote.EcoApiClient
import pe.edu.upeu.ecorecicla.data.repository.PesajeRepository
import pe.edu.upeu.ecorecicla.data.repository.RutaRepository
import pe.edu.upeu.ecorecicla.data.repository.SesionRepository
import pe.edu.upeu.ecorecicla.data.repository.SincronizacionRepository

object AppContainer {
    var databaseManager: DatabaseManager? = null
        private set
    var apiClient: EcoApiClient? = null
        private set
    var sesionRepository: SesionRepository? = null
        private set
    var rutaRepository: RutaRepository? = null
        private set
    var pesajeRepository: PesajeRepository? = null
        private set
    var syncRepository: SincronizacionRepository? = null
        private set

    fun initialize(driver: SqlDriver, baseUrl: String = "http://10.0.2.2:8080", httpClient: io.ktor.client.HttpClient? = null) {
        val dbMan = DatabaseManager(driver)
        val client = httpClient ?: EcoApiClient.crearHttpClient()
        val api = EcoApiClient(client, baseUrl = baseUrl)
        val sesionRepo = SesionRepository(dbMan, api)
        val rutaRepo = RutaRepository(dbMan, api)
        val pesajeRepo = PesajeRepository(dbMan)
        val syncRepo = SincronizacionRepository(dbMan, api, sesionRepo)

        this.databaseManager = dbMan
        this.apiClient = api
        this.sesionRepository = sesionRepo
        this.rutaRepository = rutaRepo
        this.pesajeRepository = pesajeRepo
        this.syncRepository = syncRepo
    }
}

