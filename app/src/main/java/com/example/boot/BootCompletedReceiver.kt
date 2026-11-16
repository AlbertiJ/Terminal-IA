package com.example.boot

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.agent.ClaudeAgentService
import com.example.agent.OpenClawAgentService
import com.example.data.TerminalPreferences
import com.example.engine.TerminalEngine
import com.example.fs.VirtualFileSystem
import com.example.script.ScriptManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BootExecutionReport(
    val executedCount: Int,
    val blockedCount: Int,
    val summaryLog: String
)

class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        const val BOOT_DIR = "/home/user/.termux/boot"
        const val BOOT_LOG_PATH = "/home/user/.termux/boot/last_boot.log"
        private const val CHANNEL_ID = "termux_ai_boot"
        private const val NOTIFICATION_ID = 8022

        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )

        /**
         * Executes restricted sandbox boot scripts from ~/.termux/boot/ and logs the report.
         * Fully compliant with Android 15/16 BOOT_COMPLETED restrictions (no restricted FGS started here).
         */
        suspend fun executeBootSequence(context: Context): BootExecutionReport {
            val prefs = TerminalPreferences(context)
            val settings = prefs.getSettings()
            if (!settings.autoStartOnBoot) {
                return BootExecutionReport(0, 0, "Auto-arranque desactivado por el usuario.")
            }

            val vfs = VirtualFileSystem(context)
            val engine = TerminalEngine(
                context = context,
                vfs = vfs,
                claudeService = ClaudeAgentService(),
                openClawService = OpenClawAgentService(),
                preferences = prefs
            )
            val scriptManager = ScriptManager(context, vfs)

            var executed = 0
            var blocked = 0
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val logBuilder = StringBuilder()
            logBuilder.append("=== Termux AI Boot Log (${sdf.format(Date())}) ===\n")

            if (settings.runBootScriptsOnBoot) {
                val entries = vfs.listDirectory(BOOT_DIR).getOrDefault(emptyList())
                    .filter { !it.isDirectory && it.name.endsWith(".sh") }
                    .sortedBy { it.name }

                if (entries.isEmpty()) {
                    logBuilder.append("Sin scripts .sh en $BOOT_DIR\n")
                } else {
                    for (entry in entries) {
                        val path = "$BOOT_DIR/${entry.name}"
                        val check = scriptManager.validateScriptExecution(path)
                        if (!check.allowed) {
                            blocked++
                            logBuilder.append("[BLOQUEADO] $path -> ${check.reason}\n")
                            continue
                        }

                        val content = vfs.readFile(path).getOrDefault("")
                        logBuilder.append("[EJECUTANDO] $path\n")
                        for (rawLine in content.lines()) {
                            val clean = rawLine.trim()
                            if (clean.isEmpty() || clean.startsWith("#")) continue
                            engine.executeCommand(
                                input = clean,
                                currentDir = "/home/user",
                                settings = settings,
                                onDirectoryChange = {},
                                onOpenEditor = { _, _ -> },
                                onOutputLine = { out ->
                                    logBuilder.append("  > ${out.text}\n")
                                }
                            )
                        }
                        executed++
                    }
                }
            } else {
                logBuilder.append("Ejecución de scripts al arranque desactivada.\n")
            }

            if (settings.autoStartRemoteServersOnBoot) {
                logBuilder.append("[CONFIG] Inicio automático de servidores remotos habilitado al abrir consola.\n")
            }

            val finalLog = logBuilder.toString().trimEnd()
            vfs.writeFile(BOOT_LOG_PATH, finalLog)
            return BootExecutionReport(executed, blocked, finalLog)
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in SUPPORTED_ACTIONS) return

        val prefs = TerminalPreferences(context)
        val settings = prefs.getSettings()
        if (!settings.autoStartOnBoot) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val report = executeBootSequence(context)
                postBootNotification(context, report, settings.autoStartRemoteServersOnBoot)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postBootNotification(
        context: Context,
        report: BootExecutionReport,
        autoStartServers: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.boot_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.boot_notification_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val detail = buildString {
            append("Scripts ejecutados: ${report.executedCount}")
            if (report.blockedCount > 0) append(" | Bloqueados: ${report.blockedCount}")
            if (autoStartServers) append(" • Toca para activar servidores :8022/:8080")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.boot_notification_title))
            .setContentText(detail)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }
}
