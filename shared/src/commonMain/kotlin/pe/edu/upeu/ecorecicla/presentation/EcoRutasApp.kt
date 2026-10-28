package pe.edu.upeu.ecorecicla.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.edu.upeu.ecorecicla.data.repository.*
import pe.edu.upeu.ecorecicla.domain.model.*

enum class Pantalla {
    LOGIN,
    RUTA,
    DETALLE_PUNTO,
    FORMULARIO_PESAJE,
    PERFIL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcoRutasApp(
    sesionRepo: SesionRepository,
    rutaRepo: RutaRepository,
    pesajeRepo: PesajeRepository,
    syncRepo: SincronizacionRepository
) {
    val coroutineScope = rememberCoroutineScope()
    var pantallaActual by remember { mutableStateOf(Pantalla.LOGIN) }

    val sesion by sesionRepo.observarSesion().collectAsState(initial = null)
    val rutaActual by rutaRepo.observarRutaActual().collectAsState(initial = null)
    val sincronizando by syncRepo.sincronizando.collectAsState()

    var puntoSeleccionado by remember { mutableStateOf<PuntoAcopio?>(null) }
    var pesajeAEditar by remember { mutableStateOf<Pesaje?>(null) }
    var mensajeBanner by remember { mutableStateOf<String?>(null) }

    var textoBusqueda by remember { mutableStateOf("") }
    var soloPendientes by remember { mutableStateOf(false) }
    val puntosDeRuta by rutaRepo.observarPuntosDeRuta(rutaActual?.id ?: 1L, textoBusqueda, soloPendientes).collectAsState(initial = emptyList())
    val pesajesDelPunto by pesajeRepo.observarPesajesDePunto(puntoSeleccionado?.id ?: 0L).collectAsState(initial = emptyList())
    val contadorPendientes by pesajeRepo.observarContadorPendientes(sesion?.usuarioId ?: 0L).collectAsState(initial = 0L)

    LaunchedEffect(sesion) {
        if (sesion != null && pantallaActual == Pantalla.LOGIN) {
            pantallaActual = Pantalla.RUTA
        } else if (sesion == null) {
            pantallaActual = Pantalla.LOGIN
        }
    }

    Scaffold(
        topBar = {
            if (sesion != null) {
                TopAppBar(
                    title = {
                        Column {
                            Text("EcoRutas", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                text = "${sesion?.nombre ?: ""} (${sesion?.rol ?: ""})",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        // Badge de pendientes
                        if (contadorPendientes > 0) {
                            Surface(
                                color = MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "$contadorPendientes pend.",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Boton Sync
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    val res = syncRepo.sincronizar()
                                    mensajeBanner = res.fold(
                                        onSuccess = { it.mensaje },
                                        onFailure = { "Error de sync: ${it.message}" }
                                    )
                                }
                            },
                            enabled = !sincronizando
                        ) {
                            if (sincronizando) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("🔄", fontSize = 18.sp)
                            }
                        }

                        // Boton Perfil
                        IconButton(onClick = { pantallaActual = Pantalla.PERFIL }) {
                            Text("👤", fontSize = 18.sp)
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                mensajeBanner?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(msg, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { mensajeBanner = null }) {
                                Text("OK")
                            }
                        }
                    }
                }

                when (pantallaActual) {
                    Pantalla.LOGIN -> {
                        PantallaLogin(
                            onLogin = { u, c ->
                                coroutineScope.launch {
                                    val res = sesionRepo.login(u, c)
                                    res.onSuccess {
                                        mensajeBanner = "Bienvenido ${it.nombre}"
                                        syncRepo.sincronizar()
                                    }.onFailure {
                                        mensajeBanner = it.message ?: "Credenciales inválidas"
                                    }
                                }
                            }
                        )
                    }

                    Pantalla.RUTA -> {
                        PantallaRuta(
                            ruta = rutaActual,
                            puntos = puntosDeRuta,
                            textoBusqueda = textoBusqueda,
                            onBusquedaCambio = { textoBusqueda = it },
                            soloPendientes = soloPendientes,
                            onSoloPendientesCambio = { soloPendientes = it },
                            onPuntoClick = { punto ->
                                puntoSeleccionado = punto
                                pantallaActual = Pantalla.DETALLE_PUNTO
                            },
                            onCerrarRuta = {
                                coroutineScope.launch {
                                    rutaActual?.let { r ->
                                        val res = rutaRepo.cerrarRuta(r.id)
                                        mensajeBanner = res.fold(
                                            onSuccess = { "Ruta cerrada exitosamente" },
                                            onFailure = { "Error al cerrar ruta: ${it.message}" }
                                        )
                                    }
                                }
                            },
                            puedeCerrarRuta = sesion?.privilegios?.contains("RUTA_CERRAR") == true
                        )
                    }

                    Pantalla.DETALLE_PUNTO -> {
                        puntoSeleccionado?.let { punto ->
                            PantallaDetallePunto(
                                punto = punto,
                                pesajes = pesajesDelPunto,
                                rutaAbierta = rutaActual?.estado != EstadoRuta.CERRADA,
                                puedeRegistrar = sesion?.privilegios?.contains("PESAJE_CREAR") == true,
                                puedeEditar = sesion?.privilegios?.contains("PESAJE_EDITAR") == true || sesion?.privilegios?.contains("PESAJE_EDITAR_CUALQUIERA") == true,
                                puedeAnular = sesion?.privilegios?.contains("PESAJE_ANULAR") == true,
                                puedeOmitir = sesion?.privilegios?.contains("RUTA_PUNTO_OMITIR") == true,
                                usuarioId = sesion?.usuarioId ?: 0L,
                                puedeEditarCualquiera = sesion?.privilegios?.contains("PESAJE_EDITAR_CUALQUIERA") == true,
                                onVolver = { pantallaActual = Pantalla.RUTA },
                                onNuevoPesaje = {
                                    pesajeAEditar = null
                                    pantallaActual = Pantalla.FORMULARIO_PESAJE
                                },
                                onEditarPesaje = { pesaje ->
                                    pesajeAEditar = pesaje
                                    pantallaActual = Pantalla.FORMULARIO_PESAJE
                                },
                                onAnularPesaje = { pesaje, motivo ->
                                    coroutineScope.launch {
                                        val res = pesajeRepo.anularPesajeOffline(pesaje.id, motivo, sesion?.usuarioId ?: 0L)
                                        mensajeBanner = res.fold(
                                            onSuccess = { "Pesaje anulado localmente" },
                                            onFailure = { "Error: ${it.message}" }
                                        )
                                    }
                                },
                                onOmitirPunto = { motivo ->
                                    coroutineScope.launch {
                                        rutaActual?.let { r ->
                                            val res = rutaRepo.registrarOmision(r.id, punto.id, motivo)
                                            mensajeBanner = res.fold(
                                                onSuccess = { "Punto marcado como omitido" },
                                                onFailure = { "Error: ${it.message}" }
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }

                    Pantalla.FORMULARIO_PESAJE -> {
                        puntoSeleccionado?.let { punto ->
                            PantallaFormularioPesaje(
                                pesajeExistente = pesajeAEditar,
                                onVolver = { pantallaActual = Pantalla.DETALLE_PUNTO },
                                onGuardar = { mat, bruto, tara, obs, humedad ->
                                    coroutineScope.launch {
                                        val uId = sesion?.usuarioId ?: 1L
                                        val uNom = sesion?.nombre ?: "Operario"

                                        if (pesajeAEditar == null) {
                                            val res = pesajeRepo.registrarPesajeOffline(
                                                rutaId = rutaActual?.id ?: 1L,
                                                rutaPuntoId = punto.id,
                                                puntoId = punto.id,
                                                material = mat,
                                                bruto = bruto,
                                                tara = tara,
                                                observacion = obs,
                                                usuarioId = uId,
                                                usuarioNombre = uNom,
                                                humedadEstimada = humedad
                                            )
                                            res.onSuccess {
                                                mensajeBanner = "Pesaje registrado localmente (ID provisional ${it.id})"
                                                pantallaActual = Pantalla.DETALLE_PUNTO
                                            }.onFailure {
                                                mensajeBanner = "Error: ${it.message}"
                                            }
                                        } else {
                                            val res = pesajeRepo.corregirPesajeOffline(
                                                pesajeId = pesajeAEditar!!.id,
                                                material = mat,
                                                bruto = bruto,
                                                tara = tara,
                                                observacion = obs,
                                                usuarioId = uId,
                                                humedadEstimada = humedad
                                            )
                                            res.onSuccess {
                                                mensajeBanner = "Pesaje corregido localmente"
                                                pantallaActual = Pantalla.DETALLE_PUNTO
                                            }.onFailure {
                                                mensajeBanner = "Error: ${it.message}"
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }

                    Pantalla.PERFIL -> {
                        sesion?.let { s ->
                            PantallaPerfil(
                                sesion = s,
                                pendientes = contadorPendientes,
                                onVolver = { pantallaActual = Pantalla.RUTA },
                                onCerrarSesion = {
                                    coroutineScope.launch {
                                        sesionRepo.cerrarSesion()
                                        pantallaActual = Pantalla.LOGIN
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PantallaLogin(
    onLogin: (String, String) -> Unit
) {
    var usuario by remember { mutableStateOf("operario1") }
    var contrasena by remember { mutableStateOf("Eco2026*") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🌿 EcoRecicla", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Gestión de Rutas y Pesajes", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = usuario,
            onValueChange = { usuario = it },
            label = { Text("Usuario") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = contrasena,
            onValueChange = { contrasena = it },
            label = { Text("Contraseña") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onLogin(usuario, contrasena) },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Ingresar")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Usuarios de prueba: operario1, supervisor1, admin, operario2, auditor1\nContraseña: Eco2026*",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun PantallaRuta(
    ruta: Ruta?,
    puntos: List<PuntoAcopio>,
    textoBusqueda: String,
    onBusquedaCambio: (String) -> Unit,
    soloPendientes: Boolean,
    onSoloPendientesCambio: (Boolean) -> Unit,
    onPuntoClick: (PuntoAcopio) -> Unit,
    onCerrarRuta: () -> Unit,
    puedeCerrarRuta: Boolean
) {
    var mostrarConfirmacionCierre by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Cabecera de estado de ruta
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (ruta?.estado == EstadoRuta.CERRADA) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Ruta del Día: ${ruta?.fecha ?: "Hoy"}", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Estado: ${ruta?.estado ?: "ABIERTA"}",
                        fontSize = 13.sp,
                        color = if (ruta?.estado == EstadoRuta.CERRADA) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                if (puedeCerrarRuta && ruta?.estado != EstadoRuta.CERRADA) {
                    Button(
                        onClick = { mostrarConfirmacionCierre = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cerrar Ruta", fontSize = 12.sp)
                    }
                }
            }
        }

        // Buscador reactivo
        OutlinedTextField(
            value = textoBusqueda,
            onValueChange = onBusquedaCambio,
            placeholder = { Text("Buscar punto por nombre, dirección o zona...") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            singleLine = true
        )

        // Filtro SC-A: Solo pendientes de pesar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Solo pendientes de pesar", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Switch(
                checked = soloPendientes,
                onCheckedChange = onSoloPendientesCambio
            )
        }

        // Lista de puntos
        if (puntos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay puntos asignados o coincidentes con la búsqueda")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(puntos) { punto ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onPuntoClick(punto) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(punto.razonSocial, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(punto.direccion, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Zona: ${punto.zona} • Material: ${punto.materialHabitual}", fontSize = 11.sp)
                            }

                            // Badge atendido / pendiente
                            if (punto.atendido) {
                                Surface(
                                    color = Color(0xFF2E7D32),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = if (punto.motivoOmision != null) "OMITIDO" else "ATENDIDO",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    color = Color(0xFFE65100),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "PENDIENTE",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (mostrarConfirmacionCierre) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmacionCierre = false },
            title = { Text("Confirmar Cierre de Ruta") },
            text = { Text("¿Deseas cerrar la ruta de hoy? Recuerda que todos los puntos deben estar atendidos u omitidos (RN-04).") },
            confirmButton = {
                Button(onClick = {
                    mostrarConfirmacionCierre = false
                    onCerrarRuta()
                }) {
                    Text("Cerrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmacionCierre = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun PantallaDetallePunto(
    punto: PuntoAcopio,
    pesajes: List<Pesaje>,
    rutaAbierta: Boolean,
    puedeRegistrar: Boolean,
    puedeEditar: Boolean,
    puedeAnular: Boolean,
    puedeOmitir: Boolean,
    usuarioId: Long,
    puedeEditarCualquiera: Boolean,
    onVolver: () -> Unit,
    onNuevoPesaje: () -> Unit,
    onEditarPesaje: (Pesaje) -> Unit,
    onAnularPesaje: (Pesaje, String) -> Unit,
    onOmitirPunto: (String) -> Unit
) {
    var mostrarDialogoOmision by remember { mutableStateOf(false) }
    var motivoOmisionTexto by remember { mutableStateOf("") }

    var pesajeAAnular by remember { mutableStateOf<Pesaje?>(null) }
    var motivoAnulacionTexto by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onVolver) {
                Text("← Volver a Rutas")
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(punto.razonSocial, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(punto.direccion, fontSize = 13.sp)
                Text("Zona: ${punto.zona} • Material habitual: ${punto.materialHabitual}", fontSize = 12.sp)
                if (punto.motivoOmision != null) {
                    Text("Motivo de omisión: ${punto.motivoOmision}", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (puedeRegistrar && rutaAbierta) {
                Button(
                    onClick = onNuevoPesaje,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+ Registrar Pesaje", fontSize = 13.sp)
                }
            }

            if (puedeOmitir && rutaAbierta) {
                OutlinedButton(
                    onClick = { mostrarDialogoOmision = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Omitir Punto", fontSize = 13.sp)
                }
            }
        }

        Text("Pesajes Registrados (${pesajes.size})", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))

        if (pesajes.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No hay pesajes registrados para este punto", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(pesajes) { pesaje ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (pesaje.estado == EstadoPesaje.ANULADO) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ID: ${pesaje.id} • ${pesaje.material}", fontWeight = FontWeight.Bold)

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (pesaje.pendiente) {
                                        Surface(color = Color(0xFFFBC02D), shape = RoundedCornerShape(8.dp)) {
                                            Text("PENDIENTE SYNC", color = Color.Black, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }

                                    Surface(
                                        color = if (pesaje.estado == EstadoPesaje.ANULADO) Color.Gray else Color(0xFF1976D2),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(pesaje.estado.name, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Bruto: ${pesaje.pesoBruto} kg • Tara: ${pesaje.tara} kg • Neto: ${pesaje.pesoNeto} kg", fontSize = 13.sp)
                            if (pesaje.humedadEstimada != null) {
                                Text("Humedad Estimada: ${pesaje.humedadEstimada}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("Operario: ${pesaje.operarioNombre} • ${pesaje.fechaHora}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)

                            if (!pesaje.observacion.isNullOrBlank()) {
                                Text("Obs: ${pesaje.observacion}", fontSize = 11.sp)
                            }

                            if (pesaje.motivoAnulacion != null) {
                                Text("Motivo anulación: ${pesaje.motivoAnulacion}", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }

                            // Acciones de correccion y anulacion
                            if (rutaAbierta && pesaje.estado != EstadoPesaje.ANULADO) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    val puedeEditar = puedeEditar && (puedeEditarCualquiera || (pesaje.operarioId == usuarioId))
                                    if (puedeEditar) {
                                        TextButton(onClick = { onEditarPesaje(pesaje) }) {
                                            Text("Corregir", fontSize = 12.sp)
                                        }
                                    }

                                    if (puedeAnular) {
                                        TextButton(onClick = { pesajeAAnular = pesaje }) {
                                            Text("Anular", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogo Omision
    if (mostrarDialogoOmision) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoOmision = false },
            title = { Text("Omitir Punto de Acopio") },
            text = {
                OutlinedTextField(
                    value = motivoOmisionTexto,
                    onValueChange = { motivoOmisionTexto = it },
                    label = { Text("Motivo de omisión") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (motivoOmisionTexto.isNotBlank()) {
                            onOmitirPunto(motivoOmisionTexto)
                            mostrarDialogoOmision = false
                        }
                    }
                ) {
                    Text("Registrar Omisión")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoOmision = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Dialogo Anulacion
    pesajeAAnular?.let { pesaje ->
        AlertDialog(
            onDismissRequest = { pesajeAAnular = null },
            title = { Text("Anular Pesaje #${pesaje.id}") },
            text = {
                OutlinedTextField(
                    value = motivoAnulacionTexto,
                    onValueChange = { motivoAnulacionTexto = it },
                    label = { Text("Motivo de anulación (obligatorio)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (motivoAnulacionTexto.isNotBlank()) {
                            onAnularPesaje(pesaje, motivoAnulacionTexto)
                            pesajeAAnular = null
                            motivoAnulacionTexto = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirmar Anulación")
                }
            },
            dismissButton = {
                TextButton(onClick = { pesajeAAnular = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun PantallaFormularioPesaje(
    pesajeExistente: Pesaje?,
    onVolver: () -> Unit,
    onGuardar: (material: String, bruto: Double, tara: Double, obs: String?, humedad: Double?) -> Unit
) {
    var material by remember { mutableStateOf(pesajeExistente?.material ?: "CARTON") }
    var pesoBrutoTexto by remember { mutableStateOf(pesajeExistente?.pesoBruto?.toString() ?: "") }
    var taraTexto by remember { mutableStateOf(pesajeExistente?.tara?.toString() ?: "0.0") }
    var humedadTexto by remember { mutableStateOf(pesajeExistente?.humedadEstimada?.toString() ?: "") }
    var observacion by remember { mutableStateOf(pesajeExistente?.observacion ?: "") }

    val humedadValida = humedadTexto.isBlank() || (humedadTexto.toDoubleOrNull()?.let { it in 0.0..100.0 } == true)

    val bruto = pesoBrutoTexto.toDoubleOrNull() ?: 0.0
    val tara = taraTexto.toDoubleOrNull() ?: 0.0
    val netoEstimado = bruto - tara

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onVolver) {
            Text("← Cancelar")
        }

        Text(
            text = if (pesajeExistente == null) "Registrar Pesaje (Offline)" else "Corregir Pesaje #${pesajeExistente.id}",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = material,
            onValueChange = { material = it },
            label = { Text("Material (CARTON, METAL, PLASTICO, VIDRIO, etc.)") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )

        OutlinedTextField(
            value = pesoBrutoTexto,
            onValueChange = { pesoBrutoTexto = it },
            label = { Text("Peso Bruto (kg)") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )

        OutlinedTextField(
            value = taraTexto,
            onValueChange = { taraTexto = it },
            label = { Text("Tara (kg)") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )

        // Estimacion visual del peso neto en tiempo real (RN-03 y offline)
        Surface(
            color = if (netoEstimado > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Peso Neto Estimado: ${if (netoEstimado > 0) "$netoEstimado kg" else "Inválido (bruto debe superar tara)"}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (netoEstimado > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "* Calculado localmente. Sujeto a confirmación autoritativa del backend al sincronizar.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        OutlinedTextField(
            value = humedadTexto,
            onValueChange = { humedadTexto = it },
            label = { Text("Humedad Estimada (%) (opcional, 0 - 100)") },
            isError = !humedadValida,
            supportingText = if (!humedadValida) { { Text("Debe ser un porcentaje entre 0% y 100%") } } else null,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )

        OutlinedTextField(
            value = observacion,
            onValueChange = { observacion = it },
            label = { Text("Observación (opcional)") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (bruto > tara && bruto > 0 && tara >= 0 && material.isNotBlank() && humedadValida) {
                    onGuardar(material, bruto, tara, observacion.ifBlank { null }, humedadTexto.toDoubleOrNull())
                }
            },
            enabled = bruto > tara && bruto > 0 && tara >= 0 && material.isNotBlank() && humedadValida,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Guardar en este dispositivo")
        }
    }
}

@Composable
fun PantallaPerfil(
    sesion: Sesion,
    pendientes: Long,
    onVolver: () -> Unit,
    onCerrarSesion: () -> Unit
) {
    var mostrarAlertaPendientes by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onVolver) {
            Text("← Volver a Rutas")
        }

        Text("Perfil de Operario", fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(vertical = 8.dp))

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(sesion.nombre, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Usuario: ${sesion.username}", fontSize = 14.sp)
                Text("Rol: ${sesion.rol}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)

                Spacer(modifier = Modifier.height(12.dp))
                Text("Privilegios Asignados:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                sesion.privilegios.forEach { priv ->
                    Text("• $priv", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Estado del Turno y Almacenamiento Local", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    text = if (pendientes > 0) "⚠️ Hay $pendientes registros pendientes de sincronizar en este dispositivo."
                    else "✓ Todos los registros están sincronizados con el servidor.",
                    color = if (pendientes > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (pendientes > 0) {
                    mostrarAlertaPendientes = true
                } else {
                    onCerrarSesion()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Cerrar Sesión")
        }
    }

    if (mostrarAlertaPendientes) {
        AlertDialog(
            onDismissRequest = { mostrarAlertaPendientes = false },
            title = { Text("Pesajes Pendientes Sin Sincronizar") },
            text = {
                Text("Tienes $pendientes pesajes guardados en la cola local. Si sales ahora, estos registros permanecerán resguardados bajo tu usuario y solo tú podrás sincronizarlos al volver a iniciar sesión.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        mostrarAlertaPendientes = false
                        onCerrarSesion()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Cerrar Sesión de Todos Modos")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarAlertaPendientes = false }) {
                    Text("Cancelar y Sincronizar")
                }
            }
        )
    }
}

