package pe.edu.upeu.ecorecicla.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pe.edu.upeu.ecorecicla.data.local.DatabaseManager
import pe.edu.upeu.ecorecicla.data.remote.EcoApiClient
import pe.edu.upeu.ecorecicla.data.remote.dto.*
import pe.edu.upeu.ecorecicla.domain.model.*
import kotlin.random.Random

class SesionRepository(
    private val dbManager: DatabaseManager,
    private val apiClient: EcoApiClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observarSesion(): Flow<Sesion?> {
        return dbManager.queries.obtenerSesion()
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { row ->
                if (row == null) null
                else {
                    val privs = row.privilegios.split(",").filter { it.isNotBlank() }
                    Sesion(
                        usuarioId = row.usuario_id,
                        username = row.username,
                        nombre = row.nombre,
                        rol = row.rol,
                        privilegios = privs,
                        token = row.token,
                        expiraEn = row.expira_en
                    )
                }
            }
    }

    suspend fun obtenerSesion(): Sesion? = withContext(Dispatchers.Default) {
        val row = dbManager.queries.obtenerSesion().executeAsOneOrNull() ?: return@withContext null
        val privs = row.privilegios.split(",").filter { it.isNotBlank() }
        Sesion(
            usuarioId = row.usuario_id,
            username = row.username,
            nombre = row.nombre,
            rol = row.rol,
            privilegios = privs,
            token = row.token,
            expiraEn = row.expira_en
        )
    }

    suspend fun login(usuario: String, contrasena: String): Result<Sesion> = withContext(Dispatchers.Default) {
        try {
            val resp = apiClient.login(LoginRequestDto(usuario, contrasena))
            val privsString = resp.privilegios.joinToString(",")
            val usuarioId = try {
                // Obtenemos info del usuario autenticado
                apiClient.tokenProvider = { resp.token }
                val me = apiClient.getUsuarioActual()
                me.id
            } catch (e: Exception) {
                1L
            }

            dbManager.queries.guardarSesion(
                usuario_id = usuarioId,
                username = resp.usuario,
                nombre = resp.nombre,
                rol = resp.rol,
                privilegios = privsString,
                token = resp.token,
                expira_en = resp.expiraEn
            )

            val sesion = Sesion(
                usuarioId = usuarioId,
                username = resp.usuario,
                nombre = resp.nombre,
                rol = resp.rol,
                privilegios = resp.privilegios,
                token = resp.token,
                expiraEn = resp.expiraEn
            )
            Result.success(sesion)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun cerrarSesion(): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            dbManager.queries.borrarSesion()
            apiClient.tokenProvider = { null }
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun contarPendientesUsuarioActual(): Long = withContext(Dispatchers.Default) {
        val sesion = obtenerSesion() ?: return@withContext 0L
        dbManager.queries.contarPesajesPendientes(sesion.usuarioId).executeAsOne()
    }
}

class RutaRepository(
    private val dbManager: DatabaseManager,
    private val apiClient: EcoApiClient
) {
    fun observarRutaActual(): Flow<Ruta?> {
        return dbManager.queries.obtenerRutaActual()
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { row ->
                if (row == null) null
                else Ruta(
                    id = row.id,
                    fecha = row.fecha,
                    estado = if (row.estado == "CERRADA") EstadoRuta.CERRADA else EstadoRuta.ABIERTA,
                    version = row.version
                )
            }
    }

    fun observarPuntosDeRuta(rutaId: Long, query: String = ""): Flow<List<PuntoAcopio>> {
        return dbManager.queries.listarPuntosDeRuta(rutaId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map { row ->
                    PuntoAcopio(
                        id = row.punto_id,
                        razonSocial = row.razon_social,
                        direccion = row.direccion,
                        zona = row.zona,
                        materialHabitual = row.material_habitual,
                        activo = true,
                        atendido = row.atendido == 1L,
                        motivoOmision = row.motivo_omision
                    )
                }.filter { punto ->
                    if (query.isBlank()) true
                    else {
                        punto.razonSocial.contains(query, ignoreCase = true) ||
                        punto.direccion.contains(query, ignoreCase = true) ||
                        punto.zona.contains(query, ignoreCase = true)
                    }
                }
            }
    }

    suspend fun cerrarRuta(rutaId: Long): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            apiClient.cerrarRuta(rutaId)
            dbManager.queries.actualizarEstadoRuta("CERRADA", rutaId)
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun registrarOmision(rutaId: Long, puntoId: Long, motivo: String): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            apiClient.registrarOmision(rutaId, puntoId, OmisionRequestDto(motivo))
            dbManager.queries.actualizarOmisionRutaPunto(motivo, rutaId, puntoId)
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}

class PesajeRepository(
    private val dbManager: DatabaseManager
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observarPesajesDePunto(puntoId: Long): Flow<List<Pesaje>> {
        return dbManager.queries.listarPesajesDePunto(puntoId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map { row ->
                    Pesaje(
                        id = row.id,
                        rutaPuntoId = row.ruta_punto_id,
                        puntoId = row.punto_id,
                        fechaHora = row.fecha_hora,
                        material = row.material,
                        pesoBruto = row.peso_bruto,
                        tara = row.tara,
                        pesoNeto = row.peso_neto,
                        observacion = row.observacion,
                        operarioId = row.operario_id,
                        operarioNombre = row.operario_nombre,
                        estado = if (row.estado == "ANULADO") EstadoPesaje.ANULADO else EstadoPesaje.REGISTRADO,
                        motivoAnulacion = row.motivo_anulacion,
                        pendiente = row.pendiente == 1L,
                        version = row.version_servidor,
                        operacionId = row.operacion_uuid
                    )
                }
            }
    }

    fun observarContadorPendientes(usuarioId: Long): Flow<Long> {
        return dbManager.queries.contarPesajesPendientes(usuarioId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it ?: 0L }
    }

    suspend fun registrarPesajeOffline(
        rutaId: Long,
        rutaPuntoId: Long,
        puntoId: Long,
        material: String,
        bruto: Double,
        tara: Double,
        observacion: String?,
        usuarioId: Long,
        usuarioNombre: String
    ): Result<Pesaje> = withContext(Dispatchers.Default) {
        try {
            if (bruto <= 0) return@withContext Result.failure(IllegalArgumentException("El peso bruto debe ser mayor a 0"))
            if (tara < 0) return@withContext Result.failure(IllegalArgumentException("La tara no puede ser negativa"))
            if (bruto <= tara) return@withContext Result.failure(IllegalArgumentException("El peso bruto debe ser mayor que la tara"))

            val neto = bruto - tara
            val provisionalId = dbManager.queries.siguienteIdProvisional().executeAsOne()
            val uuid = "uuid-${Random.nextLong(100000, 999999)}-${Random.nextLong(100000, 999999)}"
            val fechaHora = "2026-10-28T15:00:00Z"

            dbManager.database.transaction {
                dbManager.queries.insertarPesajeLocal(
                    id = provisionalId,
                    ruta_punto_id = rutaPuntoId,
                    punto_id = puntoId,
                    fecha_hora = fechaHora,
                    material = material.uppercase(),
                    peso_bruto = bruto,
                    tara = tara,
                    peso_neto = neto,
                    observacion = observacion,
                    operario_id = usuarioId,
                    operario_nombre = usuarioNombre,
                    estado = "REGISTRADO",
                    motivo_anulacion = null,
                    pendiente = 1L,
                    propietario_sesion_id = usuarioId,
                    version_servidor = 0L,
                    operacion_uuid = uuid
                )

                val payload = json.encodeToString(
                    CrearPesajeRequestDto(
                        material = material.uppercase(),
                        pesoBruto = bruto,
                        tara = tara,
                        observacion = observacion,
                        operacionId = uuid
                    )
                )

                dbManager.queries.insertarOperacionPendiente(
                    operacion_uuid = uuid,
                    usuario_propietario_id = usuarioId,
                    tipo = "CREATE",
                    recurso_local_id = provisionalId,
                    payload = payload,
                    creada_en = fechaHora
                )
            }

            val creado = Pesaje(
                id = provisionalId,
                rutaPuntoId = rutaPuntoId,
                puntoId = puntoId,
                fechaHora = fechaHora,
                material = material.uppercase(),
                pesoBruto = bruto,
                tara = tara,
                pesoNeto = neto,
                observacion = observacion,
                operarioId = usuarioId,
                operarioNombre = usuarioNombre,
                estado = EstadoPesaje.REGISTRADO,
                pendiente = true,
                operacionId = uuid
            )
            Result.success(creado)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun corregirPesajeOffline(
        pesajeId: Long,
        material: String,
        bruto: Double,
        tara: Double,
        observacion: String?,
        usuarioId: Long
    ): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            if (bruto <= tara) return@withContext Result.failure(IllegalArgumentException("El peso bruto debe ser mayor que la tara"))
            val neto = bruto - tara

            dbManager.database.transaction {
                dbManager.queries.actualizarPesajeLocal(
                    material = material.uppercase(),
                    peso_bruto = bruto,
                    tara = tara,
                    peso_neto = neto,
                    observacion = observacion,
                    pendiente = 1L,
                    id = pesajeId
                )

                val uuid = "update-$pesajeId-${Random.nextLong(1000, 9999)}"
                val payload = json.encodeToString(
                    CorregirPesajeRequestDto(
                        material = material.uppercase(),
                        pesoBruto = bruto,
                        tara = tara,
                        observacion = observacion
                    )
                )
                dbManager.queries.insertarOperacionPendiente(
                    operacion_uuid = uuid,
                    usuario_propietario_id = usuarioId,
                    tipo = "UPDATE",
                    recurso_local_id = pesajeId,
                    payload = payload,
                    creada_en = "2026-10-28T15:00:00Z"
                )
            }
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun anularPesajeOffline(
        pesajeId: Long,
        motivo: String,
        usuarioId: Long
    ): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            dbManager.database.transaction {
                dbManager.queries.anularPesajeLocal(
                    motivo_anulacion = motivo,
                    pendiente = 1L,
                    id = pesajeId
                )

                val uuid = "cancel-$pesajeId-${Random.nextLong(1000, 9999)}"
                val payload = json.encodeToString(AnularPesajeRequestDto(motivo = motivo))
                dbManager.queries.insertarOperacionPendiente(
                    operacion_uuid = uuid,
                    usuario_propietario_id = usuarioId,
                    tipo = "CANCEL",
                    recurso_local_id = pesajeId,
                    payload = payload,
                    creada_en = "2026-10-28T15:00:00Z"
                )
            }
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}

class SincronizacionRepository(
    private val dbManager: DatabaseManager,
    private val apiClient: EcoApiClient,
    private val sesionRepository: SesionRepository
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _sincronizando = MutableStateFlow(false)
    val sincronizando = _sincronizando.asStateFlow()

    suspend fun sincronizar(): Result<ResultadoSincronizacion> = withContext(Dispatchers.Default) {
        if (_sincronizando.value) {
            return@withContext Result.failure(IllegalStateException("Ya hay una sincronización en curso"))
        }
        _sincronizando.value = true

        try {
            val sesion = sesionRepository.obtenerSesion()
                ?: return@withContext Result.failure(IllegalStateException("No hay sesión activa"))

            apiClient.tokenProvider = { sesion.token }
            val usuarioId = sesion.usuarioId

            var enviadosCount = 0
            var conflictosCount = 0

            // 1. SUBIR PENDIENTES PRIMERO (Requisito estricto offline-first)
            val operacionesPendientes = dbManager.queries.listarOperacionesPendientesPorUsuario(usuarioId).executeAsList()

            for (op in operacionesPendientes) {
                try {
                    when (op.tipo) {
                        "CREATE" -> {
                            val req = json.decodeFromString<CrearPesajeRequestDto>(op.payload)
                            val pesajeLocal = dbManager.queries.listarPesajesDePunto(0).executeAsList().find { it.id == op.recurso_local_id }
                                ?: dbManager.queries.listarPesajesPendientes(usuarioId).executeAsList().find { it.id == op.recurso_local_id }

                            val rutaId = 1L
                            val puntoId = pesajeLocal?.punto_id ?: 1L

                            val serverDto = apiClient.crearPesaje(rutaId, puntoId, req)

                            // Sustitucion atomica del ID provisional
                            dbManager.database.transaction {
                                dbManager.queries.sustituirIdProvisional(
                                    nuevoId = serverDto.id,
                                    versionServidor = serverDto.version,
                                    idProvisional = op.recurso_local_id
                                )
                                dbManager.queries.eliminarOperacionPendiente(op.operacion_uuid)
                            }
                            enviadosCount++
                        }
                        "UPDATE" -> {
                            val req = json.decodeFromString<CorregirPesajeRequestDto>(op.payload)
                            val serverDto = apiClient.corregirPesaje(op.recurso_local_id, req)
                            dbManager.database.transaction {
                                dbManager.queries.actualizarPesajeLocal(
                                    material = serverDto.material,
                                    peso_bruto = serverDto.pesoBruto,
                                    tara = serverDto.tara,
                                    peso_neto = serverDto.pesoNeto,
                                    observacion = serverDto.observacion,
                                    pendiente = 0L,
                                    id = serverDto.id
                                )
                                dbManager.queries.eliminarOperacionPendiente(op.operacion_uuid)
                            }
                            enviadosCount++
                        }
                        "CANCEL" -> {
                            val req = json.decodeFromString<AnularPesajeRequestDto>(op.payload)
                            val serverDto = apiClient.anularPesaje(op.recurso_local_id, req)
                            dbManager.database.transaction {
                                dbManager.queries.anularPesajeLocal(
                                    motivo_anulacion = serverDto.motivoAnulacion,
                                    pendiente = 0L,
                                    id = serverDto.id
                                )
                                dbManager.queries.eliminarOperacionPendiente(op.operacion_uuid)
                            }
                            enviadosCount++
                        }
                    }
                } catch (e: ErrorApi.Conflicto) {
                    conflictosCount++
                } catch (e: Exception) {
                    // Si ocurre error de red durante envio, se conserva en cola
                }
            }

            // 2. DESCARGAR DATOS ACTUALES DEL SERVIDOR
            var descargadosCount = 0
            val rutaDto = apiClient.getRutaMia()
            dbManager.queries.guardarRuta(
                id = rutaDto.id,
                fecha = rutaDto.fecha,
                estado = rutaDto.estado,
                version = rutaDto.version
            )

            val puntosPagina = apiClient.getPuntosDeRuta(rutaDto.id, pagina = 0, tamanio = 100)
            for (rp in puntosPagina.contenido) {
                dbManager.queries.guardarPunto(
                    id = rp.punto.id,
                    razon_social = rp.punto.razonSocial,
                    direccion = rp.punto.direccion,
                    zona = rp.punto.zona,
                    material_habitual = rp.punto.materialHabitual
                )
                dbManager.queries.guardarRutaPunto(
                    id = rp.id,
                    ruta_id = rp.rutaId,
                    punto_id = rp.punto.id,
                    secuencia = rp.secuencia.toLong(),
                    motivo_omision = rp.motivoOmision
                )

                // Descargar pesajes del punto
                val pesajesPagina = apiClient.listarPesajesDePunto(rp.punto.id, pagina = 0, tamanio = 100)
                for (p in pesajesPagina.contenido) {
                    // Solo actualizamos si no hay cambio pendiente local
                    val pesajeLocal = dbManager.queries.listarPesajesDePunto(rp.punto.id).executeAsList().find { it.id == p.id }
                    if (pesajeLocal == null || pesajeLocal.pendiente == 0L) {
                        dbManager.queries.insertarPesajeLocal(
                            id = p.id,
                            ruta_punto_id = p.rutaPuntoId,
                            punto_id = p.puntoId,
                            fecha_hora = p.fechaHora,
                            material = p.material,
                            peso_bruto = p.pesoBruto,
                            tara = p.tara,
                            peso_neto = p.pesoNeto,
                            observacion = p.observacion,
                            operario_id = p.operarioId,
                            operario_nombre = p.operarioNombre,
                            estado = p.estado,
                            motivo_anulacion = p.motivoAnulacion,
                            pendiente = 0L,
                            propietario_sesion_id = usuarioId,
                            version_servidor = p.version,
                            operacion_uuid = p.operacionId
                        )
                        descargadosCount++
                    }
                }
            }

            val pendientesRestantes = dbManager.queries.contarPesajesPendientes(usuarioId).executeAsOne()

            val resultado = ResultadoSincronizacion(
                enviados = enviadosCount,
                descargados = descargadosCount,
                conflictos = conflictosCount,
                pendientesRestantes = pendientesRestantes.toInt(),
                mensaje = "Sincronización completada: $enviadosCount enviados, $descargadosCount recibidos."
            )
            Result.success(resultado)
        } catch (e: Throwable) {
            Result.failure(e)
        } finally {
            _sincronizando.value = false
        }
    }
}

