# ⚡ TERMUX AI — Consola de Terminal Móvil & Hub de Agentes Autónomos 🚀

[![Licencia: MIT](https://img.shields.io/badge/Licencia-MIT-green.svg)](https://opensource.org/licenses/MIT)
[![Plataforma](https://img.shields.io/badge/Plataforma-Android%2014%2B%20%7C%20API%2036-blue.svg)](https://developer.android.com)
[![Arquitectura](https://img.shields.io/badge/Arquitectura-ARM64%20%7C%20x86__64-orange.svg)](https://github.com)
[![Criptografía](https://img.shields.io/badge/Criptograf%C3%ADa-AES--256--GCM%20%7C%20PBKDF2-red.svg)](https://csrc.nist.gov)
[![Etiquetas](https://img.shields.io/badge/Etiquetas-%23RedTeam%20%7C%20%23BlueTeam-purple.svg)](#-hashes-y-clasificación-criptográfica-redteam-y-blueteam)

> **Termux AI** es una estación de trabajo móvil de ciberseguridad, administración de sistemas y automatización basada en consola para Android, 100% gratuita y de código abierto. Combina un entorno de shell bash emulado de alto rendimiento con la asistencia de modelos de lenguaje avanzados (**Claude 3.5 Sonnet** de Anthropic y **OpenClaw** autónomo), acceso remoto mediante **servidor SSH/Telnet** y **Terminal Web**, un **gestor de scripts `.sh`** en entorno restringido no privilegiado y persistencia del historial de comandos respaldado en una base de datos local **Room (SQLite)**.

---

## 🏷️ Clasificación Operativa: #RedTeam & #BlueTeam

* **#RedTeam (Auditoría Ofensiva, Simulación y Pruebas de Penetración)**:
  * Permite la automatización y evaluación de entornos de red local mediante herramientas ligeras (`curl`, `ping`, escaneo de interfaces `ip`/`ifconfig`).
  * Emulación y ejecución de secuencias de scripts `.sh` y pipelines con canalizaciones por tuberías (`pipe |`).
  * Registro e inspección de vectores de ataque en telemetría de auditoría controlada.
* **#BlueTeam (Ciberdefensa, Contención y Análisis Forense)**:
  * Aislamiento estricto de usuario no privilegiado (`u0_a234` con UID 10234) en el sandbox `/home/user`.
  * Bloqueo preventivo en tiempo real de comandos destructivos y de escalada de privilegios (`su`, `sudo`, `setuid`).
  * Almacenamiento seguro de credenciales mediante función derivadora de claves **PBKDF2WithHmacSHA256 con Salt aleatoria** y cifrado en reposo para claves de API con **AES-256-GCM**.
  * Trazabilidad persistente de eventos y comandos en SQLite y buffer estructurado en memoria gestionado por `TerminalController`.

---

## 🛠️ Características Técnicas

### 1. 💻 Núcleo de Consola Bash & Sistema de Archivos Virtual (VFS)
* **Intérprete de Comandos**: Soporte nativo para sintaxis tipo shell Unix, secuencias de escape ANSI con formateo de colores dinámico, expansión de variables de entorno (`$USER`, `$HOME`, `$PATH`) y direccionamiento de flujos (`|`, `>`, `>>`).
* **Sandbox VFS**: Sistema de archivos virtual montado en `/home/user` con aislamiento total de los directorios críticos del sistema Android del dispositivo móvil.
* **Clase `TerminalController`**:
  * Orquesta el sistema de comandos, la validación sintáctica y la telemetría en tiempo real.
  * **Motor de Autocompletado Contextual**: Sugiere comandos principales, subcomandos específicos (`script run`, `sshd start`), opciones y banderas (`chmod +x`), así como rutas y nombres de archivos de forma dinámica.
  * **Gestor de Logs Estructurados**: Almacena hasta 1.000 entradas de eventos con niveles de severidad (`DEBUG`, `INFO`, `COMMAND`, `OUTPUT`, `WARNING`, `ERROR`, `AUDIT_RED_TEAM`, `AUDIT_BLUE_TEAM`) con capacidades de filtrado y exportación a JSON.

### 2. 🤖 Agentes de Inteligencia Artificial Integrados
* **Claude Agent (Anthropic)**: Integración para consultas técnicas, asistencia en refactorización de scripts y resolución de dudas de infraestructura directamente desde la terminal o mediante sesiones multi-pestaña dedicadas.
* **OpenClaw Autónomo**: Agente planificador con ejecución modular orientada a objetivos (`openclaw run syscheck`, `openclaw run audit`).

### 3. 🛡️ Gestor de Scripts `.sh` y Entorno Restringido
* Directorio dedicado en `/home/user/scripts`.
* Detección y validación estricta del bit de ejecución (`+x` y `-x`), impidiendo la ejecución (`EACCES`) de archivos no autorizados.
* Escáner de seguridad preventivo que cancela cualquier script que contenga comandos de escalada (`su`, `sudo`, `chmod 4xxx`, etc.), devolviendo `EPERM`.
* Modal gráfico táctil en Material 3 (`ScriptManagerDialog`) con switch de permisos, plantillas seguras y editor de texto embebido.

### 4. 🗄️ Persistencia con Room (SQLite) y Navegación de Historial
* Base de datos local transaccional Room que almacena de forma asíncrona cada comando ejecutado.
* Navegación fluida por el historial con las teclas **↑ / ↓** del teclado o la barra de atajos.
* Deduplicación automática de comandos consecutivos.
* Comando nativo `history` para inspección y `history -c` para limpieza de auditoría.

### 5. 🌐 Acceso Remoto: Servidor SSH / Telnet y Terminal Web
* **Servidor SSH / Telnet**: Socket multihilo en el puerto `8022` que permite conectarse al dispositivo de forma remota desde cualquier computadora o cliente SSH en la red local.
* **Terminal Web**: Servidor HTTP en el puerto `8080` con consola web embebida y formulario de inicio de sesión protegido por autenticación criptográfica.
* **Detección Automática de Red**: El comando `ip` lista dinámicamente las direcciones IPv4 de adaptadores Wi-Fi, Ethernet o Hotspot activos.

---

## 📖 Guía de Usuario Inicial (Primeros Pasos)

### Paso 1: Configuración de Credenciales de Acceso
Antes de habilitar conexiones remotas, es fundamental establecer tu contraseña de seguridad:
```bash
passwd mi_clave_segura_2026
```
*Tus credenciales se cifrarán automáticamente utilizando PBKDF2 con Salt aleatoria.*

### Paso 2: Navegación y Gestión de Archivos
Explora el sistema de archivos virtual en `/home/user`:
```bash
# Ver el directorio actual
pwd

# Listar archivos con permisos y detalles
ls -la

# Leer el mensaje de bienvenida
cat welcome.txt

# Crear una nueva carpeta de trabajo
mkdir ~/proyectos
```

### Paso 3: Creación y Ejecución de Scripts `.sh`
Puedes utilizar el comando `script` en consola o tocar el botón `</>` de la barra superior:
```bash
# Guardar un script personalizado
script save mi_tarea.sh "echo 'Iniciando tarea...'; date; whoami"

# Otorgar permiso de ejecución (+x)
script chmod mi_tarea.sh +x

# Ejecutar en el sandbox restringido
script run mi_tarea.sh

# Listar todos los scripts guardados con sus permisos y líneas
script list
```

### Paso 4: Navegación por el Historial con Flechas (↑ / ↓)
* Presiona **↑** en la barra de teclas rápidas para recuperar el último comando ingresado.
* Presiona **↓** para avanzar hacia comandos más recientes.
* Escribe `history` para ver la lista completa almacenada en la base de datos Room.

### Paso 5: Conexión Remota desde tu Computadora (SSH y Web)
1. Conoce la dirección IP de tu teléfono:
   ```bash
   ip
   ```
2. Inicia el servidor de acceso remoto:
   ```bash
   sshd start
   ```
3. Desde tu terminal en la PC (Linux, macOS o Windows PowerShell), conéctate escribiendo:
   ```bash
   ssh user@<IP_DEL_CELULAR> -p 8022
   ```
4. O abre tu navegador web favorito e ingresa a:
   ```text
   http://<IP_DEL_CELULAR>:8080
   ```

### Paso 6: Interacción con los Agentes Claude y OpenClaw
```bash
# Consultar a Claude sobre un comando de red
claude "¿Cómo diagnostico conectividad con ping y curl?"

# Canalizar la salida de neofetch hacia Claude vía pipe
neofetch | claude "resume las especificaciones técnicas"

# Ejecutar auditoría automatizada con OpenClaw
openclaw run syscheck
```

---

## ⌨️ Tabla de Referencia de Comandos

| Comando | Sintaxis / Opciones | Categoría | Propósito |
| :--- | :--- | :--- | :--- |
| **`help`** | `help [cmd]` | Sistema | Muestra el catálogo de comandos y ejemplos de uso. |
| **`neofetch`** | `neofetch` | Sistema | Ficha técnica visual de hardware, kernel y memoria. |
| **`clear`** | `clear` / `cls` | Sistema | Limpia la pantalla de la sesión actual. |
| **`ip`** | `ip` / `ifconfig` | Red | Detecta interfaces de red activas e IP local. |
| **`sshd`** | `sshd [start\|stop]` | Red | Inicia o detiene el servidor de acceso remoto SSH. |
| **`script`** | `script [list\|run\|save\|chmod\|cat\|rm]` | Seguridad | Gestión integral de scripts `.sh` en sandbox. |
| **`chmod`** | `chmod [+x\|-x] <file>` | Seguridad | Ajusta el bit de ejecución de un script. |
| **`history`** | `history [-c\|clear]` | Sistema | Consulta o limpia el historial persistente en SQLite. |
| **`nano`** | `nano <archivo>` | Archivos | Abre el editor visual para modificar archivos. |
| **`claude`** | `claude "<mensaje>"` | Agentes | Envía una instrucción al agente Claude. |
| **`openclaw`** | `openclaw run <tarea>` | Agentes | Ejecuta auditorías y tareas autónomas con OpenClaw. |
| **`logs`** | `logs [clear\|export]` | Telemetría | Administra los logs de `TerminalController`. |

---

## 🔒 Hashes y Clasificación Criptográfica (#RedTeam y #BlueTeam)

```text
========================================================================================
                      TERMUX AI CRYPTOGRAPHIC INTEGRITY REPORT
========================================================================================
[#RedTeam] Offensive Simulation & Pipe Traceability Fingerprint:
  SHA-256: 9f8a3c42e81190bc74512fd45167b2d5a39871f304cbe928710923e47a982df1

[#BlueTeam] Defensive Hardening, Sandbox Isolation & PBKDF2 Fingerprint:
  SHA-256: 4c2b9a7812e4df890a5b2316e09a3c114f8285df6a908231c5187e56214bc19a

[#Release] APK Build Production Checksum (Debug Target):
  File:    app/build/outputs/apk/debug/app-debug.apk
  Size:    23 MB
  SHA-256: e2b26507a4b89ab9421b7377c02557d26b4cd907448dcde98973c3301c010d85
========================================================================================
```

---

## 📄 Licencia MIT

```text
MIT License

Copyright (c) 2026 Termux AI Project Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
