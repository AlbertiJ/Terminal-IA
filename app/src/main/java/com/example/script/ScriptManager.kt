package com.example.script

import android.content.Context
import android.content.SharedPreferences
import com.example.fs.VirtualFileSystem
import com.example.model.LineType
import com.example.model.TerminalLine

class ScriptManager(
    private val context: Context,
    private val vfs: VirtualFileSystem
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("termux_scripts_perms", Context.MODE_PRIVATE)

    companion object {
        const val SCRIPTS_DIR = "/home/user/scripts"
        private const val PERM_PREFIX = "perm_exec_"
    }

    init {
        // Ensure default script has execute permission
        val defaultScript = "$SCRIPTS_DIR/syscheck.sh"
        if (!prefs.contains(PERM_PREFIX + defaultScript)) {
            setExecutable(defaultScript, true)
        }
    }

    fun isExecutable(virtualPath: String): Boolean {
        return prefs.getBoolean(PERM_PREFIX + virtualPath, true) // default true for new .sh unless restricted
    }

    fun setExecutable(virtualPath: String, executable: Boolean): Result<Unit> {
        if (!vfs.exists(virtualPath)) {
            return Result.failure(Exception("El archivo no existe: $virtualPath"))
        }
        prefs.edit().putBoolean(PERM_PREFIX + virtualPath, executable).apply()
        return Result.success(Unit)
    }

    fun listScripts(): List<ScriptMetadata> {
        val results = mutableListOf<ScriptMetadata>()

        // 1. Scan /home/user/scripts
        vfs.listDirectory(SCRIPTS_DIR).onSuccess { entries ->
            for (e in entries) {
                if (!e.isDirectory && e.name.endsWith(".sh")) {
                    val path = "$SCRIPTS_DIR/${e.name}"
                    val exec = isExecutable(path)
                    val content = vfs.readFile(path).getOrDefault("")
                    val lineCount = content.lines().count { it.isNotBlank() }

                    results.add(
                        ScriptMetadata(
                            name = e.name,
                            virtualPath = path,
                            isExecutable = exec,
                            sizeBytes = e.size,
                            lastModified = System.currentTimeMillis(),
                            permissionString = if (exec) "-rwxr-xr--" else "-rw-r--r--",
                            restrictedUser = "u0_a234 (unprivileged)",
                            lineCount = lineCount
                        )
                    )
                }
            }
        }

        // 2. Scan /home/user root for scripts
        vfs.listDirectory("/home/user").onSuccess { entries ->
            for (e in entries) {
                if (!e.isDirectory && e.name.endsWith(".sh")) {
                    val path = "/home/user/${e.name}"
                    val exec = isExecutable(path)
                    val content = vfs.readFile(path).getOrDefault("")
                    val lineCount = content.lines().count { it.isNotBlank() }

                    results.add(
                        ScriptMetadata(
                            name = e.name,
                            virtualPath = path,
                            isExecutable = exec,
                            sizeBytes = e.size,
                            lastModified = System.currentTimeMillis(),
                            permissionString = if (exec) "-rwxr-xr--" else "-rw-r--r--",
                            restrictedUser = "u0_a234 (unprivileged)",
                            lineCount = lineCount
                        )
                    )
                }
            }
        }

        return results.distinctBy { it.virtualPath }.sortedBy { it.name }
    }

    fun saveScript(
        name: String,
        content: String,
        makeExecutable: Boolean = true
    ): Result<ScriptMetadata> {
        var cleanName = name.trim()
        if (!cleanName.endsWith(".sh")) {
            cleanName += ".sh"
        }

        // Validate script name against illegal characters
        val illegalChars = Regex("[/\\\\\\s<>:\"|?*]")
        if (illegalChars.containsMatchIn(cleanName)) {
            return Result.failure(Exception("Nombre de script inválido. Usa solo letras, números, guiones y extensión .sh"))
        }

        val targetPath = "$SCRIPTS_DIR/$cleanName"

        // Ensure shebang exists
        val formattedContent = if (!content.trimStart().startsWith("#!")) {
            "#!/bin/sh\n# Termux AI Script: $cleanName\n$content\n"
        } else {
            content
        }

        val writeResult = vfs.writeFile(targetPath, formattedContent)
        return writeResult.map {
            setExecutable(targetPath, makeExecutable)
            ScriptMetadata(
                name = cleanName,
                virtualPath = targetPath,
                isExecutable = makeExecutable,
                sizeBytes = formattedContent.toByteArray().size.toLong(),
                lastModified = System.currentTimeMillis(),
                permissionString = if (makeExecutable) "-rwxr-xr--" else "-rw-r--r--",
                restrictedUser = "u0_a234 (unprivileged)",
                lineCount = formattedContent.lines().count { it.isNotBlank() }
            )
        }
    }

    fun deleteScript(virtualPath: String): Result<Unit> {
        val deleteRes = vfs.deleteFile(virtualPath)
        prefs.edit().remove(PERM_PREFIX + virtualPath).apply()
        return deleteRes
    }

    /**
     * Verifies security checks before executing:
     * 1. Execution permission (+x) check
     * 2. Path sandbox check (must be inside /home/user)
     * 3. Escalation / Privilege attack scan (blocks 'su', 'sudo', 'setuid')
     */
    fun validateScriptExecution(virtualPath: String): ExecutionCheckResult {
        if (!vfs.exists(virtualPath)) {
            return ExecutionCheckResult(
                allowed = false,
                reason = "bash: $virtualPath: No such file or directory"
            )
        }

        if (!virtualPath.startsWith("/home/user")) {
            return ExecutionCheckResult(
                allowed = false,
                reason = "Seguridad EACCES: Solo se permite la ejecución de scripts dentro del sandbox /home/user"
            )
        }

        if (!isExecutable(virtualPath)) {
            return ExecutionCheckResult(
                allowed = false,
                reason = "bash: $virtualPath: Permiso denegado (EACCES). El script carece de permiso de ejecución (+x). Usa: script chmod $virtualPath +x"
            )
        }

        // Scan script content for prohibited root/escalation commands
        val content = vfs.readFile(virtualPath).getOrDefault("")
        val prohibitedPatterns = listOf(
            Regex("(?:^|\\s)(?:sudo|su)(?:\\s|$)"),
            Regex("(?:^|\\s)/system/bin/su"),
            Regex("chmod\\s+4[0-9]{3}"), // SUID bit setting
            Regex("chown\\s+root")
        )

        for (pattern in prohibitedPatterns) {
            if (pattern.containsMatchIn(content)) {
                return ExecutionCheckResult(
                    allowed = false,
                    reason = "Seguridad EPERM: El script contiene comandos de escalada de privilegios restringidos (su/sudo). Ejecución abortada."
                )
            }
        }

        return ExecutionCheckResult(allowed = true, reason = null)
    }

    data class ExecutionCheckResult(val allowed: Boolean, val reason: String?)
}
