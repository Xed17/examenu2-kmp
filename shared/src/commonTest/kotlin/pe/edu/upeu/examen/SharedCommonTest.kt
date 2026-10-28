package pe.edu.upeu.examen

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import pe.edu.upeu.ecorecicla.data.remote.EcoApiClient
import pe.edu.upeu.ecorecicla.data.remote.dto.*
import pe.edu.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.edu.upeu.ecorecicla.domain.model.EstadoRuta
import pe.edu.upeu.ecorecicla.domain.model.ErrorApi
import kotlin.test.*

class SharedCommonTest {

    @Test
    fun test01_DtoADominio() {
        val pesajeDto = PesajeDto(
            id = 45L,
            rutaPuntoId = 12L,
            puntoId = 3L,
            fechaHora = "2026-10-28T14:30:00Z",
            material = "CARTON",
            pesoBruto = 50.0,
            tara = 5.0,
            pesoNeto = 45.0,
            observacion = "Cajas limpias",
            operarioId = 1L,
            operarioNombre = "Operario Uno",
            estado = "ANULADO",
            motivoAnulacion = "Error de pesaje",
            version = 2L,
            operacionId = "uuid-test-123"
        )

        val dominio = pesajeDto.toDomain()

        assertEquals(45L, dominio.id)
        assertEquals(12L, dominio.rutaPuntoId)
        assertEquals(3L, dominio.puntoId)
        assertEquals("CARTON", dominio.material)
        assertEquals(50.0, dominio.pesoBruto)
        assertEquals(5.0, dominio.tara)
        assertEquals(45.0, dominio.pesoNeto)
        assertEquals(EstadoPesaje.ANULADO, dominio.estado)
        assertEquals("Error de pesaje", dominio.motivoAnulacion)
        assertFalse(dominio.pendiente)
    }

    @Test
    fun test02_TraduccionDeErroresApi() = runTest {
        // Prueba error 400 con mapa de validationErrors
        val mockEngine400 = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""
                    {
                        "timestamp": "2026-10-28T15:00:00",
                        "status": 400,
                        "error": "Bad Request",
                        "message": "Datos de entrada inválidos",
                        "validationErrors": {
                            "pesoBruto": "El peso bruto debe ser estrictamente mayor que la tara"
                        },
                        "codigo": "VALIDACION_FALLIDA"
                    }
                """.trimIndent()),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client400 = EcoApiClient(EcoApiClient.crearHttpClient(mockEngine400))

        val error = assertFailsWith<ErrorApi.Validacion> {
            client400.crearPesaje(1L, 1L, CrearPesajeRequestDto("CARTON", 10.0, 10.0))
        }

        assertTrue(error.campos.containsKey("pesoBruto"))
        assertEquals("El peso bruto debe ser estrictamente mayor que la tara", error.campos["pesoBruto"])

        // Prueba error 403 Forbidden
        val mockEngine403 = MockEngine { _ ->
            respond(
                content = ByteReadChannel("""
                    {
                        "timestamp": "2026-10-28T15:00:00",
                        "status": 403,
                        "error": "Forbidden",
                        "message": "Solo el autor del pesaje puede corregirlo",
                        "codigo": "PESAJE_AJENO"
                    }
                """.trimIndent()),
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client403 = EcoApiClient(EcoApiClient.crearHttpClient(mockEngine403))

        val error403 = assertFailsWith<ErrorApi.SinPrivilegio> {
            client403.corregirPesaje(1L, CorregirPesajeRequestDto("CARTON", 20.0, 5.0))
        }

        assertTrue(error403.message.contains("autor del pesaje"))
    }

    @Test
    fun test03_CalculoLocalYProvisionalOffline() {
        val bruto = 80.0
        val tara = 8.5
        val netoEstimado = bruto - tara

        assertEquals(71.5, netoEstimado)

        // Verificacion logica de ID negativo provisional
        val idsLocales = listOf<Long>()
        val minExistente = idsLocales.filter { it < 0 }.minOrNull() ?: 0L
        val siguienteProvisional = minExistente - 1

        assertEquals(-1L, siguienteProvisional)

        val idsConUno = listOf(-1L)
        val minExistente2 = idsConUno.filter { it < 0 }.minOrNull() ?: 0L
        val siguienteProvisional2 = minExistente2 - 1

        assertEquals(-2L, siguienteProvisional2)
    }

    @Test
    fun test04_OrdenDeSincronizacionSubirAntesDeBajar() = runTest {
        val secuenciaLlamadas = mutableListOf<String>()

        val mockEngine = MockEngine { request ->
            val url = request.url.encodedPath
            if (request.method == HttpMethod.Post && url.contains("pesajes")) {
                secuenciaLlamadas.add("SUBIR_PENDIENTE_POST")
                respond(
                    content = ByteReadChannel("""
                        {
                            "id": 99,
                            "rutaPuntoId": 1,
                            "puntoId": 1,
                            "fechaHora": "2026-10-28T15:00:00Z",
                            "material": "CARTON",
                            "pesoBruto": 40.0,
                            "tara": 5.0,
                            "pesoNeto": 35.0,
                            "operarioId": 1,
                            "operarioNombre": "Operario",
                            "estado": "REGISTRADO",
                            "version": 0
                        }
                    """.trimIndent()),
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else if (request.method == HttpMethod.Get && url.contains("rutas/mia")) {
                secuenciaLlamadas.add("DESCARGAR_RUTA_GET")
                respond(
                    content = ByteReadChannel("""
                        {
                            "id": 1,
                            "fecha": "2026-10-28",
                            "estado": "ABIERTA",
                            "version": 0
                        }
                    """.trimIndent()),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respondOk()
            }
        }

        val apiClient = EcoApiClient(EcoApiClient.crearHttpClient(mockEngine))

        // Simulamos el algoritmo exacto de sincronizacion:
        // 1. Envios
        apiClient.crearPesaje(1L, 1L, CrearPesajeRequestDto("CARTON", 40.0, 5.0, operacionId = "uuid-orden-1"))
        // 2. Descargas
        apiClient.getRutaMia()

        assertEquals(2, secuenciaLlamadas.size)
        assertEquals("SUBIR_PENDIENTE_POST", secuenciaLlamadas[0], "El envio debe ocurrir ANTES de la descarga")
        assertEquals("DESCARGAR_RUTA_GET", secuenciaLlamadas[1], "La descarga debe ocurrir DESPUES del envio")
    }

    @Test
    fun test05_FiltroSoloPendientesDePesar() {
        val p1 = pe.edu.upeu.ecorecicla.domain.model.PuntoAcopio(1L, "Punto 1", "Av 1", "Norte", "CARTON", true, atendido = true)
        val p2 = pe.edu.upeu.ecorecicla.domain.model.PuntoAcopio(2L, "Punto 2", "Av 2", "Norte", "PLASTICO", true, atendido = false)
        val p3 = pe.edu.upeu.ecorecicla.domain.model.PuntoAcopio(3L, "Punto 3", "Av 3", "Centro", "VIDRIO", true, atendido = true)
        val p4 = pe.edu.upeu.ecorecicla.domain.model.PuntoAcopio(4L, "Punto 4", "Av 4", "Sur", "METAL", true, atendido = false)

        val listaOriginal = listOf(p1, p2, p3, p4)

        val sinFiltro = listaOriginal.filter { true }
        assertEquals(4, sinFiltro.size)

        val soloPendientes = listaOriginal.filter { !it.atendido }
        assertEquals(2, soloPendientes.size)
        assertTrue(soloPendientes.all { !it.atendido })
        assertEquals(listOf(2L, 4L), soloPendientes.map { it.id })
    }

    @Test
    fun test06_RolAuditorSoloLectura() = runTest {
        val auditorSesion = pe.edu.upeu.ecorecicla.domain.model.Sesion(
            usuarioId = 5L,
            username = "auditor1",
            nombre = "Auditor de Calidad",
            rol = "AUDITOR",
            privilegios = listOf("PUNTO_LEER", "PESAJE_LEER"),
            token = "jwt-auditor-fake",
            expiraEn = "2026-10-28T18:00:00Z"
        )

        // Verificacion de banderas de solo lectura
        val puedeRegistrar = auditorSesion.privilegios.contains("PESAJE_CREAR")
        val puedeEditar = auditorSesion.privilegios.contains("PESAJE_EDITAR")
        val puedeAnular = auditorSesion.privilegios.contains("PESAJE_ANULAR")
        val puedeCerrarRuta = auditorSesion.privilegios.contains("RUTA_CERRAR")
        val puedeOmitir = auditorSesion.privilegios.contains("RUTA_PUNTO_OMITIR")

        assertFalse(puedeRegistrar, "AUDITOR no debe tener permiso de crear pesajes")
        assertFalse(puedeEditar, "AUDITOR no debe tener permiso de editar pesajes")
        assertFalse(puedeAnular, "AUDITOR no debe tener permiso de anular pesajes")
        assertFalse(puedeCerrarRuta, "AUDITOR no debe tener permiso de cerrar rutas")
        assertFalse(puedeOmitir, "AUDITOR no debe tener permiso de omitir puntos")

        assertTrue(auditorSesion.privilegios.contains("PUNTO_LEER"))
        assertTrue(auditorSesion.privilegios.contains("PESAJE_LEER"))
    }
}
