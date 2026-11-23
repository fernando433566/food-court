# Food Court

Cartera Android para los programas de fidelización de restaurantes. Reúne tus tarjetas en una sola app y, para las marcas que solo funcionan desde su app oficial, permite usar un teléfono **emisor** (con esas apps y tus sesiones iniciadas) desde cualquier otro dispositivo **receptor**, de forma cifrada.

> **Aviso.** Food Court es un proyecto independiente y **no está afiliado, patrocinado ni respaldado** por McDonald’s, Burger King, Popeyes, KFC, Taco Bell, La Tagliatella (Amici), Club·by, VIPS, Foster’s Hollywood, Ginos, TGI Fridays ni ninguna otra marca o empresa mencionada. Todas las marcas, nombres y logotipos pertenecen a sus respectivos propietarios y se citan solo para identificar los programas compatibles. Los emblemas de la app son dibujos propios y no logotipos oficiales. Úsala con tus propias cuentas y respetando las condiciones de cada programa; el uso es responsabilidad de cada persona.

Versión actual: **0.1.0**. El APK firmado se publica en la sección *Releases* del repositorio.

## Apps soportadas

| Marca | App oficial necesaria en el emisor | Funciones desde el receptor |
|---|---|---|
| **McDonald’s** (MyMcDonald’s) | `com.mcdonalds.mobileapp` | Muestra el código de puntos (texto y QR) leído en el emisor. Premios y Deals incluyen datos de demostración (`FC-DEMO`) y acceso a la app oficial. |
| **Taco Bell** (Taco Bell Club) | `es.tacobell.app` | QR de socio capturado en el emisor; escanear un ticket (QR o código de barras) y registrarlo en la cuenta del emisor; ofertas públicas. |
| **La Tagliatella** (Amici) | `com.amrest.latagliatella` | Registrar un ticket con número, fecha e importe (también deducidos al escanear el QR del ticket); sincronizar nivel y cupones; ver el código de cada cupón; abrir el lector de Amici en el emisor. |
| **Club·by** (VIPS, Foster’s Hollywood, Ginos, Fridays) | `com.clubvips.app` | QR de la tarjeta de socio; «Tus promociones» con el QR de cada una; añadir un código de promoción; enviar QR que sean enlaces de Club·by; abrir el lector QR y el ocio en el emisor; catálogo público de promociones. |
| **Burger King, Popeyes, KFC** | — | Su web oficial dentro de la app (código de tienda, recompensas y ofertas). |

Además: **Almacén** para añadir, quitar y ordenar tarjetas; **Mis códigos** para guardar descuentos; tema claro u oscuro OLED.

## Modos de conexión

El receptor se conecta al emisor de cualquiera de estas formas:

| Modo | Dirección en el receptor | Requisitos |
|---|---|---|
| **Red local** | IP local del emisor, por ejemplo `192.168.1.50` | Los dos dispositivos en la misma red. Puerto TCP 38147. |
| **Netbird** (o Tailscale) | IP `100.x.y.z` o nombre DNS del emisor | Los dos dispositivos conectados a la misma red Netbird. Puerto TCP 38147. |
| **Enlace HTTPS** | `https://tu-dominio` | Un túnel o proxy inverso (Cloudflare Tunnel, Caddy, nginx…) que publique `http://localhost:38148` del emisor. Con Cloudflare Tunnel, el hostname debe usar el servicio **HTTP** `localhost:38148`. |

Todas las peticiones viajan cifradas de extremo a extremo con la clave de emparejamiento (HMAC-SHA256 y AES-GCM), también a través de HTTPS. El emisor solo atiende a receptores que conocen su clave.

## Configuración

### Emisor

1. Instala las apps oficiales que quieras usar e inicia sesión en ellas.
2. En Food Court, abre **Intercambio → Emisor**.
3. Si instalaste el APK fuera de Google Play, ve a *Ajustes → Aplicaciones → Food Court → ⋮ → Permitir ajustes restringidos*.
4. En *Ajustes → Accesibilidad*, activa **Food Court · Emisor de fidelización**.
5. Pulsa **Activar emisor automático** y **Quitar restricciones de batería**. Algunos fabricantes (por ejemplo Samsung) congelan las apps en segundo plano.
6. Copia la clave de emparejamiento para el receptor.

Mientras está activo, el emisor muestra la notificación «Emisor activo». Si el teléfono no tiene PIN, patrón ni contraseña, se enciende solo para cada petición y vuelve a apagarse. Con un bloqueo seguro debe estar desbloqueado, porque Food Court nunca salta ese bloqueo.

*Food Court · Amici* es un servicio aparte, para usar Amici en el mismo teléfono sin emisor.

### Receptor

En la tarjeta de McDonald’s, o en **Intercambio → Receptor**, escribe la dirección del emisor y su clave. La dirección puede cambiarse después sin volver a emparejar. **Revocar emparejamiento** en el emisor invalida la clave anterior.

## Requisitos

- Android 6.0 (API 23) o superior.
- En el emisor, Android 11 o superior para capturar los QR mostrados en pantalla (Taco Bell, Club·by).

## Compilar desde el código

Necesitas el JDK que incluye Android Studio y el Android SDK 36.

```sh
export JAVA_HOME=/ruta/a/android-studio/jbr
export ANDROID_HOME=/ruta/a/Android/Sdk
./gradlew assembleDebug      # APK de depuración en app/build/outputs/apk/debug/
./gradlew assembleRelease    # APK de release en app/build/outputs/apk/release/
```

La firma de *release* es opcional. Crea un `keystore.properties` en la raíz del proyecto (está en `.gitignore`), o apunta a ese archivo con la variable `FOODCOURT_KEYSTORE_PROPERTIES`:

```properties
storeFile=/ruta/a/tu-clave.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Sin ese archivo, `assembleRelease` genera un APK sin firmar.

## Privacidad

Food Court no tiene servidores ni analíticas. Los datos (tarjetas, códigos guardados y emparejamiento) se quedan en tus dispositivos. El catálogo público de Club·by se descarga de las webs oficiales de cada marca.

## Estructura del código

- **Cartera**: `MainActivity`, `WalletCardStyle`, `Franchises` y una clase por marca que implementa `Franchise`. Los dibujos están en `StoreroomArt`, `FoodCardDrawable` y `ClubByTableArtwork`.
- **Pantallas de marca**: `AmiciActivity`, `ClubByActivity`, `TacoBellActivity`, `FranchisePortalActivity` y `DiscountWalletActivity`; `Ui` reúne las piezas comunes.
- **Intercambio**:
  - `CodeExchange`: el protocolo y la lista de operaciones.
  - `SenderClient`: el lado receptor.
  - `HttpsChannel` / `HttpsOrigin`: el mismo protocolo sobre WebSocket para HTTPS.
  - `ExchangeActivity`: las pantallas de emisor y receptor.
- **Emisor**: `OfficialCodeService` (servicio de accesibilidad), `TaskRunner`, `SenderKeepAliveService` y `WakeActivity`.
- **Tareas sobre apps oficiales** (una por flujo, subclases de `AppTask`): `McDonaldsQrTask`, `TacoBellTask`, `ClubCardTask`, `ClubPromotionsTask`, `AmiciScannerTask`, `AmiciTicketTask` y `AmiciBenefitsTask`.

`OfficialCodeService` y `AmiciAccessService` conservan su nombre porque Android guarda los servicios de accesibilidad activados por nombre de clase.

### Añadir una franquicia

Implementa `Franchise`, añádela a `Franchises.ALL` y define su tarjeta en `WalletCardStyle` y su emblema en `StoreroomArt.Emblem`. Si necesita el emisor, crea una `AppTask`, añade la operación en `CodeExchange.Operation` y su caso en `OfficialCodeService.taskFor`.
