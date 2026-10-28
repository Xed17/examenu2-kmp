package pe.edu.upeu.ecorecicla.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import pe.edu.upeu.ecorecicla.data.remote.dto.*
import pe.edu.upeu.ecorecicla.domain.model.ErrorApi

class EcoApiClient(
    val httpClient: HttpClient,
    var baseUrl: String = "http://10.0.2.2:8080",
    var tokenProvider: () -> String? = { null }
) {
    companion object {
        fun crearHttpClient(engine: io.ktor.client.engine.HttpClientEngine? = null): HttpClient {
            val jsonConfig = Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = false
                encodeDefaults = true
            }

            return if (engine != null) {
                HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(jsonConfig)
                    }
                    install(HttpTimeout) {
                        requestTimeoutMillis = 15000
                        connectTimeoutMillis = 10000
                        socketTimeoutMillis = 15000
                    }
                }
            } else {
                HttpClient {
                    install(ContentNegotiation) {
                        json(jsonConfig)
                    }
                    install(HttpTimeout) {
                        requestTimeoutMillis = 15000
                        connectTimeoutMillis = 10000
                        socketTimeoutMillis = 15000
                    }
                }
            }
        }
    }

    private suspend fun <T> ejecutarPeticion(block: suspend () -> HttpResponse, deserializar: suspend (HttpResponse) -> T): T {
        try {
            val response = block()
            if (response.status.isSuccess()) {
                if (response.status == HttpStatusCode.NoContent) {
                    @Suppress("UNCHECKED_CAST")
                    return Unit as T
                }
                return deserializar(response)
            } else {
                val errorBody = try {
                    response.body<ErrorResponseDto>()
                } catch (e: Exception) {
                    null
                }
                val mensaje = errorBody?.message ?: "Error del servidor (${response.status.value})"
                val codigo = errorBody?.codigo

                when (response.status) {
                    HttpStatusCode.BadRequest -> {
                        throw ErrorApi.Validacion(errorBody?.validationErrors ?: emptyMap(), mensaje)
                    }
                    HttpStatusCode.Unauthorized -> {
                        throw ErrorApi.NoAutorizado(mensaje)
                    }
                    HttpStatusCode.Forbidden -> {
                        throw ErrorApi.SinPrivilegio(mensaje)
                    }
                    HttpStatusCode.NotFound -> {
                        throw ErrorApi.NoEncontrado(mensaje)
                    }
                    HttpStatusCode.Conflict -> {
                        throw ErrorApi.Conflicto(codigo, mensaje)
                    }
                    HttpStatusCode.InternalServerError -> {
                        throw ErrorApi.Servidor(mensaje)
                    }
                    else -> {
                        throw ErrorApi.RespuestaInvalida("HTTP ${response.status.value}: $mensaje")
                    }
                }
            }
        } catch (e: ErrorApi) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            throw ErrorApi.TiempoAgotado()
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("timeout", ignoreCase = true)) {
                throw ErrorApi.TiempoAgotado()
            }
            throw ErrorApi.SinConexion(e.message ?: "Sin conexión")
        }
    }

    private fun HttpRequestBuilder.adjuntarHeaders(incluirAuth: Boolean = true) {
        contentType(ContentType.Application.Json)
        if (incluirAuth) {
            tokenProvider()?.let { token ->
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }

    suspend fun login(req: LoginRequestDto): LoginResponseDto {
        return ejecutarPeticion(
            block = {
                httpClient.post("$baseUrl/api/v1/auth/login") {
                    adjuntarHeaders(incluirAuth = false)
                    setBody(req)
                }
            },
            deserializar = { it.body() }
        )
    }

    suspend fun getUsuarioActual(): UsuarioActualDto {
        return ejecutarPeticion(
            block = {
                httpClient.get("$baseUrl/api/v1/usuarios/me") {
                    adjuntarHeaders()
                }
            },
            deserializar = { it.body() }
        )
    }

    suspend fun getRutaMia(fecha: String? = null): RutaDto {
        return ejecutarPeticion(
            block = {
                httpClient.get("$baseUrl/api/v1/rutas/mia") {
                    adjuntarHeaders()
                    fecha?.let { parameter("fecha", it) }
                }
            },
            deserializar = { it.body() }
        )
    }

    suspend fun getPuntosDeRuta(rutaId: Long, pagina: Int = 0, tamanio: Int = 50): PaginaDto<RutaPuntoDto> {
        return ejecutarPeticion(
            block = {
                httpClient.get("$baseUrl/api/v1/rutas/$rutaId/puntos") {
                    adjuntarHeaders()
                    parameter("pagina", pagina)
                    parameter("tamanio", tamanio)
                }
            },
            deserializar = { it.body() }
        )
    }

    suspend fun crearPesaje(rutaId: Long, puntoId: Long, req: CrearPesajeRequestDto): PesajeDto {
        return ejecutarPeticion(
            block = {
                httpClient.post("$baseUrl/api/v1/rutas/$rutaId/puntos/$puntoId/pesajes") {
                    adjuntarHeaders()
                    setBody(req)
                }
            },
            deserializar = { it.body() }
        )
    }

    suspend fun corregirPesaje(pesajeId: Long, req: CorregirPesajeRequestDto): PesajeDto {
        return ejecutarPeticion(
            block = {
                httpClient.put("$baseUrl/api/v1/pesajes/$pesajeId") {
                    adjuntarHeaders()
                    setBody(req)
                }
            },
            deserializar = { it.body() }
        )
    }

    suspend fun anularPesaje(pesajeId: Long, req: AnularPesajeRequestDto): PesajeDto {
        return ejecutarPeticion(
            block = {
                httpClient.post("$baseUrl/api/v1/pesajes/$pesajeId/anulacion") {
                    adjuntarHeaders()
                    setBody(req)
                }
            },
            deserializar = { it.body() }
        )
    }

    suspend fun cerrarRuta(rutaId: Long): RutaDto {
        return ejecutarPeticion(
            block = {
                httpClient.post("$baseUrl/api/v1/rutas/$rutaId/cierre") {
                    adjuntarHeaders()
                }
            },
            deserializar = { it.body() }
        )
    }

    suspend fun registrarOmision(rutaId: Long, puntoId: Long, req: OmisionRequestDto): RutaPuntoDto {
        return ejecutarPeticion(
            block = {
                httpClient.put("$baseUrl/api/v1/rutas/$rutaId/puntos/$puntoId/omision") {
                    adjuntarHeaders()
                    setBody(req)
                }
            },
            deserializar = { it.body() }
        )
    }

    suspend fun eliminarOmision(rutaId: Long, puntoId: Long) {
        ejecutarPeticion(
            block = {
                httpClient.delete("$baseUrl/api/v1/rutas/$rutaId/puntos/$puntoId/omision") {
                    adjuntarHeaders()
                }
            },
            deserializar = { }
        )
    }

    suspend fun listarPesajesDePunto(puntoId: Long, pagina: Int = 0, tamanio: Int = 50): PaginaDto<PesajeDto> {
        return ejecutarPeticion(
            block = {
                httpClient.get("$baseUrl/api/v1/puntos/$puntoId/pesajes") {
                    adjuntarHeaders()
                    parameter("pagina", pagina)
                    parameter("tamanio", tamanio)
                }
            },
            deserializar = { it.body() }
        )
    }
}

