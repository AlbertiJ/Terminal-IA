package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AgentSettings
import com.example.model.TerminalThemePreset
import com.example.security.SecurityManager

class TerminalPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("termux_ai_prefs", Context.MODE_PRIVATE)
    private val securityManager = SecurityManager(context)

    companion object {
        private const val KEY_CLAUDE_API_KEY_ENC = "claude_api_key_enc"
        private const val KEY_CLAUDE_MODEL = "claude_model"
        private const val KEY_CLAUDE_SYSTEM_PROMPT = "claude_system_prompt"
        private const val KEY_OPENCLAW_ENDPOINT = "openclaw_endpoint"
        private const val KEY_OPENCLAW_TOKEN_ENC = "openclaw_token_enc"
        private const val KEY_OPENCLAW_AUTONOMOUS = "openclaw_autonomous"
        private const val KEY_THEME = "terminal_theme"
        private const val KEY_FONT_SIZE = "font_size_sp"
        private const val KEY_VIBRATION = "enable_vibration"
        private const val KEY_AUTO_START_BOOT = "auto_start_on_boot"
        private const val KEY_AUTO_START_REMOTE = "auto_start_remote_on_boot"
        private const val KEY_RUN_BOOT_SCRIPTS = "run_boot_scripts_on_boot"
        private const val KEY_HISTORY = "command_history"

        // Web Terminal Credentials (Fase 2 hardening)
        private const val KEY_REMOTE_USER = "remote_user"
        private const val KEY_REMOTE_PASS_HASH = "remote_pass_hash"
        private const val KEY_WEB_ENABLED = "web_terminal_enabled"

        // Termux Real SSH Connection Settings (Fase 1)
        private const val KEY_TERMUX_SSH_PORT = "termux_ssh_port"
        private const val KEY_TERMUX_SSH_USER = "termux_ssh_user"
        private const val KEY_TERMINAL_MODE = "terminal_app_mode"
    }

    fun getSecurityManager(): SecurityManager = securityManager

    fun getSettings(): AgentSettings {
        val themeStr = prefs.getString(KEY_THEME, TerminalThemePreset.HACKER_GREEN.name)
        val theme = try {
            TerminalThemePreset.valueOf(themeStr ?: TerminalThemePreset.HACKER_GREEN.name)
        } catch (e: Exception) {
            TerminalThemePreset.HACKER_GREEN
        }

        val encClaudeKey = prefs.getString(KEY_CLAUDE_API_KEY_ENC, "") ?: ""
        val encOpenClawToken = prefs.getString(KEY_OPENCLAW_TOKEN_ENC, "") ?: ""

        return AgentSettings(
            claudeApiKey = securityManager.decryptSecret(encClaudeKey),
            claudeModel = prefs.getString(KEY_CLAUDE_MODEL, "claude-3-5-sonnet-20241022") ?: "claude-3-5-sonnet-20241022",
            claudeSystemPrompt = prefs.getString(
                KEY_CLAUDE_SYSTEM_PROMPT,
                "You are Claude, an expert AI programming and Linux system assistant operating directly within a Termux Android terminal console."
            ) ?: "",
            openClawEndpoint = prefs.getString(KEY_OPENCLAW_ENDPOINT, "https://api.openclaw.io/v1") ?: "https://api.openclaw.io/v1",
            openClawToken = securityManager.decryptSecret(encOpenClawToken),
            openClawAutonomous = prefs.getBoolean(KEY_OPENCLAW_AUTONOMOUS, true),
            terminalTheme = theme,
            fontSizeSp = prefs.getInt(KEY_FONT_SIZE, 13),
            enableVibration = prefs.getBoolean(KEY_VIBRATION, true),
            autoStartOnBoot = prefs.getBoolean(KEY_AUTO_START_BOOT, false),
            autoStartRemoteServersOnBoot = prefs.getBoolean(KEY_AUTO_START_REMOTE, false),
            runBootScriptsOnBoot = prefs.getBoolean(KEY_RUN_BOOT_SCRIPTS, true)
        )
    }

    fun saveSettings(settings: AgentSettings) {
        val encClaudeKey = securityManager.encryptSecret(settings.claudeApiKey)
        val encOpenClawToken = securityManager.encryptSecret(settings.openClawToken)

        prefs.edit().apply {
            putString(KEY_CLAUDE_API_KEY_ENC, encClaudeKey)
            putString(KEY_CLAUDE_MODEL, settings.claudeModel)
            putString(KEY_CLAUDE_SYSTEM_PROMPT, settings.claudeSystemPrompt)
            putString(KEY_OPENCLAW_ENDPOINT, settings.openClawEndpoint)
            putString(KEY_OPENCLAW_TOKEN_ENC, encOpenClawToken)
            putBoolean(KEY_OPENCLAW_AUTONOMOUS, settings.openClawAutonomous)
            putString(KEY_THEME, settings.terminalTheme.name)
            putInt(KEY_FONT_SIZE, settings.fontSizeSp)
            putBoolean(KEY_VIBRATION, settings.enableVibration)
            putBoolean(KEY_AUTO_START_BOOT, settings.autoStartOnBoot)
            putBoolean(KEY_AUTO_START_REMOTE, settings.autoStartRemoteServersOnBoot)
            putBoolean(KEY_RUN_BOOT_SCRIPTS, settings.runBootScriptsOnBoot)
            apply()
        }
    }

    // --- REMOTE ACCESS CREDENTIALS (SSH / TELNET) ---

    fun hasRemoteCredentials(): Boolean {
        val passHash = prefs.getString(KEY_REMOTE_PASS_HASH, "") ?: ""
        return passHash.isNotBlank()
    }

    fun getRemoteUsername(): String {
        return prefs.getString(KEY_REMOTE_USER, "user") ?: "user"
    }

    fun setRemoteCredentials(username: String, plainPassword: String) {
        val trimmedUser = username.trim().ifBlank { "user" }
        val saltedHash = securityManager.hashPassword(plainPassword)
        prefs.edit().apply {
            putString(KEY_REMOTE_USER, trimmedUser)
            putString(KEY_REMOTE_PASS_HASH, saltedHash)
            apply()
        }
    }

    fun verifyRemoteCredentials(username: String, plainPassword: String): Boolean {
        val storedUser = getRemoteUsername()
        val storedHash = prefs.getString(KEY_REMOTE_PASS_HASH, "") ?: ""
        if (storedHash.isBlank()) return false
        if (!username.trim().equals(storedUser, ignoreCase = false)) return false
        return securityManager.verifyPassword(plainPassword, storedHash)
    }

    fun getTermuxSshPort(): Int = prefs.getInt(KEY_TERMUX_SSH_PORT, 8022)

    fun setTermuxSshPort(port: Int) {
        if (port in 1024..65535) {
            prefs.edit().putInt(KEY_TERMUX_SSH_PORT, port).apply()
        }
    }

    fun getTermuxSshUser(): String = prefs.getString(KEY_TERMUX_SSH_USER, "termux") ?: "termux"

    fun setTermuxSshUser(user: String) {
        prefs.edit().putString(KEY_TERMUX_SSH_USER, user.trim().ifBlank { "termux" }).apply()
    }

    fun isPracticeModePreferred(): Boolean =
        prefs.getString(KEY_TERMINAL_MODE, "REAL") == "PRACTICE"

    fun setPracticeModePreferred(practice: Boolean) {
        prefs.edit().putString(KEY_TERMINAL_MODE, if (practice) "PRACTICE" else "REAL").apply()
    }

    fun isWebServerEnabled(): Boolean = prefs.getBoolean(KEY_WEB_ENABLED, false)

    fun setWebServerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEB_ENABLED, enabled).apply()
    }

    // --- COMMAND HISTORY ---

    fun getCommandHistory(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("\n\u0000\n").filter { it.isNotBlank() }
    }

    fun appendCommandToHistory(command: String) {
        if (command.isBlank()) return
        val current = getCommandHistory().toMutableList()
        current.remove(command)
        current.add(command)
        val trimmed = if (current.size > 200) current.takeLast(200) else current
        prefs.edit().putString(KEY_HISTORY, trimmed.joinToString("\n\u0000\n")).apply()
    }

    fun clearCommandHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }
}
