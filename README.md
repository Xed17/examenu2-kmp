# EcoRutas Móvil (Kotlin Multiplatform + Compose Multiplatform)

Aplicación móvil multiplataforma desarrollada en **Kotlin Multiplatform (KMP)** con UI declarativa unificada en **Compose Multiplatform** para la gestión de pesajes en campo y cumplimiento de rutas de recolección de residuos. Implementa arquitectura **Offline-First**, base de datos local reactiva con **SQLDelight**, sincronización bidireccional contra **EcoAPI** vía **Ktor Client**, y gestión de cola de operaciones pendientes con IDs provisionales negativos.

---

## 1. Cómo Abrir y Ejecutar

### Requisitos Previos
- **JDK 17 o 21** (probado con OpenJDK 21).
- **Android Studio** Ladybug (2024.2+) o superior, con Android SDK 34/35/37 instalado.
- Dispositivo físico Android con depuración USB habilitada o Emulador Android (AVD con API 28+).

### Pasos de Ejecución
1. Abrir la carpeta `kmp/` en Android Studio.
2. Esperar a que Gradle sincronice el proyecto automáticamente.
3. Para ejecutar pruebas unitarias de lógica compartida:
   ```bash
   ./gradlew :shared:testAndroidHostTest
   ```
4. Para compilar y generar el APK Debug:
   ```bash
   ./gradlew :androidApp:assembleDebug
   ```
5. Para instalar y ejecutar en el dispositivo/emulador conectado:
   - Seleccionar la configuración de ejecución `androidApp` y presionar **Run (Shift + F10)**.

---

## 2. Versiones y Targets

- **Kotlin:** 2.4.21
- **AGP (Android Gradle Plugin):** 9.1.1
- **Compose Multiplatform:** 1.12.2
- **Ktor Client:** 3.0.3 (Engine `ktor-client-okhttp` en Android, `ktor-client-mock` para testing)
- **SQLDelight:** 2.0.2 (`app.cash.sqldelight`)
- **Coroutines:** 1.9.0 (`kotlinx.coroutines`)
- **Serialization:** 1.7.3 (`kotlinx.serialization`)
- **Android SDK:**
  - `minSdk`: 28
  - `compileSdk`: 37
  - `targetSdk`: 37
- **Target evaluado:** Android (`androidApp` consumiendo lógica y UI compartida en `shared`).

---

## 3. Configuración de URL Base

La configuración de red se gestiona de forma centralizada en el cliente HTTP (`pe.edu.upeu.ecorecicla.data.remote.EcoApiClient`):

```kotlin
class EcoApiClient(
    private val httpClient: HttpClient = crearHttpClient(),
    var baseUrl: String = "http://10.0.2.2:8080"
)
```

Por defecto, la URL apunta al host local desde el emulador de Android: `http://10.0.2.2:8080`.

---

## 4. Configuración para Emulador y Dispositivo Físico

- **Emulador Android Oficial:**
  Usa `http://10.0.2.2:8080` (alias especial que mapea a `localhost:8080` de la máquina anfitriona donde corre el backend Spring Boot).
- **Dispositivo Físico (vía Wi-Fi LAN):**
  Cambiar la URL base a la IP local de la computadora (ej. `http://192.168.1.50:8080`).
- **Dispositivo Físico (vía ADB Reverse por USB):**
  Ejecutar en la terminal del host:
  ```bash
  adb reverse tcp:8080 tcp:8080
  ```
  Esto permite que el dispositivo acceda directamente a `http://localhost:8080`.
- **Permisos de Red y Cleartext:**
  `androidApp/src/main/AndroidManifest.xml` cuenta con `android:usesCleartextTraffic="true"` y `<uses-permission android:name="android.permission.INTERNET" />` para desarrollo local en HTTP.

---

## 5. Estructura de Capas (Shared Module)

El módulo `shared` sigue los principios de Clean Architecture y aislamiento multiplataforma:

```text
shared/src/commonMain/kotlin/pe/edu/upeu/ecorecicla/
├── domain/
│   └── model/
│       ├── Modelos.kt            # Modelos inmutables de dominio (Usuario, Ruta, Punto, Pesaje, Errores)
├── data/
│   ├── remote/
│   │   ├── dto/                  # DTOs serializables con kotlinx.serialization
│   │   └── EcoApiClient.kt       # Cliente HTTP Ktor con manejo de tokens y traducción de errores
│   ├── local/
│   │   ├── DatabaseManager.kt    # Envoltorio del driver SQLDelight y consultas
│   │   └── DriverFactory.kt      # Factoría de drivers específicos de plataforma
│   └── repository/
│       ├── SesionRepository.kt   # Gestión de token JWT, roles, privilegios y propietario
│       ├── RutaRepository.kt     # Acceso a rutas y puntos en BD local y remoto
│       ├── PesajeRepository.kt   # CRUD de pesajes, generación de IDs negativos y colas
│       └── SincronizacionRepository.kt # Coordinación de sincronización Upload-Before-Download
├── presentation/
│   ├── EcoRutasApp.kt            # Aplicación principal Compose y navegación
│   ├── LoginViewModel.kt         # Lógica de presentación de autenticación
│   ├── RutaViewModel.kt          # Lógica de presentación de ruta activa y puntos
│   ├── PuntoDetalleViewModel.kt  # Lógica de pesajes por punto y sincronización
│   └── PerfilViewModel.kt        # Perfil del operario y cierre de sesión seguro
└── di/
    └── AppContainer.kt           # Contenedor de Inyección de Dependencias manual
```

---

## 6. Modelo de Dominio

- **`UsuarioSesion`:** `id`, `username`, `nombre`, `rol`, `privilegios: Set<String>`, `token: String`.
- **`Ruta`:** `id`, `fecha`, `estado` (`ABIERTA`, `CERRADA`), `cerradaEn`, `cerradaPor`, `puntos: List<PuntoAcopio>`.
- **`PuntoAcopio`:** `id`, `razonSocial`, `direccion`, `zona`, `materialHabitual`, `activo`, `ordenVisita`, `atendido`.
- **`Pesaje`:** `id` (negativo si es provisional/offline, positivo si confirmado en backend), `puntoId`, `material`, `pesoBruto`, `tara`, `pesoNeto`, `observacion`, `operarioId`, `operarioNombre`, `estado` (`REGISTRADO`, `CORREGIDO`, `ANULADO`), `motivoAnulacion`, `pendiente: Boolean`.

---

## 7. Esquema Local (SQLDelight)

Definido en `shared/src/commonMain/sqldelight/pe/edu/upeu/ecorecicla/db/EcoRecicla.sq`:

1. **`punto_acopio_local`:** Cache local de puntos de la ruta.
2. **`ruta_local`:** Estado de la ruta del día.
3. **`pesaje_local`:** Almacenamiento local de pesajes con soporte de identificadores provisionales negativos y bandera de sincronización.
4. **`cola_operaciones`:** Cola persistente de mutaciones pendientes:
   - `id`, `tipo` (`CREAR_PESAJE`, `CORREGIR_PESAJE`, `ANULAR_PESAJE`), `pesaje_local_id`, `payload_json`, `intentos`, `ultimo_error`, `operacion_id`, `propietario_sesion_id`, `creado_en`.

---

## 8. Fuente Local Observable

Todas las pantallas observan la base de datos local SQLite mediante `Flow` reactivos de SQLDelight:
```kotlin
queries.obtenerPesajesPorPunto(puntoId)
    .asFlow()
    .mapToList(Dispatchers.Default)
```
Cualquier inserción, actualización o eliminación local actualiza de inmediato la interfaz de usuario en menos de un fotograma, sin esperar respuestas del servidor.

---

## 9. Cola de Operaciones Pendientes y IDs Negativos

Cuando se crea un pesaje sin conectividad o en modo normal:
1. Se consulta el ID provisional siguiente:
   ```sql
   siguienteIdProvisional:
   SELECT COALESCE(MIN(id), 0) - 1 FROM pesaje_local WHERE id < 0;
   ```
2. Se inserta en `pesaje_local` con el ID negativo (ej. `-1`, `-2`) y `pendiente = 1`.
3. Se encola la operación en `cola_operaciones` con un UUID idempotente (`operacion_id`) y el `propietario_sesion_id`.
4. El cálculo de `pesoNeto` se estima localmente (`pesoBruto - tara`), pero queda marcado como pendiente hasta la confirmación autoritativa del servidor.

---

## 10. Orden Estricto de Sincronización (Upload-Before-Download)

Para garantizar consistencia y evitar que la descarga del servidor sobrescriba o duplique cambios locales no confirmados:
1. **Paso 1 (SUBIDA):** Se leen las operaciones pendientes en `cola_operaciones` ordenadas por `id ASC`.
2. Para cada operación:
   - Se envía la petición HTTP al backend enviando el header de idempotencia `X-Operacion-Id`.
   - Si es creación, el servidor retorna el pesaje oficial con su `id` positivo real y el `pesoNeto` recalculado autoritativamente.
   - Se actualiza el registro local: se sustituye el ID negativo provisional por el ID oficial definitivo en `pesaje_local` y se marca `pendiente = 0`.
   - Se elimina la entrada correspondiente de `cola_operaciones`.
3. **Paso 2 (DESCARGA):** Solo tras vaciar con éxito las operaciones pendientes, se descargan los datos frescos de la ruta activa desde el backend (`GET /api/v1/rutas/mia`) y se actualiza la base de datos local.

---

## 11. Traducción y Resolución de Errores

El cliente `EcoApiClient` intercepta las respuestas HTTP y las transforma en excepciones fuertemente tipadas (`ErrorApi`):
- `400 Bad Request`: `ErrorApi.Validacion` conteniendo el mapa de campos inválidos retornado por el servidor (`validationErrors`).
- `401 Unauthorized`: `ErrorApi.NoAutenticado`.
- `403 Forbidden`: `ErrorApi.SinPrivilegio` (ej. intento de corregir pesaje ajeno o ruta cerrada).
- `404 Not Found`: `ErrorApi.NoEncontrado`.
- `409 Conflict`: `ErrorApi.Conflicto` (ej. ruta ya cerrada, versión obsoleta).
- Errores de red/timeout: `ErrorApi.Red` (la app no se bloquea y preserva la operación en cola).

---

## 12. Almacenamiento Seguro del Token

El token de sesión JWT y los privilegios del usuario activo se custodian en memoria en `SesionRepository` durante el ciclo de vida de la aplicación, inyectándose automáticamente en el encabezado `Authorization: Bearer <token>` de cada petición HTTP.

---

## 13. Política de Turnos Compartidos en Dispositivos

Para evitar que un operario sincronice o altere operaciones pendientes dejadas por otro usuario que utilizó el mismo dispositivo:
1. Toda operación en `cola_operaciones` guarda `propietario_sesion_id`.
2. Durante el proceso de cierre de sesión (`PerfilViewModel.confirmarCerrarSesion`), el sistema consulta si existen operaciones pendientes sin subir para el usuario actual:
   ```kotlin
   val pendientes = pesajeRepository.obtenerCantidadPendientesPorUsuario(usuarioId)
   ```
3. Si `pendientes > 0`, se presenta una advertencia modal explícita solicitando confirmación antes de salir para prevenir la pérdida o el bloqueo de registros.

---

## 14. Pruebas Automatizadas

El proyecto incluye una suite de pruebas en `kmp/shared/src/commonTest/kotlin/pe/edu/upeu/examen/SharedCommonTest.kt`:
1. **`test01_DtoADominio`:** Mapeo correcto de DTOs a modelos de dominio y conversión de estados.
2. **`test02_TraduccionDeErroresApi`:** Verificación de deserialización y tipado de errores 400 (con `validationErrors`) y 403 (pesaje ajeno) usando `Ktor MockEngine`.
3. **`test03_CalculoLocalYProvisionalOffline`:** Lógica de asignación de identificadores negativos provisionales decrecientes (`-1`, `-2`) y cálculo de peso neto.
4. **`test04_OrdenDeSincronizacionSubirAntesDeBajar`:** Validación del protocolo estricto Upload-Before-Download ante la cola local.

Ejecución de pruebas:
```bash
./gradlew :shared:testAndroidHostTest
```

---

## 15. Solicitudes de Cambio (Parte II: SC-A a SC-D)

La implementación de las solicitudes de cambio se realiza en ramas separadas derivadas de `develop`:
- **SC-A:** Filtro "Solo pendientes de pesar" (`sc-a-chambilla`).
  - Rama: `sc-a-chambilla`, Tag: `v1.1-sc-a`.
  - Agrega consulta SQLDelight `listarPuntosDeRutaConFiltro` discriminando `atendido == 0`.
  - Control interactivo `Switch` en `PantallaRuta` ("Solo pendientes de pesar") que actualiza reactivamente la lista de puntos de acopio.
  - Validación con prueba unitaria `test05_FiltroSoloPendientesDePesar`.
- **SC-B:** Rol AUDITOR de solo lectura (`sc-b-chambilla`).
- **SC-C:** Captura de Humedad Estimada (`sc-c-chambilla`).
- **SC-D:** Contador de pesajes pendientes reactivo con SQLDelight (`sc-d-chambilla`).

