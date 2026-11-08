package com.example.server

import android.content.Context
import com.example.data.TerminalPreferences
import com.example.engine.TerminalEngine
import com.example.model.AgentSettings
import com.example.model.LineType
import com.example.model.TerminalLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

sealed class ServerState {
    object Stopped : ServerState()
    data class Running(val port: Int, val activeClients: Int) : ServerState()
    data class Error(val message: String) : ServerState()
}

class RemoteConsoleServer(
    private val context: Context,
    private val preferences: TerminalPreferences,
    private val engine: TerminalEngine,
    private val onLogMessage: (String, LineType) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val activeConnectionsCount = AtomicInteger(0)

    var state: ServerState = ServerState.Stopped
        private set

    fun getDeviceIpAddresses(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: ""
                        if (host.isNotEmpty() && !host.startsWith("127.")) {
                            ips.add(host)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (ips.isEmpty()) {
            ips.add("127.0.0.1 (Localhost)")
        }
        return ips
    }

    fun start(port: Int = preferences.getRemotePort()) {
        if (serverJob?.isActive == true) return

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                state = ServerState.Running(port, activeConnectionsCount.get())
                preferences.setRemoteServerEnabled(true)
                preferences.setRemotePort(port)

                val ips = getDeviceIpAddresses().joinToString(", ")
                onLogMessage(
                    "[Servidor Remoto SSH/Telnet iniciado en puerto $port]\nIPs detectadas: $ips\nComando de conexión: ssh ${preferences.getRemoteUsername()}@<IP> -p $port",
                    LineType.SYSTEM
                )

                while (isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    scope.launch {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    state = ServerState.Error("Error en servidor remoto: ${e.localizedMessage}")
                    onLogMessage("Error en servidor remoto: ${e.localizedMessage}", LineType.ERROR)
                }
            } finally {
                state = ServerState.Stopped
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
            serverSocket = null
            serverJob?.cancel()
            serverJob = null
            state = ServerState.Stopped
            preferences.setRemoteServerEnabled(false)
            onLogMessage("[Servidor Remoto detenido]", LineType.SYSTEM)
        } catch (e: Exception) {
            onLogMessage("Error al detener servidor: ${e.localizedMessage}", LineType.ERROR)
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        val clientIp = socket.inetAddress.hostAddress ?: "desconocido"
        val security = preferences.getSecurityManager()

        if (security.isIpLockedOut(clientIp)) {
            val remain = security.getRemainingLockoutSeconds(clientIp)
            try {
                val out = PrintWriter(socket.getOutputStream(), true)
                out.println("HTTP/1.1 403 Forbidden - Acceso bloqueado por seguridad.")
                out.println("Demasiados intentos fallidos. Reintenta en $remain segundos.")
                socket.close()
            } catch (_: Exception) {}
            onLogMessage("[Conexión remota rechazada desde $clientIp: IP bloqueada temporalmente]", LineType.ERROR)
            return@withContext
        }

        activeConnectionsCount.incrementAndGet()
        onLogMessage("[Nueva conexión remota establecida desde $clientIp]", LineType.SYSTEM)

        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            // Banner
            writer.println("\r\n==================================================================")
            writer.println("   TERMUX AI - CONSOLA REMOTA SEGURA (SSH / TELNET BRIDGE)        ")
            writer.println("==================================================================")

            var authenticatedUser: String? = null

            // 1. Check if user credentials exist
            if (!preferences.hasRemoteCredentials()) {
                writer.println("[AVISO DE SEGURIDAD]")
                writer.println("No se ha configurado usuario y clave para el acceso remoto.")
                writer.println("Por favor, crea tus credenciales ahora para proteger el terminal:\r\n")

                var newUsername = ""
                while (newUsername.isBlank()) {
                    writer.print("Nuevo usuario (ej: admin): ")
                    writer.flush()
                    newUsername = reader.readLine()?.trim() ?: ""
                }

                var newPassword = ""
                while (newPassword.length < 4) {
                    writer.print("Nueva contraseña (mínimo 4 caracteres): ")
                    writer.flush()
                    newPassword = reader.readLine()?.trim() ?: ""
                    if (newPassword.length < 4) {
                        writer.println("Error: La contraseña debe tener al menos 4 caracteres.")
                    }
                }

                writer.print("Confirmar contraseña: ")
                writer.flush()
                val confirmPass = reader.readLine()?.trim() ?: ""

                if (newPassword != confirmPass) {
                    writer.println("Las contraseñas no coinciden. Desconectando...")
                    socket.close()
                    return@withContext
                }

                preferences.setRemoteCredentials(newUsername, newPassword)
                authenticatedUser = newUsername
                writer.println("\r\n[Usuario y contraseña creados y encriptados con PBKDF2 satisfactoriamente]\r\n")
                onLogMessage("[Credenciales remotas configuradas por el usuario: $newUsername]", LineType.SYSTEM)
            } else {
                // Authenticate existing user
                var attempts = 0
                val maxTries = 3

                while (attempts < maxTries && authenticatedUser == null) {
                    writer.print("login: ")
                    writer.flush()
                    val user = reader.readLine()?.trim() ?: ""

                    writer.print("Password: ")
                    writer.flush()
                    val pass = reader.readLine()?.trim() ?: ""

                    if (preferences.verifyRemoteCredentials(user, pass)) {
                        authenticatedUser = user
                        security.resetFailedAttempts(clientIp)
                        writer.println("\r\nAcceso concedido. Bienvenido a Termux AI Remoto.")
                    } else {
                        attempts++
                        security.recordFailedAttempt(clientIp)
                        writer.println("Credenciales incorrectas ($attempts/$maxTries).")
                    }
                }

                if (authenticatedUser == null) {
                    writer.println("Demasiados intentos fallidos. Conexión cerrada.")
                    onLogMessage("[Fallo de autenticación remota desde $clientIp]", LineType.ERROR)
                    socket.close()
                    return@withContext
                }
            }

            onLogMessage("[Usuario '$authenticatedUser' autenticado remotamente desde $clientIp]", LineType.SYSTEM)

            // Shell session loop
            var currentDirectory = "/home/user"
            writer.println("Escribe 'help' para comandos, 'run <script>' para ejecutar un script, o 'exit' para salir.\r\n")

            while (isActive && !socket.isClosed) {
                writer.print("$authenticatedUser@termux:$currentDirectory$ ")
                writer.flush()

                val line = reader.readLine() ?: break
                val command = line.trim()
                if (command.isEmpty()) continue

                if (command == "exit" || command == "quit") {
                    writer.println("Cerrando sesión remota. Hasta luego!")
                    break
                }

                // Execute via TerminalEngine
                engine.executeCommand(
                    input = command,
                    currentDir = currentDirectory,
                    settings = preferences.getSettings(),
                    onDirectoryChange = { newDir ->
                        currentDirectory = newDir
                    },
                    onOpenEditor = { path, _ ->
                        writer.println("[Editor no disponible en modo consola remota pura. Usa 'cat $path' o comandos echo]")
                    },
                    onOutputLine = { outLine ->
                        val plainText = outLine.text
                            .replace(Regex("\u001B\\[[0-9;]*m"), "") // strip ANSI for standard telnet or send clean
                        writer.println(plainText)
                    }
                )
                writer.println()
            }
        } catch (e: Exception) {
            onLogMessage("[Sesión remota terminada con $clientIp: ${e.localizedMessage}]", LineType.SYSTEM)
        } finally {
            activeConnectionsCount.decrementAndGet()
            try { socket.close() } catch (_: Exception) {}
        }
    }
}
