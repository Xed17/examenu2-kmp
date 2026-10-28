package pe.edu.upeu.ecorecicla.data.remote.dto

import kotlinx.serialization.Serializable
import pe.edu.upeu.ecorecicla.domain.model.EstadoPesaje
import pe.edu.upeu.ecorecicla.domain.model.EstadoRuta
import pe.edu.upeu.ecorecicla.domain.model.Pesaje
import pe.edu.upeu.ecorecicla.domain.model.PuntoAcopio
import pe.edu.upeu.ecorecicla.domain.model.Ruta
import pe.edu.upeu.ecorecicla.domain.model.RutaPunto

@Serializable
data class LoginRequestDto(
    val usuario: String,
    val contrasena: String
)

@Serializable
data class LoginResponseDto(
    val token: String,
    val usuario: String,
    val nombre: String,
    val rol: String,
    val privilegios: List<String>,
    val expiraEn: String
)

@Serializable
data class UsuarioActualDto(
    val id: Long,
    val username: String,
    val nombre: String,
    val rol: String,
    val privilegios: List<String>
)

@Serializable
data class PuntoAcopioDto(
    val id: Long,
    val razonSocial: String,
    val direccion: String,
    val zona: String,
    val materialHabitual: String,
    val activo: Boolean,
    val atendido: Boolean = false,
    val motivoOmision: String? = null
) {
    fun toDomain(): PuntoAcopio = PuntoAcopio(
        id = id,
        razonSocial = razonSocial,
        direccion = direccion,
        zona = zona,
        materialHabitual = materialHabitual,
        activo = activo,
        atendido = atendido,
        motivoOmision = motivoOmision
    )
}

@Serializable
data class RutaDto(
    val id: Long,
    val fecha: String,
    val estado: String,
    val version: Long
) {
    fun toDomain(): Ruta = Ruta(
        id = id,
        fecha = fecha,
        estado = if (estado == "CERRADA") EstadoRuta.CERRADA else EstadoRuta.ABIERTA,
        version = version
    )
}

@Serializable
data class RutaPuntoDto(
    val id: Long,
    val rutaId: Long,
    val punto: PuntoAcopioDto,
    val secuencia: Int,
    val atendido: Boolean,
    val motivoOmision: String? = null
) {
    fun toDomain(): RutaPunto = RutaPunto(
        id = id,
        rutaId = rutaId,
        punto = punto.toDomain(),
        secuencia = secuencia,
        atendido = atendido,
        motivoOmision = motivoOmision
    )
}

@Serializable
data class PesajeDto(
    val id: Long,
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
    val estado: String,
    val motivoAnulacion: String? = null,
    val version: Long,
    val operacionId: String? = null
) {
    fun toDomain(): Pesaje = Pesaje(
        id = id,
        rutaPuntoId = rutaPuntoId,
        puntoId = puntoId,
        fechaHora = fechaHora,
        material = material,
        pesoBruto = pesoBruto,
        tara = tara,
        pesoNeto = pesoNeto,
        observacion = observacion,
        operarioId = operarioId,
        operarioNombre = operarioNombre,
        estado = if (estado == "ANULADO") EstadoPesaje.ANULADO else EstadoPesaje.REGISTRADO,
        motivoAnulacion = motivoAnulacion,
        pendiente = false,
        version = version,
        operacionId = operacionId
    )
}

@Serializable
data class CrearPesajeRequestDto(
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val observacion: String? = null,
    val operacionId: String? = null
)

@Serializable
data class CorregirPesajeRequestDto(
    val material: String,
    val pesoBruto: Double,
    val tara: Double,
    val observacion: String? = null,
    val version: Long? = null
)

@Serializable
data class AnularPesajeRequestDto(
    val motivo: String
)

@Serializable
data class OmisionRequestDto(
    val motivo: String
)

@Serializable
data class PaginaDto<T>(
    val contenido: List<T>,
    val pagina: Int,
    val tamanio: Int,
    val totalElementos: Long,
    val totalPaginas: Int,
    val ultima: Boolean
)

@Serializable
data class ErrorResponseDto(
    val timestamp: String? = null,
    val status: Int? = null,
    val error: String? = null,
    val message: String? = null,
    val path: String? = null,
    val validationErrors: Map<String, String>? = null,
    val codigo: String? = null
)

