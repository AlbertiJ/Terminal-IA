# Terminal-IA

Aplicación Android (Kotlin + Jetpack Compose) pensada como una **capa visual, terminal web local y hub de agentes con aprobación humana sobre una instalación real de Termux**.

> **Principio de arquitectura:** Terminal-IA **no** reimplementa Linux ni empaqueta Termux dentro del APK. El entorno Linux real, los paquetes (`pkg`/`apt`) y el servidor SSH (`sshd`) los aporta **Termux** instalado por separado; Terminal-IA se conecta por SSH local con clave Ed25519 a `127.0.0.1`.

---

## Qué es

Terminal-IA reúne en una sola interfaz Material 3:
1. **Modo Real (Termux SSH):** cliente SSH real (`mwiede/jsch`) con autenticación exclusiva por clave Ed25519 y emulador de terminal VT/ANSI (`ConnectBot termlib`) conectado a `127.0.0.1:8022`.
2. **Asistente de primer uso:** detecta si Termux está instalado, enlaza a su descarga oficial en F-Droid / GitHub Releases y genera **un único comando** listo para pegar en Termux.
3. **Terminal Web autenticada:** servidor HTTP/HTTPS embebido (puerto `8080`, en `127.0.0.1` por defecto) que opera exclusivamente sobre la sesión SSH real conectada.
4. **Agentes IA con aprobación obligatoria:** cliente para proveedores reales (Anthropic Cloud, OpenAI/compatible HTTPS o un servidor local compatible con OpenAI dentro de Termux en `127.0.0.1`) que **solo propone comandos** para tu confirmación y ofrece un botón inmediato de **PARAR**.
5. **Modo Práctica (simulado):** entorno de consola VFS en memoria (`/home/user`), claramente rotulado como simulado para probar comandos básicos o editar scripts sin tocar el sistema real.

---

## Instalar en 3 pasos

1. **Baja e instala el APK de Terminal-IA**
   - Descarga `Terminal-IA-<versión>-debug.apk` desde la sección [**Releases**](../../releases) del repositorio e instálalo en tu dispositivo Android.
2. **Instala Termux (desde F-Droid o GitHub Releases)**
   - Instala **Termux** desde [F-Droid](https://f-droid.org/packages/com.termux/) o desde [GitHub Releases de termux/termux-app](https://github.com/termux/termux-app/releases).
   - *Importante:* usa la compilación de F-Droid o GitHub (la versión antigua de Google Play está obsoleta y no recibe actualizaciones de paquetes).
3. **Conecta con un solo comando**
   - Abre **Terminal-IA**, entra al asistente de Termux y toca **Copiar comando de configuración**.
   - Pega ese comando en **Termux** y ejecútalo: instalará `openssh` si falta, agregará la clave pública Ed25519 de Terminal-IA en `~/.ssh/authorized_keys`, configurará `sshd` en `127.0.0.1:8022` **sin contraseña** y arrancará el servicio.
   - Vuelve a **Terminal-IA**, pulsa **Probar conexión** y abre el **Modo Real**.

---

## Compilar desde el código

### Requisitos locales
- **JDK 17** o superior (con `keytool` en el `PATH`).
- **Android SDK** con `ANDROID_SDK_ROOT` o `ANDROID_HOME` configurado (el proyecto usa `compileSdk 36`, `minSdk 24`, `targetSdk 36`).
- **Gradle 9.3.1** en el `PATH` (o Android Studio compatible).

### Pasos
1. Verifica el entorno y genera `debug.keystore` si aún no existe (este script **no descarga nada**):
   ```bash
   bash scripts/setup-dev.sh
   ```
2. Ejecuta todas las pruebas unitarias y Robolectric:
   ```bash
   gradle :app:testDebugUnitTest
   ```
3. Compila el APK Debug instalable:
   ```bash
   gradle :app:assembleDebug
   ```
   El APK generado quedará en:
   `app/build/outputs/apk/debug/app-debug.apk`

- **Compilación Release (firma propia):** requiere definir `KEYSTORE_PATH`, `STORE_PASSWORD` y `KEY_PASSWORD` (alias `upload`).
- **Instalación en almacenamiento:** el manifiesto declara `android:installLocation="auto"`. Si mueves la app a tarjeta SD externa y la tarjeta no está montada al encender el dispositivo, Android omitirá los eventos de arranque hasta abrir la app manualmente.
- **Integración continua (CI):** el flujo [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml) ejecuta las pruebas y compila el APK debug en cada `push`, y crea automáticamente un GitHub Release al publicar una etiqueta `v*`. Si existe el secreto opcional `DEBUG_KEYSTORE_BASE64`, lo utiliza; si no, genera un `debug.keystore` temporal sin exigir secretos.

---

## Cómo usar Modo Real, Terminal Web y Agentes

### 1. Modo Real (Termux por SSH)
- Terminal-IA genera un par de claves **Ed25519** dentro de la app y cifra la clave privada mediante **Android Keystore** (`AndroidKeyStoreWrappingCipher`).
- En la primera conexión aplica verificación de clave de servidor **TOFU** (*Trust On First Use*) y avisa si la huella del `sshd` cambia inesperadamente.
- El `sshd` configurado por el asistente escucha únicamente en `127.0.0.1` con autenticación por clave pública (`PasswordAuthentication no`).

### 2. Terminal Web
- **Desactivada por defecto en red y al arrancar:** cuando la enciendes desde **Acceso Remoto**, escucha en `127.0.0.1:8080`. Solo expone `0.0.0.0:8080` en tu Wi-Fi/LAN si activas explícitamente el interruptor de red local.
- **Credenciales obligatorias desde la app:** la web **no permite crear usuarios ni contraseñas** ni existe un usuario por defecto (`"user"` está rechazado). Debes crear credenciales desde la app (contraseña mínima de **12 caracteres**, almacenada con **PBKDF2WithHmacSHA256 ≥ 310.000 iteraciones**).
- **Protecciones incluidas:** validación estricta de cabecera `Origin` (sin `Access-Control-Allow-Origin: *`), cookie de sesión `HttpOnly` + `SameSite=Strict` (`Secure` cuando HTTPS está activo), vencimiento de sesión por TTL, bloqueo temporal tras 5 intentos fallidos, escape HTML/JS de salida y modo **HTTPS opcional** con certificado RSA autofirmado.
- **Solo Modo Real:** si el canal SSH con Termux no está conectado, la consola web informa ese estado y no ejecuta comandos ni simula resultados.

### 3. Agentes de IA
- **Proveedores soportados:**
  - **Anthropic Cloud API** (HTTPS con tu propia clave).
  - **OpenAI o API compatible en la nube** (exige **HTTPS**).
  - **Servidor local compatible con OpenAI en Termux** (por ejemplo `ollama` o `llama.cpp` escuchando en `http://127.0.0.1:<puerto>/v1` o `localhost`).
  - **Sin proveedor (`NONE`):** si no configuras uno, el agente informa explícitamente que no hay proveedor activo; **nunca fabrica ni simula respuestas**.
- **Solo propuestas y aprobación por defecto:** cada comando propuesto por el modelo requiere confirmación manual (`Aprobar SSH`) antes de enviarse al SSH real.
- **Auto-aprobación acotada (desactivada por defecto):** solo puede autorizar comandos simples presentes en tu **lista blanca editable de lectura** (ej. `ls`, `cat`, `pwd`, `whoami`) y confinados estrictamente dentro de la **carpeta de trabajo elegida**. Nunca permite auto-aprobar borrado, instalación/desinstalación de paquetes, `chmod`/`chown`, movimiento/escritura, red saliente, redirecciones, encadenamientos (`;`, `&&`, `|`) ni rutas fuera de la carpeta de trabajo (`..`, `/etc/...`, `~/.ssh/...`).
- **Botón `PARAR` y auditoría:** corta al instante peticiones HTTP al modelo y canales SSH activos. Todas las claves de API se cifran con **Android Keystore** y se redactan automáticamente de logs y exportaciones JSON.

---

## Acceso desde afuera (SOLO como guía)

> **Regla de seguridad:** **NUNCA** abras puertos del teléfono ni hagas *port forwarding* en tu router hacia Internet para exponer la Terminal Web (`8080`) o `sshd`.

Si necesitas acceder a tu teléfono desde otra red, hazlo únicamente mediante una red privada cifrada:

1. **Opción recomendada — VPN en malla (WireGuard o Tailscale):**
   - Instala WireGuard o Tailscale en tu teléfono y en tu computadora.
   - Mantén el servicio SSH en `127.0.0.1` o enlaza el acceso únicamente sobre la interfaz de la VPN privada, siempre con clave pública (sin contraseña) y HTTPS activo si usas la Terminal Web por LAN/VPN.
2. **Alternativa — Túnel SSH inverso hacia un VPS propio:**
   - Desde Termux en el teléfono, abre un túnel saliente autenticado **solo por clave** hacia un servidor VPS que tú controles, exponiendo el puerto **en loopback del VPS (`127.0.0.1`)**:
     ```bash
     ssh -N -R 127.0.0.1:8080:127.0.0.1:8080 usuario@tu-vps.ejemplo
     ```
   - Luego conéctate desde tu PC al VPS mediante túnel local (`ssh -L 8080:127.0.0.1:8080 usuario@tu-vps.ejemplo`) y abre `https://127.0.0.1:8080` (o `http://127.0.0.1:8080`). De ese modo ningún puerto queda abierto a Internet público.

---

## Qué NO hace

- **No incluye Termux ni binarios Linux dentro del APK:** sin instalar Termux por separado no hay entorno Linux real ni paquetes `pkg`/`apt`.
- **No otorga permisos `root`** ni evade restricciones del sistema operativo Android o SELinux.
- **No simula respuestas de IA:** sin API key válida o servidor local activo, los agentes devuelven el error de configuración correspondiente.
- **No tiene servidor SMTP ni envío de correos**, ni sincronización en la nube de tus sesiones, historial o claves.
- **No se orienta a publicación directa en Google Play** en su modalidad completa que orquesta ejecución de binarios externos vía Termux (por eso se distribuye vía GitHub Releases / código fuente).

---

## Riesgos a tener en cuenta

- **Ejecución real en Termux:** todo comando que apruebes en **Modo Real** o desde el panel de **Agentes IA** se ejecuta realmente en tu entorno Termux (puede borrar archivos de Termux, consumir CPU/batería o realizar conexiones de red según el comando aprobado). Revisa siempre el comando propuesto antes de pulsar **Aprobar SSH**.
- **Tráfico en red local (LAN):** si activas la Terminal Web en `0.0.0.0` sin activar el modo HTTPS, las sesiones viajarán en HTTP plano dentro de tu red Wi-Fi. Activa **HTTPS** o limita el servidor a `127.0.0.1` + túnel/VPN.
- **Certificado autofirmado:** el modo HTTPS opcional genera un certificado autofirmado por sesión/instalación; el navegador mostrará un aviso de certificado no emitido por una autoridad pública (puedes comparar su huella SHA-256 mostrada en la app).
- **Privacidad con APIs en la nube:** cuando usas Anthropic u OpenAI en la nube, tus mensajes y el contexto que incluyas se envían a los servidores de ese proveedor. Para uso totalmente privado/offline, utiliza un servidor local compatible con OpenAI dentro de Termux en `127.0.0.1`.
- **Auto-arranque y ahorro de batería:** en Android 14/15/16, las restricciones del fabricante (Doze / optimización de batería) y los límites de `BOOT_COMPLETED` pueden retrasar o impedir notificaciones/tareas al encender el dispositivo si el sistema suspende procesos en segundo plano.

---

## Licencia y avisos de terceros

- Proyecto publicado bajo la [**Licencia MIT**](LICENSE).
- Consulta las licencias de todas las bibliotecas usadas en [**THIRD_PARTY_NOTICES.md**](THIRD_PARTY_NOTICES.md).
