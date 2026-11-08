package com.example.fs

import android.content.Context
import java.io.File

class VirtualFileSystem(private val context: Context) {

    private val rootDir: File = File(context.filesDir, "termux_fs")
    private val homeDir: File = File(rootDir, "home/user")

    init {
        initializeFileSystem()
    }

    private fun initializeFileSystem() {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
        val subDirs = listOf(
            "home/user/scripts",
            "home/user/projects",
            "bin",
            "etc",
            "tmp"
        )
        for (sub in subDirs) {
            File(rootDir, sub).mkdirs()
        }

        // Default welcome.txt
        val welcomeFile = File(homeDir, "welcome.txt")
        if (!welcomeFile.exists()) {
            welcomeFile.writeText(
                """
                =======================================================
                   WELCOME TO TERMUX AI CONSOLE (100% FREE & OPEN)
                =======================================================
                
                This terminal provides a full Android shell environment
                supercharged with autonomous AI agent connectivity:
                
                * CLAUDE AGENT (Anthropic):
                  - Command: claude "your query here"
                  - Interactive Mode: claude -i
                  - Config: claude config
                
                * OPENCLAW AUTONOMOUS AGENT:
                  - Command: openclaw "objective"
                  - Workflows: openclaw run syscheck
                  - Status: openclaw status
                
                * BUILT-IN COMMANDS:
                  help, ls, cd, cat, nano, touch, mkdir, rm, curl,
                  ping, neofetch, python, date, whoami, env, pkg
                
                Run 'help' for the complete command reference list.
                =======================================================
                """.trimIndent()
            )
        }

        // Default syscheck.sh
        val syscheckScript = File(File(homeDir, "scripts"), "syscheck.sh")
        if (!syscheckScript.exists()) {
            syscheckScript.writeText(
                """
                #!/bin/sh
                echo "Running Termux AI Diagnostics..."
                date
                whoami
                uname -a
                echo "All agents initialized and ready."
                """.trimIndent()
            )
        }

        // Default sample agent script
        val agentPy = File(File(homeDir, "scripts"), "agent_task.py")
        if (!agentPy.exists()) {
            agentPy.writeText(
                """
                # Termux AI Script
                import sys

                def main():
                    print("Termux AI autonomous task runner initialized.")
                    print("Connecting Claude & OpenClaw agents...")
                    print("Done! Ready for execution.")

                if __name__ == "__main__":
                    main()
                """.trimIndent()
            )
        }
    }

    fun getHomePath(): String = "/home/user"

    fun normalizePath(currentDir: String, target: String): String {
        var cleanTarget = target.trim()
        if (cleanTarget == "~" || cleanTarget.startsWith("~/")) {
            cleanTarget = cleanTarget.replaceFirst("~", "/home/user")
        }

        val parts = if (cleanTarget.startsWith("/")) {
            cleanTarget.split("/").filter { it.isNotEmpty() }
        } else {
            val baseParts = currentDir.split("/").filter { it.isNotEmpty() }
            val relParts = cleanTarget.split("/").filter { it.isNotEmpty() }
            val combined = ArrayList(baseParts)
            for (p in relParts) {
                if (p == ".") continue
                if (p == "..") {
                    if (combined.isNotEmpty()) combined.removeAt(combined.size - 1)
                } else {
                    combined.add(p)
                }
            }
            combined
        }

        val resolved = ArrayList<String>()
        for (p in parts) {
            if (p == ".") continue
            if (p == "..") {
                if (resolved.isNotEmpty()) resolved.removeAt(resolved.size - 1)
            } else {
                resolved.add(p)
            }
        }
        return "/" + resolved.joinToString("/")
    }

    private fun getFileForVirtualPath(virtualPath: String): File {
        val rel = virtualPath.removePrefix("/")
        val target = File(rootDir, rel)
        val canonicalRoot = rootDir.canonicalFile
        val canonicalTarget = target.canonicalFile
        if (!canonicalTarget.path.startsWith(canonicalRoot.path)) {
            throw SecurityException("Acceso denegado: intento de escape de ruta fuera de VFS ($virtualPath)")
        }
        return target
    }

    fun listDirectory(virtualPath: String): Result<List<FileEntry>> {
        val file = getFileForVirtualPath(virtualPath)
        if (!file.exists()) return Result.failure(Exception("No such file or directory: $virtualPath"))
        if (!file.isDirectory) return Result.failure(Exception("Not a directory: $virtualPath"))

        val items = file.listFiles()?.map { f ->
            FileEntry(
                name = f.name,
                isDirectory = f.isDirectory,
                size = f.length(),
                isExecutable = f.name.endsWith(".sh") || f.name.endsWith(".py")
            )
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()

        return Result.success(items)
    }

    fun readFile(virtualPath: String): Result<String> {
        val file = getFileForVirtualPath(virtualPath)
        if (!file.exists()) return Result.failure(Exception("No such file: $virtualPath"))
        if (file.isDirectory) return Result.failure(Exception("Is a directory: $virtualPath"))
        return try {
            Result.success(file.readText())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun writeFile(virtualPath: String, content: String, append: Boolean = false): Result<Unit> {
        val file = getFileForVirtualPath(virtualPath)
        return try {
            file.parentFile?.mkdirs()
            if (append) {
                file.appendText(content)
            } else {
                file.writeText(content)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun makeDirectory(virtualPath: String): Result<Unit> {
        val file = getFileForVirtualPath(virtualPath)
        return if (file.mkdirs()) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Failed to create directory: $virtualPath"))
        }
    }

    fun deleteFile(virtualPath: String): Result<Unit> {
        val file = getFileForVirtualPath(virtualPath)
        if (!file.exists()) return Result.failure(Exception("No such file or directory: $virtualPath"))
        return if (file.deleteRecursively()) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Failed to delete: $virtualPath"))
        }
    }

    fun exists(virtualPath: String): Boolean {
        return getFileForVirtualPath(virtualPath).exists()
    }

    fun isDirectory(virtualPath: String): Boolean {
        return getFileForVirtualPath(virtualPath).isDirectory
    }

    data class FileEntry(
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val isExecutable: Boolean
    )
}
