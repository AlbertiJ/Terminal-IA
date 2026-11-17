package com.example.agent

/**
 * Policy engine for AI-proposed commands:
 * 1. Never auto-approves by default (user must opt-in to read-only allowlist mode).
 * 2. Hard-blocks dangerous categories from EVER entering the allowlist or auto-approving:
 *    - Delete/wipe (rm, rmdir, unlink, shred, dd, mkfs, find -delete/-exec)
 *    - Package install/uninstall (pkg, apt, apt-get, dpkg, pip, npm, yarn, cargo, etc.)
 *    - Permissions/ownership (chmod, chown, chgrp, chattr)
 *    - Move/rename/modify/write (mv, rename, ln, cp, touch, mkdir, tee, sed, tar, unzip)
 *    - Outbound network (curl, wget, nc, ssh, scp, sftp, rsync, ping, git, telnet, ftp, etc.)
 *    - Shells / interpreters / chaining / redirections / subshells
 *    - Any path outside the user's chosen working directory.
 */
object AgentCommandPolicy {

    const val TERMUX_HOME_PATH = "/data/data/com.termux/files/home"

    val DEFAULT_READ_ONLY_ALLOWLIST: Set<String> = setOf(
        "ls", "cat", "head", "tail", "pwd", "whoami", "id",
        "uname", "date", "df", "free", "uptime", "ps", "which", "wc", "stat", "file"
    )

    val NEVER_AUTO_APPROVE_COMMANDS: Set<String> = setOf(
        // Destructive / delete / disk
        "rm", "rmdir", "unlink", "shred", "truncate", "dd", "mkfs", "srm", "wipefs", "fdisk", "parted",
        // Install / uninstall / build / package managers
        "pkg", "apt", "apt-get", "aptitude", "dpkg", "pip", "pip2", "pip3",
        "npm", "npx", "yarn", "pnpm", "bun", "gem", "cargo", "rustup", "go",
        "brew", "pacman", "yum", "dnf", "apk", "make", "cmake", "ninja", "termux-setup-storage",
        // Permissions / ownership / attributes
        "chmod", "chown", "chgrp", "umask", "chattr", "setfacl",
        // Move / rename / write / link / edit / archive write
        "mv", "rename", "ln", "cp", "touch", "mkdir", "tee", "install",
        "sed", "ed", "vi", "vim", "nvim", "nano", "emacs",
        "tar", "unzip", "zip", "gzip", "gunzip", "bzip2", "xz",
        // Outbound network & remote transfer
        "curl", "wget", "fetch", "aria2c", "nc", "ncat", "netcat", "socat",
        "ssh", "scp", "sftp", "rsync", "ping", "ping6", "traceroute", "tracepath",
        "dig", "nslookup", "host", "telnet", "ftp", "nmap", "whois", "openssl",
        "git", "tailscale", "wg",
        // Shells / interpreters / wrappers / process termination
        "sh", "bash", "zsh", "dash", "fish", "ash", "csh", "tcsh",
        "su", "sudo", "eval", "exec", "source", ".", "xargs", "env",
        "nohup", "timeout", "nice", "watch", "kill", "pkill", "killall",
        "python", "python2", "python3", "perl", "ruby", "node", "php", "lua", "awk", "gawk"
    )

    private val SHELL_METACHAR_REGEX = Regex("""[;&|`<>()\n\r]|\$\(|\$\{""")

    data class ProposalPolicyDecision(
        val canAutoApprove: Boolean,
        val baseCommand: String,
        val reason: String,
        val isReadOnlySafeInWorkspace: Boolean
    )

    fun validateAllowlistEntry(rawCommandName: String): Result<String> {
        val candidate = rawCommandName.trim()
        if (candidate.isEmpty()) {
            return Result.failure(IllegalArgumentException("Ingresa el nombre de un comando (ej. 'ls' o 'cat')."))
        }
        if (candidate.any { it.isWhitespace() } || SHELL_METACHAR_REGEX.containsMatchIn(candidate)) {
            return Result.failure(IllegalArgumentException("Solo se permiten nombres de comando simples sin argumentos ni operadores."))
        }
        if (candidate.contains('/')) {
            return Result.failure(IllegalArgumentException("Usa solo el nombre base del comando sin rutas (ej. 'ls', no '/bin/ls')."))
        }
        val normalized = candidate.lowercase()
        if (normalized in NEVER_AUTO_APPROVE_COMMANDS) {
            return Result.failure(
                IllegalArgumentException("Bloqueado por política: '$normalized' nunca puede entrar en la lista blanca de auto-aprobación.")
            )
        }
        if (!Regex("^[a-z0-9][a-z0-9._-]{0,31}$").matches(normalized)) {
            return Result.failure(IllegalArgumentException("Nombre de comando inválido: '$candidate'."))
        }
        return Result.success(normalized)
    }

    fun sanitizeAllowlist(entries: Collection<String>): Set<String> {
        return entries.mapNotNull { validateAllowlistEntry(it).getOrNull() }.toSet()
    }

    fun normalizeWorkingDirectory(rawWorkingDir: String): String {
        val trimmed = rawWorkingDir.trim().ifEmpty { "~" }
        val expanded = when {
            trimmed == "~" -> TERMUX_HOME_PATH
            trimmed.startsWith("~/") -> "$TERMUX_HOME_PATH/${trimmed.removePrefix("~/")}"
            trimmed.startsWith("/") -> trimmed
            else -> "$TERMUX_HOME_PATH/$trimmed"
        }
        return normalizePosixPath(expanded) ?: TERMUX_HOME_PATH
    }

    fun evaluateProposal(
        command: String,
        workingDir: String,
        autoApproveEnabled: Boolean,
        allowlist: Set<String>
    ): ProposalPolicyDecision {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            return ProposalPolicyDecision(
                canAutoApprove = false,
                baseCommand = "",
                reason = "Comando vacío.",
                isReadOnlySafeInWorkspace = false
            )
        }

        if (SHELL_METACHAR_REGEX.containsMatchIn(trimmed)) {
            return ProposalPolicyDecision(
                canAutoApprove = false,
                baseCommand = trimmed.substringBefore(' ').lowercase(),
                reason = "Bloqueado para auto-aprobación: contiene operadores de shell, encadenamiento o redirección.",
                isReadOnlySafeInWorkspace = false
            )
        }

        val tokens = tokenizeSimple(trimmed)
            ?: return ProposalPolicyDecision(
                canAutoApprove = false,
                baseCommand = trimmed.substringBefore(' ').lowercase(),
                reason = "Bloqueado para auto-aprobación: comillas sin cerrar.",
                isReadOnlySafeInWorkspace = false
            )

        if (tokens.isEmpty()) {
            return ProposalPolicyDecision(false, "", "Comando vacío.", false)
        }

        val rawCmd = tokens[0]
        if (rawCmd.contains('/')) {
            return ProposalPolicyDecision(
                canAutoApprove = false,
                baseCommand = rawCmd.lowercase(),
                reason = "Bloqueado para auto-aprobación: no se permiten ejecutables por ruta directa ('$rawCmd').",
                isReadOnlySafeInWorkspace = false
            )
        }

        val baseCmd = rawCmd.lowercase()
        val args = tokens.drop(1)

        if (baseCmd in NEVER_AUTO_APPROVE_COMMANDS) {
            return ProposalPolicyDecision(
                canAutoApprove = false,
                baseCommand = baseCmd,
                reason = "Bloqueado para auto-aprobación: '$baseCmd' pertenece a una categoría restringida (borrado/instalación/permisos/escritura/red/shell).",
                isReadOnlySafeInWorkspace = false
            )
        }

        // Extra dangerous-flag checks on inspection commands (e.g., find -delete / -exec)
        if (baseCmd == "find" && args.any {
                val lower = it.lowercase()
                lower == "-delete" || lower == "-exec" || lower == "-execdir" ||
                    lower == "-ok" || lower == "-okdir" || lower == "-fprint" ||
                    lower == "-fprintf" || lower == "-fls"
            }
        ) {
            return ProposalPolicyDecision(
                canAutoApprove = false,
                baseCommand = baseCmd,
                reason = "Bloqueado para auto-aprobación: 'find' con -delete o -exec requiere aprobación manual.",
                isReadOnlySafeInWorkspace = false
            )
        }

        val normalizedWorkDir = normalizeWorkingDirectory(workingDir)
        for (arg in args) {
            val pathCheckError = checkArgumentConfinement(arg, normalizedWorkDir)
            if (pathCheckError != null) {
                return ProposalPolicyDecision(
                    canAutoApprove = false,
                    baseCommand = baseCmd,
                    reason = pathCheckError,
                    isReadOnlySafeInWorkspace = false
                )
            }
        }

        val cleanAllowlist = sanitizeAllowlist(allowlist)
        if (baseCmd !in cleanAllowlist) {
            return ProposalPolicyDecision(
                canAutoApprove = false,
                baseCommand = baseCmd,
                reason = if (autoApproveEnabled) {
                    "Requiere aprobación manual: '$baseCmd' no está en tu lista blanca de lectura."
                } else {
                    "Requiere aprobación manual (aprobación automática desactivada)."
                },
                isReadOnlySafeInWorkspace = false
            )
        }

        if (!autoApproveEnabled) {
            return ProposalPolicyDecision(
                canAutoApprove = false,
                baseCommand = baseCmd,
                reason = "Requiere aprobación manual (comando '$baseCmd' de lectura, pero auto-aprobación está desactivada).",
                isReadOnlySafeInWorkspace = true
            )
        }

        return ProposalPolicyDecision(
            canAutoApprove = true,
            baseCommand = baseCmd,
            reason = "Auto-aprobado: comando de lectura '$baseCmd' en lista blanca dentro de $normalizedWorkDir.",
            isReadOnlySafeInWorkspace = true
        )
    }

    private fun checkArgumentConfinement(arg: String, normalizedWorkDir: String): String? {
        if (arg.contains('$')) {
            return "Bloqueado para auto-aprobación: contiene expansión de variable ('$arg')."
        }
        val candidate = if (arg.startsWith("-") && arg.contains('=')) {
            arg.substringAfter('=')
        } else if (arg.startsWith("-") && !arg.startsWith("./") && !arg.startsWith("../")) {
            // Plain short/long flag without path assignment (e.g. -la, --human-readable)
            return null
        } else {
            arg
        }

        if (candidate.isEmpty()) return null
        if (candidate == ".." || candidate.startsWith("../") || candidate.contains("/../") || candidate.endsWith("/..")) {
            return "Bloqueado para auto-aprobación: recorrido de directorio '..' fuera de la carpeta de trabajo ('$candidate')."
        }
        if (candidate.startsWith("~") && candidate != "~" && !candidate.startsWith("~/")) {
            return "Bloqueado para auto-aprobación: referencia a otro home de usuario ('$candidate')."
        }

        val expanded = when {
            candidate == "~" -> TERMUX_HOME_PATH
            candidate.startsWith("~/") -> "$TERMUX_HOME_PATH/${candidate.removePrefix("~/")}"
            candidate.startsWith("/") -> candidate
            else -> "$normalizedWorkDir/$candidate"
        }

        val resolved = normalizePosixPath(expanded)
            ?: return "Bloqueado para auto-aprobación: la ruta '$candidate' escapa de la carpeta de trabajo ($normalizedWorkDir)."

        val insideWorkDir = resolved == normalizedWorkDir || resolved.startsWith("$normalizedWorkDir/")
        if (!insideWorkDir) {
            return "Bloqueado para auto-aprobación: ruta '$candidate' fuera de la carpeta de trabajo ($normalizedWorkDir)."
        }
        return null
    }

    private fun normalizePosixPath(path: String): String? {
        if (!path.startsWith("/")) return null
        val stack = ArrayDeque<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> {}
                ".." -> {
                    if (stack.isEmpty()) return null
                    stack.removeLast()
                }
                else -> stack.addLast(segment)
            }
        }
        return if (stack.isEmpty()) "/" else "/" + stack.joinToString("/")
    }

    private fun tokenizeSimple(input: String): List<String>? {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inSingle = false
        var inDouble = false
        var hasToken = false

        for (ch in input) {
            when {
                ch == '\'' && !inDouble -> {
                    inSingle = !inSingle
                    hasToken = true
                }
                ch == '"' && !inSingle -> {
                    inDouble = !inDouble
                    hasToken = true
                }
                ch.isWhitespace() && !inSingle && !inDouble -> {
                    if (hasToken) {
                        tokens.add(sb.toString())
                        sb.clear()
                        hasToken = false
                    }
                }
                else -> {
                    sb.append(ch)
                    hasToken = true
                }
            }
        }
        if (inSingle || inDouble) return null
        if (hasToken) tokens.add(sb.toString())
        return tokens
    }
}
