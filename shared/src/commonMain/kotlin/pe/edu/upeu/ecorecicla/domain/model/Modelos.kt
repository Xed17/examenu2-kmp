package pe.edu.upeu.ecorecicla.domain.model

enum class EstadoPesaje {
    REGISTRADO,
    ANULADO
}

enum class EstadoRuta {
    ABIERTA,
    CERRADA
}

data class PuntoAcopio(
    val id: Long,
    val razonSocial: String,
    val direccion: String,
    val zona: String,
    val materialHabitual: String,
    val activo: Boolean = true,
    val atendido: Boolean = false,
    val motivoOmision: String? = null
)

data class Pesaje(
    val id: Long, // Puede ser negativo si es provisional offline
    val rutaPuntoId: Long,
    val puntoId: Long,
    val fechaHora: String,
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val pesoNeto: Double,
    val observacion: String? = null,
    val operarioId: Long,
    val operarioNombre: String,
    val estado: EstadoPesaje = EstadoPesaje.REGISTRADO,
    val motivoAnulacion: String? = null,
    val pendiente: Boolean = false,
    val version: Long = 0,
    val operacionId: String? = null,
    val humedadEstimada: Double? = null
)

data class Sesion(
    val usuarioId: Long,
    val username: String,
    val nombre: String,
    val rol: String,
    val privilegios: List<String>,
    val token: String,
    val expiraEn: String
)

data class Ruta(
    val id: Long,
    val fecha: String,
    val estado: EstadoRuta,
    val version: Long = 0
)

data class RutaPunto(
    val id: Long,
    val rutaId: Long,
    val punto: PuntoAcopio,
    val secuencia: Int,
    val atendido: Boolean,
    val motivoOmision: String?
)

data class ResultadoSincronizacion(
    val enviados: Int = 0,
    val descargados: Int = 0,
    val conflictos: Int = 0,
    val pendientesRestantes: Int = 0,
    val mensaje: String = ""
)

sealed class ErrorApi : Exception() {
    data class Validacion(val campos: Map<String, String>, override val message: String = "Datos inválidos") : ErrorApi()
    data class NoAutorizado(override val message: String = "No autorizado o token expirado") : ErrorApi()
    data class SinPrivilegio(override val message: String = "Privilegios insuficientes") : ErrorApi()
    data class NoEncontrado(override val message: String = "Recurso no encontrado") : ErrorApi()
    data class Conflicto(val codigo: String?, override val message: String) : ErrorApi()
    data class Servidor(override val message: String = "Error interno del servidor") : ErrorApi()
    data class SinConexion(override val message: String = "Sin conexión a internet") : ErrorApi()
    data class TiempoAgotado(override val message: String = "Tiempo de espera agotado") : ErrorApi()
    data class RespuestaInvalida(override val message: String = "Respuesta no reconocible del servidor") : ErrorApi()
}

