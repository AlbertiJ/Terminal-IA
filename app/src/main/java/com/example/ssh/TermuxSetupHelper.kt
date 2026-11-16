package com.example.ssh

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

object TermuxSetupHelper {

    const val TERMUX_PACKAGE_NAME = "com.termux"
    const val TERMUX_FDROID_URL = "https://f-droid.org/packages/com.termux/"
    const val TERMUX_GITHUB_RELEASES_URL = "https://github.com/termux/termux-app/releases"
    const val DEFAULT_SSH_PORT = 8022

    private val VALID_ED25519_PUBLIC_KEY_REGEX =
        Regex("^ssh-ed25519\\s+[A-Za-z0-9+/=]+(\\s+[A-Za-z0-9._@-]+)?$")

    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(TERMUX_PACKAGE_NAME, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(TERMUX_PACKAGE_NAME, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun openTermuxDownloadPage(context: Context, url: String = TERMUX_FDROID_URL) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun launchTermuxApp(context: Context): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE_NAME)
            ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }

    /**
     * Generates a single, idempotent shell command to paste into Termux.
     * - Installs openssh if missing
     * - Ensures ~/.ssh (700) and ~/.ssh/authorized_keys (600) without duplicate keys
     * - Configures sshd_config with ListenAddress 127.0.0.1, PasswordAuthentication no, PubkeyAuthentication yes, and Port
     * - Generates host keys if needed and starts/restarts sshd
     * Contains ONLY the public key (never private keys or passwords).
     */
    fun buildSetupCommand(publicKeyOpenSsh: String, sshPort: Int = DEFAULT_SSH_PORT): String {
        val trimmedKey = publicKeyOpenSsh.trim()
        require(VALID_ED25519_PUBLIC_KEY_REGEX.matches(trimmedKey)) {
            "Clave pública Ed25519 inválida para Termux."
        }
        require(sshPort in 1024..65535) {
            "Puerto SSH fuera de rango permitido sin root (1024..65535): $sshPort"
        }

        // Single-quoted safely after strict regex validation above
        val safeKey = trimmedKey.replace("'", "'\\''")

        return listOf(
            "command -v sshd >/dev/null 2>&1 || (pkg update -y && pkg install -y openssh)",
            "mkdir -p ~/.ssh && chmod 700 ~/.ssh",
            "touch ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys",
            "(grep -qxF '$safeKey' ~/.ssh/authorized_keys || printf '%s\\n' '$safeKey' >> ~/.ssh/authorized_keys)",
            "CFG=\"\$PREFIX/etc/ssh/sshd_config\" && mkdir -p \"\$PREFIX/etc/ssh\" && touch \"\$CFG\" && (grep -vE '^[[:space:]]*#?[[:space:]]*(Port|ListenAddress|PasswordAuthentication|PubkeyAuthentication)([[:space:]]|$)' \"\$CFG\" > \"\$CFG.tmp\" || true) && printf 'Port $sshPort\\nListenAddress 127.0.0.1\\nPasswordAuthentication no\\nPubkeyAuthentication yes\\n' >> \"\$CFG.tmp\" && mv \"\$CFG.tmp\" \"\$CFG\"",
            "ssh-keygen -A >/dev/null 2>&1 || true",
            "(pkill -x sshd >/dev/null 2>&1 || true) && sshd && echo 'Terminal-IA SSH listo en 127.0.0.1:$sshPort'"
        ).joinToString(" && ")
    }
}
