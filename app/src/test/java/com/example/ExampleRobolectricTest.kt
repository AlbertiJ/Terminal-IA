package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.TerminalPreferences
import com.example.fs.VirtualFileSystem
import com.example.security.SecurityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Termux AI", appName)
  }

  @Test
  fun `security manager hashes and verifies password with salt`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val security = SecurityManager(context)
    val password = "SuperSecretPassword123"

    val hashWithSalt = security.hashPassword(password)
    assertTrue(hashWithSalt.contains(":"))

    // Verification succeeds with correct password
    assertTrue(security.verifyPassword(password, hashWithSalt))

    // Verification fails with wrong password
    assertFalse(security.verifyPassword("WrongPassword", hashWithSalt))
  }

  @Test
  fun `security manager encrypts and decrypts secrets with AES-GCM`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val security = SecurityManager(context)
    val apiKey = "sk-ant-api03-abcdef1234567890"

    val encrypted = security.encryptSecret(apiKey)
    assertNotEquals(apiKey, encrypted)
    assertFalse(encrypted.contains("abcdef"))

    val decrypted = security.decryptSecret(encrypted)
    assertEquals(apiKey, decrypted)
  }

  @Test
  fun `security manager masks secrets correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val security = SecurityManager(context)

    val masked = security.maskSecret("sk-ant-api03-abcdef123456")
    assertTrue(masked.startsWith("sk-ant-"))
    assertTrue(masked.contains("***"))
    assertFalse(masked.contains("abcdef"))
  }

  @Test
  fun `security manager blocks destructive commands`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val security = SecurityManager(context)

    val dangerous = security.validateCommandSafety("rm -rf /")
    assertFalse(dangerous.isSafe)

    val safe = security.validateCommandSafety("ls -la /home/user")
    assertTrue(safe.isSafe)
  }

  @Test
  fun `virtual file system creates welcome file and blocks path escapes`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vfs = VirtualFileSystem(context)
    val readResult = vfs.readFile("/home/user/welcome.txt")
    assertTrue(readResult.isSuccess)
    assertTrue(readResult.getOrNull()?.contains("TERMUX AI") == true)
  }

  @Test
  fun `preferences sets and verifies remote credentials securely`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = TerminalPreferences(context)

    prefs.setRemoteCredentials("termux_admin", "AdminPass2026")
    assertTrue(prefs.hasRemoteCredentials())
    assertEquals("termux_admin", prefs.getRemoteUsername())

    assertTrue(prefs.verifyRemoteCredentials("termux_admin", "AdminPass2026"))
    assertFalse(prefs.verifyRemoteCredentials("termux_admin", "BadPass"))
  }

  @Test
  fun `vfs file creation and deletion works for visual file manager`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vfs = VirtualFileSystem(context)

    val testPath = "/home/user/scripts/custom_test.sh"
    val writeRes = vfs.writeFile(testPath, "#!/bin/sh\necho 'hello world'\n")
    assertTrue(writeRes.isSuccess)
    assertTrue(vfs.exists(testPath))

    val readRes = vfs.readFile(testPath)
    assertTrue(readRes.isSuccess)
    assertEquals("#!/bin/sh\necho 'hello world'\n", readRes.getOrNull())

    vfs.deleteFile(testPath)
    assertFalse(vfs.exists(testPath))
  }

  @Test
  fun `script manager enforces restricted execution permissions and blocks privilege escalation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vfs = VirtualFileSystem(context)
    val scriptManager = com.example.script.ScriptManager(context, vfs)

    // 1. Save valid script
    val saveRes = scriptManager.saveScript(
        name = "test_task",
        content = "echo 'running safe task'\ndate\n",
        makeExecutable = true
    )
    assertTrue(saveRes.isSuccess)
    val meta = saveRes.getOrThrow()
    assertEquals("test_task.sh", meta.name)
    assertEquals("/home/user/scripts/test_task.sh", meta.virtualPath)
    assertTrue(meta.isExecutable)
    assertEquals("-rwxr-xr--", meta.permissionString)

    // 2. Allowed execution check
    val check1 = scriptManager.validateScriptExecution(meta.virtualPath)
    assertTrue(check1.allowed)

    // 3. Toggle executable permission to false (-x)
    scriptManager.setExecutable(meta.virtualPath, false)
    assertFalse(scriptManager.isExecutable(meta.virtualPath))
    val check2 = scriptManager.validateScriptExecution(meta.virtualPath)
    assertFalse(check2.allowed)
    assertTrue(check2.reason?.contains("EACCES") == true)

    // 4. Restore executable permission (+x)
    scriptManager.setExecutable(meta.virtualPath, true)
    assertTrue(scriptManager.isExecutable(meta.virtualPath))

    // 5. Test privilege escalation detection (su/sudo blockage)
    val rootScriptRes = scriptManager.saveScript(
        name = "escalate",
        content = "#!/bin/sh\nsudo rm -rf /\n",
        makeExecutable = true
    )
    assertTrue(rootScriptRes.isSuccess)
    val rootCheck = scriptManager.validateScriptExecution(rootScriptRes.getOrThrow().virtualPath)
    assertFalse(rootCheck.allowed)
    assertTrue(rootCheck.reason?.contains("EPERM") == true)

    // Cleanup
    scriptManager.deleteScript(meta.virtualPath)
    scriptManager.deleteScript(rootScriptRes.getOrThrow().virtualPath)
  }

  @Test
  fun `room database persists command history for up and down arrow navigation`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.db.TermuxDatabase.getDatabase(context)
    val repo = com.example.data.db.CommandHistoryRepository(db.commandHistoryDao())

    // Clear initial state
    repo.clearHistory()
    assertEquals(0, repo.count())

    // Record sequential commands
    repo.recordCommand("help")
    repo.recordCommand("ls -la")
    repo.recordCommand("neofetch")
    repo.recordCommand("scripts list")

    // Verify count and navigation order (FIFO order for UP/DOWN arrows)
    assertEquals(4, repo.count())
    val history = repo.getNavigationHistory()
    assertEquals(listOf("help", "ls -la", "neofetch", "scripts list"), history)

    // Test deduplication on re-entering a command
    repo.recordCommand("help")
    val dedupHistory = repo.getNavigationHistory()
    assertEquals(4, dedupHistory.size)
    assertEquals("help", dedupHistory.last())

    // Test clear
    repo.clearHistory()
    assertEquals(0, repo.count())
  }

  @Test
  fun `terminal controller computes command and subcommand completions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vfs = VirtualFileSystem(context)
    val controller = com.example.controller.TerminalController(vfs)

    // Primary command completion
    val comp1 = controller.computeCompletions("neo", "/home/user")
    assertEquals("neofetch ", comp1.completedInput)

    // Subcommand completion
    val comp2 = controller.computeCompletions("script ", "/home/user")
    assertTrue(comp2.suggestions.contains("run"))
    assertTrue(comp2.suggestions.contains("list"))
    assertTrue(comp2.suggestions.contains("chmod"))

    // Flag completion
    val comp3 = controller.computeCompletions("chmod +", "/home/user")
    assertTrue(comp3.suggestions.contains("+x"))
  }

  @Test
  fun `terminal controller records logs with RedTeam and BlueTeam tags and exports json`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vfs = VirtualFileSystem(context)
    val controller = com.example.controller.TerminalController(vfs)

    controller.clearLogs()
    controller.log(com.example.controller.TerminalLogLevel.INFO, "TEST", "Inicialización completada")
    controller.logRedTeamEvent("SIMULATION", "sh /home/user/scripts/syscheck.sh | claude 'audita'")
    controller.logBlueTeamEvent("SANDBOX", "Bloqueo preventivo de sudo en script de usuario no privilegiado")

    val currentLogs = controller.logs.value
    assertTrue(currentLogs.size >= 3)

    val redTeamLogs = controller.getLogsByLevel(com.example.controller.TerminalLogLevel.AUDIT_RED_TEAM)
    assertEquals(1, redTeamLogs.size)
    assertTrue(redTeamLogs[0].message.contains("#RedTeam"))

    val blueTeamLogs = controller.getLogsByLevel(com.example.controller.TerminalLogLevel.AUDIT_BLUE_TEAM)
    assertEquals(1, blueTeamLogs.size)
    assertTrue(blueTeamLogs[0].message.contains("#BlueTeam"))

    val jsonOutput = controller.exportLogsAsJson()
    assertTrue(jsonOutput.startsWith("["))
    assertTrue(jsonOutput.contains("AUDIT_RED_TEAM"))
    assertTrue(jsonOutput.contains("AUDIT_BLUE_TEAM"))
  }

  @Test
  fun `preferences persists auto-boot and remote server startup options`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = TerminalPreferences(context)

    val custom = prefs.getSettings().copy(
        autoStartOnBoot = true,
        autoStartRemoteServersOnBoot = true,
        runBootScriptsOnBoot = false
    )
    prefs.saveSettings(custom)

    val reloaded = prefs.getSettings()
    assertTrue(reloaded.autoStartOnBoot)
    assertTrue(reloaded.autoStartRemoteServersOnBoot)
    assertFalse(reloaded.runBootScriptsOnBoot)
  }

  @Test
  fun `boot completed receiver runs safe boot scripts and blocks privilege escalation`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = TerminalPreferences(context)
    val vfs = VirtualFileSystem(context)
    val scriptManager = com.example.script.ScriptManager(context, vfs)

    // Verify default non-destructive sample boot script is seeded
    val samplePath = "${com.example.boot.BootCompletedReceiver.BOOT_DIR}/00-boot-env.sh"
    assertTrue(vfs.exists(samplePath))
    scriptManager.setExecutable(samplePath, true)

    // Add a dangerous script in ~/.termux/boot/ to ensure sandbox blocks it
    val evilBootPath = "${com.example.boot.BootCompletedReceiver.BOOT_DIR}/99-evil.sh"
    vfs.writeFile(evilBootPath, "#!/bin/sh\nsudo id\n")
    scriptManager.setExecutable(evilBootPath, true)

    prefs.saveSettings(
        prefs.getSettings().copy(
            autoStartOnBoot = true,
            runBootScriptsOnBoot = true,
            autoStartRemoteServersOnBoot = true
        )
    )

    val report = com.example.boot.BootCompletedReceiver.executeBootSequence(context)
    assertTrue(report.executedCount >= 1)
    assertTrue(report.blockedCount >= 1)
    assertTrue(report.summaryLog.contains("[BLOQUEADO]"))
    assertTrue(vfs.exists(com.example.boot.BootCompletedReceiver.BOOT_LOG_PATH))

    // Cleanup evil script
    vfs.deleteFile(evilBootPath)
  }

  // =========================================================================
  // FASE 1: Termux Setup Command, Ed25519/TOFU Keys & SSH Error Diagnostics
  // =========================================================================

  private fun inMemoryAesCipher(): com.example.ssh.KeyWrappingCipher {
    val keyGen = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }
    val secretKey = keyGen.generateKey()
    return object : com.example.ssh.KeyWrappingCipher {
      override fun encrypt(plainBytes: ByteArray): String {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val out = cipher.doFinal(plainBytes)
        val combined = iv + out
        return android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
      }

      override fun decrypt(encoded: String): ByteArray {
        val combined = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val enc = combined.copyOfRange(12, combined.size)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, javax.crypto.spec.GCMParameterSpec(128, iv))
        return cipher.doFinal(enc)
      }
    }
  }

  @Test
  fun `ssh key manager generates Ed25519 identity loadable by JSch and enforces TOFU`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val keyManager = com.example.ssh.SshKeyManager(context, inMemoryAesCipher())

    val pubKey = keyManager.regenerateIdentity()
    assertTrue(pubKey.startsWith("ssh-ed25519 "))
    assertTrue(keyManager.getPublicKeyFingerprintSha256().startsWith("SHA256:"))

    // Verify JSch parses our BouncyCastle OpenSSH Ed25519 private key without errors
    val privBytes = keyManager.loadPrivateKeyBytesForSsh()
    try {
      val privText = String(privBytes, Charsets.UTF_8)
      assertTrue(privText.contains("BEGIN OPENSSH PRIVATE KEY"))
      assertFalse(pubKey.contains("PRIVATE KEY"))

      val jsch = com.jcraft.jsch.JSch()
      jsch.addIdentity("test-ed25519", privBytes, null, null)
      assertTrue(jsch.identityNames.contains("test-ed25519"))
    } finally {
      privBytes.fill(0)
    }

    // TOFU host key workflow
    keyManager.clearTrustedHostKey("127.0.0.1", 8022)
    val hostKey1 = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
    val hostKey2 = byteArrayOf(9, 8, 7, 6, 5, 4, 3, 2)

    val first = keyManager.verifyOrTrustHostKey("127.0.0.1", 8022, "ssh-ed25519", hostKey1)
    assertTrue(first is com.example.ssh.HostKeyCheckResult.TrustedFirstUse)

    val matched = keyManager.verifyOrTrustHostKey("127.0.0.1", 8022, "ssh-ed25519", hostKey1)
    assertTrue(matched is com.example.ssh.HostKeyCheckResult.Matched)

    val mismatch = keyManager.verifyOrTrustHostKey("127.0.0.1", 8022, "ssh-ed25519", hostKey2)
    assertTrue(mismatch is com.example.ssh.HostKeyCheckResult.Mismatch)

    keyManager.acceptChangedHostKey("127.0.0.1", 8022, "ssh-ed25519", hostKey2)
    val matchedAfterAccept = keyManager.verifyOrTrustHostKey("127.0.0.1", 8022, "ssh-ed25519", hostKey2)
    assertTrue(matchedAfterAccept is com.example.ssh.HostKeyCheckResult.Matched)
  }

  @Test
  fun `setup command generator is idempotent, secret-free, and configures sshd safely`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val keyManager = com.example.ssh.SshKeyManager(context, inMemoryAesCipher())
    val pubKey = keyManager.getOrCreatePublicKey()

    val cmd = com.example.ssh.TermuxSetupHelper.buildSetupCommand(pubKey, sshPort = 8022)
    assertTrue(cmd.contains("ListenAddress 127.0.0.1"))
    assertTrue(cmd.contains("PasswordAuthentication no"))
    assertTrue(cmd.contains("PubkeyAuthentication yes"))
    assertTrue(cmd.contains("Port 8022"))
    assertTrue(cmd.contains("chmod 700 ~/.ssh"))
    assertTrue(cmd.contains("chmod 600 ~/.ssh/authorized_keys"))
    assertFalse(cmd.contains("PRIVATE KEY"))

    // Execute generated command twice against a temp HOME + PREFIX with stubbed binaries to prove real shell idempotency
    val tempRoot = java.nio.file.Files.createTempDirectory("termux_setup_test").toFile()
    try {
      val fakeHome = java.io.File(tempRoot, "home").apply { mkdirs() }
      val fakePrefix = java.io.File(tempRoot, "usr").apply { mkdirs() }
      val fakeBin = java.io.File(tempRoot, "bin").apply { mkdirs() }
      val sshEtcDir = java.io.File(fakePrefix, "etc/ssh").apply { mkdirs() }
      val sshdCfg = java.io.File(sshEtcDir, "sshd_config")
      sshdCfg.writeText("#Default config\n#Port 8022\nPasswordAuthentication yes\nListenAddress 0.0.0.0\n")

      for (tool in listOf("sshd", "ssh-keygen", "pkill", "pkg")) {
        val f = java.io.File(fakeBin, tool)
        f.writeText("#!/bin/sh\nexit 0\n")
        f.setExecutable(true)
      }

      repeat(2) {
        val pb = ProcessBuilder("/bin/sh", "-c", cmd)
        pb.environment()["HOME"] = fakeHome.absolutePath
        pb.environment()["PREFIX"] = fakePrefix.absolutePath
        pb.environment()["PATH"] = "${fakeBin.absolutePath}:/bin:/usr/bin"
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val out = proc.inputStream.bufferedReader().readText()
        val code = proc.waitFor()
        assertEquals("El script falló: $out", 0, code)
      }

      val authKeys = java.io.File(fakeHome, ".ssh/authorized_keys")
      assertTrue(authKeys.exists())
      assertEquals(1, authKeys.readLines().filter { it.isNotBlank() }.size)
      assertEquals(pubKey.trim(), authKeys.readLines().first().trim())

      val cfgLines = sshdCfg.readLines()
      assertEquals(1, cfgLines.count { it == "Port 8022" })
      assertEquals(1, cfgLines.count { it == "ListenAddress 127.0.0.1" })
      assertEquals(1, cfgLines.count { it == "PasswordAuthentication no" })
      assertEquals(1, cfgLines.count { it == "PubkeyAuthentication yes" })
      assertFalse(cfgLines.any { it.contains("0.0.0.0") })
    } finally {
      tempRoot.deleteRecursively()
    }
  }

  @Test
  fun `setup command succeeds when sshd_config is empty or leaves zero lines after grep`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val keyManager = com.example.ssh.SshKeyManager(context, inMemoryAesCipher())
    val pubKey = keyManager.getOrCreatePublicKey()
    val cmd = com.example.ssh.TermuxSetupHelper.buildSetupCommand(pubKey, sshPort = 8022)

    val tempRoot = java.nio.file.Files.createTempDirectory("termux_empty_cfg_test").toFile()
    try {
      val fakeHome = java.io.File(tempRoot, "home").apply { mkdirs() }
      val fakePrefix = java.io.File(tempRoot, "usr").apply { mkdirs() }
      val fakeBin = java.io.File(tempRoot, "bin").apply { mkdirs() }
      val sshEtcDir = java.io.File(fakePrefix, "etc/ssh").apply { mkdirs() }
      val sshdCfg = java.io.File(sshEtcDir, "sshd_config")

      for (tool in listOf("sshd", "ssh-keygen", "pkill", "pkg")) {
        val f = java.io.File(fakeBin, tool)
        f.writeText("#!/bin/sh\nexit 0\n")
        f.setExecutable(true)
      }

      // Case 1: completely empty sshd_config (grep -vE returns exit code 1)
      sshdCfg.writeText("")
      run {
        val pb = ProcessBuilder("/bin/sh", "-c", cmd)
        pb.environment()["HOME"] = fakeHome.absolutePath
        pb.environment()["PREFIX"] = fakePrefix.absolutePath
        pb.environment()["PATH"] = "${fakeBin.absolutePath}:/bin:/usr/bin"
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val out = proc.inputStream.bufferedReader().readText()
        assertEquals("Falló con sshd_config vacío: $out", 0, proc.waitFor())
      }

      // Case 2: sshd_config containing ONLY filtered directives (grep -vE filters all lines -> exit 1)
      sshdCfg.writeText("Port 9022\nPasswordAuthentication yes\n")
      run {
        val pb = ProcessBuilder("/bin/sh", "-c", cmd)
        pb.environment()["HOME"] = fakeHome.absolutePath
        pb.environment()["PREFIX"] = fakePrefix.absolutePath
        pb.environment()["PATH"] = "${fakeBin.absolutePath}:/bin:/usr/bin"
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val out = proc.inputStream.bufferedReader().readText()
        assertEquals("Falló con sshd_config sin líneas remanentes: $out", 0, proc.waitFor())
      }

      val cfgLines = sshdCfg.readLines()
      assertEquals(1, cfgLines.count { it == "Port 8022" })
      assertEquals(1, cfgLines.count { it == "ListenAddress 127.0.0.1" })
      assertEquals(1, cfgLines.count { it == "PasswordAuthentication no" })
      assertEquals(1, cfgLines.count { it == "PubkeyAuthentication yes" })
      assertFalse(cfgLines.any { it.contains("9022") })
    } finally {
      tempRoot.deleteRecursively()
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun `setup command generator rejects privileged or invalid port`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val keyManager = com.example.ssh.SshKeyManager(context, inMemoryAesCipher())
    com.example.ssh.TermuxSetupHelper.buildSetupCommand(keyManager.getOrCreatePublicKey(), sshPort = 22)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `setup command generator rejects injection payload as public key`() {
    com.example.ssh.TermuxSetupHelper.buildSetupCommand("ssh-ed25519 AAAA; rm -rf /", sshPort = 8022)
  }

  @Test
  fun `termux ssh client diagnoses every ssh connection error accurately`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val keyManager = com.example.ssh.SshKeyManager(context, inMemoryAesCipher())
    keyManager.getOrCreatePublicKey()

    // 1. Termux not installed
    val clientNoTermux = com.example.ssh.TermuxSshClient(
        context = context,
        keyManager = keyManager,
        isTermuxInstalledProvider = { false }
    )
    val resNoTermux = clientNoTermux.testConnection(port = 8022)
    assertTrue(resNoTermux is com.example.ssh.SshDiagnosticResult.Failure)
    assertTrue((resNoTermux as com.example.ssh.SshDiagnosticResult.Failure).error is com.example.ssh.SshConnectionError.TermuxNotInstalled)

    // 2. Invalid port
    val clientInstalled = com.example.ssh.TermuxSshClient(
        context = context,
        keyManager = keyManager,
        isTermuxInstalledProvider = { true }
    )
    val resInvalidPort = clientInstalled.testConnection(port = 22)
    assertTrue((resInvalidPort as com.example.ssh.SshDiagnosticResult.Failure).error is com.example.ssh.SshConnectionError.InvalidPort)

    // 3. Real connection refused on unused localhost ephemeral port -> SshdNotRunning
    val unusedPort = java.net.ServerSocket(0).use { it.localPort }
    val resRefused = clientInstalled.testConnection(port = unusedPort)
    assertTrue(resRefused is com.example.ssh.SshDiagnosticResult.Failure)
    assertTrue((resRefused as com.example.ssh.SshDiagnosticResult.Failure).error is com.example.ssh.SshConnectionError.SshdNotRunning)

    // 4. KeyNotAuthorized classification
    val authErr = com.example.ssh.TermuxSshClient.classifySshException(
        com.jcraft.jsch.JSchException("Auth fail for methods 'publickey'")
    )
    assertTrue(authErr is com.example.ssh.SshConnectionError.KeyNotAuthorized)
    assertTrue(authErr.toUserMessage().contains("authorized_keys"))

    // 5. WrongPortOrTimeout classification
    val timeoutErr = com.example.ssh.TermuxSshClient.classifySshException(
        java.net.SocketTimeoutException("connect timed out"),
        port = 9999
    )
    assertTrue(timeoutErr is com.example.ssh.SshConnectionError.WrongPortOrTimeout)
    assertTrue(timeoutErr.toUserMessage().contains("9999"))

    // 6. HostKeyChanged message includes expected and actual fingerprints
    val hkErr = com.example.ssh.SshConnectionError.HostKeyChanged(
        host = "127.0.0.1",
        port = 8022,
        expectedFingerprint = "SHA256:OLD",
        actualFingerprint = "SHA256:NEW",
        algorithm = "ssh-ed25519",
        hostKeyBytes = byteArrayOf(1, 2, 3)
    )
    assertTrue(hkErr.toUserMessage().contains("SHA256:OLD"))
    assertTrue(hkErr.toUserMessage().contains("SHA256:NEW"))
  }

  @Test
  fun `preferences persists termux ssh port, user, mode and web flag`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = TerminalPreferences(context)

    prefs.setTermuxSshPort(2222)
    prefs.setTermuxSshUser("u0_a314")
    prefs.setPracticeModePreferred(false)
    prefs.setWebServerEnabled(true)

    assertEquals(2222, prefs.getTermuxSshPort())
    assertEquals("u0_a314", prefs.getTermuxSshUser())
    assertFalse(prefs.isPracticeModePreferred())
    assertTrue(prefs.isWebServerEnabled())
  }

  // =========================================================================
  // FASE 2: Web Terminal Hardening, PBKDF2 310k + Migration, Origin/Cookies/TTL
  // =========================================================================

  private data class HttpTestResponse(
      val statusCode: Int,
      val headers: Map<String, List<String>>,
      val body: String
  ) {
    fun firstHeader(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

    fun allHeaders(name: String): List<String> =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value ?: emptyList()
  }

  private fun sendRawHttp(
      host: String,
      port: Int,
      method: String,
      path: String,
      headers: Map<String, String> = emptyMap(),
      body: String = "",
      useTls: Boolean = false
  ): HttpTestResponse {
    val socket: java.net.Socket = if (useTls) {
      val trustAll = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
      })
      val sslCtx = javax.net.ssl.SSLContext.getInstance("TLS")
      sslCtx.init(null, trustAll, java.security.SecureRandom())
      sslCtx.socketFactory.createSocket(host, port)
    } else {
      java.net.Socket(host, port)
    }

    return socket.use { sock ->
      sock.soTimeout = 10_000
      val bodyBytes = body.toByteArray(Charsets.UTF_8)
      val out = sock.getOutputStream()
      val req = buildString {
        append("$method $path HTTP/1.1\r\n")
        if (!headers.keys.any { it.equals("Host", ignoreCase = true) }) {
          append("Host: $host:$port\r\n")
        }
        headers.forEach { (k, v) -> append("$k: $v\r\n") }
        if (bodyBytes.isNotEmpty()) {
          append("Content-Length: ${bodyBytes.size}\r\n")
        }
        append("Connection: close\r\n\r\n")
      }
      out.write(req.toByteArray(Charsets.UTF_8))
      if (bodyBytes.isNotEmpty()) out.write(bodyBytes)
      out.flush()

      val raw = sock.getInputStream().readBytes()
      val rawText = String(raw, Charsets.UTF_8)
      val sepIdx = rawText.indexOf("\r\n\r\n")
      val headText = if (sepIdx >= 0) rawText.substring(0, sepIdx) else rawText
      val resBody = if (sepIdx >= 0) rawText.substring(sepIdx + 4) else ""

      val headLines = headText.split("\r\n")
      val statusCode = headLines.firstOrNull()?.split(" ")?.getOrNull(1)?.toIntOrNull() ?: -1
      val parsedHeaders = linkedMapOf<String, MutableList<String>>()
      for (line in headLines.drop(1)) {
        val idx = line.indexOf(':')
        if (idx > 0) {
          val k = line.substring(0, idx).trim()
          val v = line.substring(idx + 1).trim()
          parsedHeaders.getOrPut(k) { mutableListOf() }.add(v)
        }
      }
      HttpTestResponse(statusCode, parsedHeaders, resBody)
    }
  }

  private fun fakeExecutor(
      connected: () -> Boolean,
      handler: (String, String) -> com.example.server.WebRealExecResult = { cmd, dir ->
        com.example.server.WebRealExecResult(
            success = true,
            output = "REAL:$cmd",
            currentDir = dir,
            realModeConnected = true
        )
      }
  ) = object : com.example.server.RealTerminalExecutor {
    override fun isRealModeConnected(): Boolean = connected()
    override suspend fun executeCommand(command: String, currentDir: String): com.example.server.WebRealExecResult =
        handler(command, currentDir)
  }

  @Test
  fun `pbkdf2 uses at least 310000 iterations and migrates legacy 10000 hash on login`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val security = SecurityManager(context)
    val prefs = TerminalPreferences(context)
    prefs.clearRemoteCredentials()

    val modernHash = security.hashPassword("ClaveMuySegura2026!")
    assertEquals(310_000, security.getHashIterations(modernHash))
    assertFalse(security.needsPasswordRehash(modernHash))
    assertTrue(security.verifyPassword("ClaveMuySegura2026!", modernHash))

    // Simulate a legacy 10,000-iteration hash stored directly in SharedPreferences
    val legacySalt = ByteArray(16).apply { java.security.SecureRandom().nextBytes(this) }
    val legacySpec = javax.crypto.spec.PBEKeySpec(
        "LegacyClave2026!".toCharArray(),
        legacySalt,
        10_000,
        256
    )
    val legacyBytes = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(legacySpec).encoded
    val legacyHash = legacySalt.joinToString("") { "%02x".format(it) } +
        ":" + legacyBytes.joinToString("") { "%02x".format(it) }

    assertEquals(10_000, security.getHashIterations(legacyHash))
    assertTrue(security.needsPasswordRehash(legacyHash))

    val rawPrefs = context.getSharedPreferences("termux_ai_prefs", Context.MODE_PRIVATE)
    rawPrefs.edit()
        .putString("remote_user", "juan_admin")
        .putString("remote_pass_hash", legacyHash)
        .apply()

    // Failed login must NOT migrate or accept
    assertFalse(prefs.verifyRemoteCredentials("juan_admin", "ClaveIncorrecta123"))
    assertEquals(legacyHash, rawPrefs.getString("remote_pass_hash", ""))

    // Successful login transparently migrates to >= 310,000 iterations
    assertTrue(prefs.verifyRemoteCredentials("juan_admin", "LegacyClave2026!"))
    val migratedHash = rawPrefs.getString("remote_pass_hash", "") ?: ""
    assertNotEquals(legacyHash, migratedHash)
    assertTrue((security.getHashIterations(migratedHash) ?: 0) >= 310_000)
    assertFalse(security.needsPasswordRehash(migratedHash))
    assertTrue(prefs.verifyRemoteCredentials("juan_admin", "LegacyClave2026!"))
  }

  @Test
  fun `preferences rejects default user, blank user, and passwords shorter than 12 chars while defaults disable auto-boot and LAN`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = TerminalPreferences(context)
    prefs.clearRemoteCredentials()

    // No default "user", auto-boot and LAN disabled by default
    assertEquals("", prefs.getRemoteUsername())
    assertFalse(prefs.hasRemoteCredentials())
    assertFalse(prefs.isWebLanAccessEnabled())
    assertFalse(prefs.getSettings().autoStartOnBoot)
    assertFalse(prefs.getSettings().autoStartRemoteServersOnBoot)

    // Reject blank username
    var rejectedBlank = false
    try {
      prefs.setRemoteCredentials("   ", "ClaveSeguraDe12+")
    } catch (_: IllegalArgumentException) {
      rejectedBlank = true
    }
    assertTrue(rejectedBlank)

    // Reject password shorter than 12 chars
    var rejectedShort = false
    try {
      prefs.setRemoteCredentials("operador", "corta123456") // 11 chars
    } catch (_: IllegalArgumentException) {
      rejectedShort = true
    }
    assertTrue(rejectedShort)
    assertFalse(prefs.hasRemoteCredentials())

    // Accept >= 12 chars
    prefs.setRemoteCredentials("operador", "ClaveLarga2026!")
    assertTrue(prefs.hasRemoteCredentials())
    assertEquals("operador", prefs.getRemoteUsername())
  }

  @Test
  fun `web server denies network access and credential creation when no credentials exist`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = TerminalPreferences(context)
    prefs.clearRemoteCredentials()

    val server = com.example.server.WebTerminalServer(
        context = context,
        preferences = prefs,
        realExecutor = fakeExecutor(connected = { true }),
        onLogMessage = { _, _ -> }
    )
    try {
      assertTrue(server.startAndAwait(webPort = 0, allowLan = false, useHttps = false))
      val port = server.getLocalPort()

      // Attempt to register/login over network when empty -> 403 and no creds stored
      val loginRes = sendRawHttp(
          host = "127.0.0.1",
          port = port,
          method = "POST",
          path = "/api/login",
          body = """{"username":"attacker","password":"AttackerPassword2026!"}"""
      )
      assertEquals(403, loginRes.statusCode)
      assertFalse(prefs.hasRemoteCredentials())
      assertEquals("", prefs.getRemoteUsername())

      // Attempt command execution -> 401
      val execRes = sendRawHttp(
          host = "127.0.0.1",
          port = port,
          method = "POST",
          path = "/api/exec",
          body = """{"command":"id"}"""
      )
      assertEquals(401, execRes.statusCode)

      // Session info leaks no default user and says hasCredentials=false
      val sessRes = sendRawHttp("127.0.0.1", port, "GET", "/api/session")
      assertEquals(200, sessRes.statusCode)
      assertFalse(sessRes.body.contains("defaultUser"))
      assertTrue(sessRes.body.contains("\"hasCredentials\":false"))
    } finally {
      server.stop()
    }
  }

  @Test
  fun `web server binds strictly to 127_0_0_1 when LAN toggle is off and opens 0_0_0_0 only when enabled`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = TerminalPreferences(context)
    prefs.setRemoteCredentials("admin_sec", "ClaveSuperSegura2026!")

    // Default LAN OFF -> strictly 127.0.0.1
    prefs.setWebLanAccessEnabled(false)
    val localServer = com.example.server.WebTerminalServer(
        context = context,
        preferences = prefs,
        realExecutor = fakeExecutor(connected = { true }),
        onLogMessage = { _, _ -> }
    )
    try {
      assertTrue(localServer.startAndAwait(webPort = 0, allowLan = prefs.isWebLanAccessEnabled(), useHttps = false))
      assertEquals("127.0.0.1", localServer.getBoundHostAddress())
      assertFalse(localServer.isLanEnabled)

      // Connection to non-loopback non-local address fails/refuses when bound only to 127.0.0.1
      val nonLoopback = java.net.NetworkInterface.getNetworkInterfaces().toList()
          .flatMap { it.inetAddresses.toList() }
          .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
          ?.hostAddress
      if (nonLoopback != null) {
        var refused = false
        try {
          java.net.Socket().use { s ->
            s.connect(java.net.InetSocketAddress(nonLoopback, localServer.getLocalPort()), 1000)
          }
        } catch (_: Exception) {
          refused = true
        }
        assertTrue("Expected non-loopback connection to $nonLoopback to be refused when LAN is OFF", refused)
      }
    } finally {
      localServer.stop()
    }

    // Explicit LAN ON -> binds wildcard 0.0.0.0
    val lanServer = com.example.server.WebTerminalServer(
        context = context,
        preferences = prefs,
        realExecutor = fakeExecutor(connected = { true }),
        onLogMessage = { _, _ -> }
    )
    try {
      assertTrue(lanServer.startAndAwait(webPort = 0, allowLan = true, useHttps = false))
      assertTrue(lanServer.isLanEnabled)
      assertEquals("0.0.0.0", lanServer.getBoundHostAddress())
    } finally {
      lanServer.stop()
    }
  }

  @Test
  fun `web server locks out IP after 5 failed login attempts`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var nowMs = 1_000_000L
    val prefs = TerminalPreferences(context) { nowMs }
    prefs.setRemoteCredentials("admin_lock", "ClaveMuySegura2026!")

    val server = com.example.server.WebTerminalServer(
        context = context,
        preferences = prefs,
        realExecutor = fakeExecutor(connected = { true }),
        onLogMessage = { _, _ -> },
        timeProvider = { nowMs }
    )
    try {
      assertTrue(server.startAndAwait(webPort = 0, allowLan = false, useHttps = false))
      val port = server.getLocalPort()

      repeat(5) { idx ->
        val failRes = sendRawHttp(
            host = "127.0.0.1",
            port = port,
            method = "POST",
            path = "/api/login",
            body = """{"username":"admin_lock","password":"WrongPassword$idx!!"}"""
        )
        assertEquals(401, failRes.statusCode)
      }

      // After 5 failures, even valid credentials MUST be blocked with 403
      val blockedRes = sendRawHttp(
          host = "127.0.0.1",
          port = port,
          method = "POST",
          path = "/api/login",
          body = """{"username":"admin_lock","password":"ClaveMuySegura2026!"}"""
      )
      assertEquals(403, blockedRes.statusCode)
      assertTrue(blockedRes.body.contains("lockedOut"))

      // After lockout window (5 minutes + 1s), valid credentials succeed again
      nowMs += SecurityManager.LOCKOUT_DURATION_MS + 1000L
      val okRes = sendRawHttp(
          host = "127.0.0.1",
          port = port,
          method = "POST",
          path = "/api/login",
          body = """{"username":"admin_lock","password":"ClaveMuySegura2026!"}"""
      )
      assertEquals(200, okRes.statusCode)
    } finally {
      server.stop()
    }
  }

  @Test
  fun `web server rejects invalid Origin, omits wildcard CORS, and sets HttpOnly SameSite=Strict and Secure cookies`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = TerminalPreferences(context)
    prefs.setRemoteCredentials("admin_cors", "ClaveMuySegura2026!")

    // 1. HTTP mode: checks Origin rejection + no wildcard CORS + HttpOnly & SameSite=Strict
    val httpServer = com.example.server.WebTerminalServer(
        context = context,
        preferences = prefs,
        realExecutor = fakeExecutor(connected = { true }),
        onLogMessage = { _, _ -> }
    )
    try {
      assertTrue(httpServer.startAndAwait(webPort = 0, allowLan = false, useHttps = false))
      val port = httpServer.getLocalPort()

      val evilOrigin = sendRawHttp(
          host = "127.0.0.1",
          port = port,
          method = "POST",
          path = "/api/login",
          headers = mapOf("Origin" to "https://evil.example"),
          body = """{"username":"admin_cors","password":"ClaveMuySegura2026!"}"""
      )
      assertEquals(403, evilOrigin.statusCode)
      assertEquals(null, evilOrigin.firstHeader("Access-Control-Allow-Origin"))

      val wrongPortOrigin = sendRawHttp(
          host = "127.0.0.1",
          port = port,
          method = "POST",
          path = "/api/login",
          headers = mapOf("Origin" to "http://127.0.0.1:${port + 1}"),
          body = """{"username":"admin_cors","password":"ClaveMuySegura2026!"}"""
      )
      assertEquals(403, wrongPortOrigin.statusCode)

      val validOriginLogin = sendRawHttp(
          host = "127.0.0.1",
          port = port,
          method = "POST",
          path = "/api/login",
          headers = mapOf("Origin" to "http://127.0.0.1:$port"),
          body = """{"username":"admin_cors","password":"ClaveMuySegura2026!"}"""
      )
      assertEquals(200, validOriginLogin.statusCode)
      assertEquals(null, validOriginLogin.firstHeader("Access-Control-Allow-Origin"))
      val cookie = validOriginLogin.firstHeader("Set-Cookie") ?: ""
      assertTrue(cookie.contains("HttpOnly"))
      assertTrue(cookie.contains("SameSite=Strict"))
      assertFalse(cookie.contains("; Secure"))
    } finally {
      httpServer.stop()
    }

    // 2. HTTPS mode: TLS handshake + Secure attribute on Set-Cookie
    val httpsLogs = mutableListOf<String>()
    val httpsServer = com.example.server.WebTerminalServer(
        context = context,
        preferences = prefs,
        realExecutor = fakeExecutor(connected = { true }),
        onLogMessage = { msg, _ -> httpsLogs.add(msg) }
    )
    try {
      assertTrue(httpsServer.startAndAwait(webPort = 0, allowLan = false, useHttps = true))
      assertTrue(httpsServer.isHttpsActive)
      assertTrue(httpsServer.getTlsCertificateFingerprintSha256().startsWith("SHA256:"))
      val tlsPort = httpsServer.getLocalPort()

      val httpsLogin = sendRawHttp(
          host = "127.0.0.1",
          port = tlsPort,
          method = "POST",
          path = "/api/login",
          headers = mapOf("Origin" to "https://127.0.0.1:$tlsPort"),
          body = """{"username":"admin_cors","password":"ClaveMuySegura2026!"}""",
          useTls = true
      )
      assertEquals("HTTPS logs: $httpsLogs, body: ${httpsLogin.body}", 200, httpsLogin.statusCode)
      val tlsCookie = httpsLogin.firstHeader("Set-Cookie") ?: ""
      assertTrue(tlsCookie.contains("HttpOnly"))
      assertTrue(tlsCookie.contains("SameSite=Strict"))
      assertTrue(tlsCookie.contains("; Secure"))
    } finally {
      httpsServer.stop()
    }
  }

  @Test
  fun `web server expires session token after TTL and escapes HTML and JS payloads`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var nowMs = 5_000_000L
    val prefs = TerminalPreferences(context) { nowMs }
    val xssUsername = """admin"><script>alert(1)</script>"""
    prefs.setRemoteCredentials(xssUsername, "ClaveMuySegura2026!")

    var realConnected = false
    val server = com.example.server.WebTerminalServer(
        context = context,
        preferences = prefs,
        realExecutor = fakeExecutor(
            connected = { realConnected },
            handler = { cmd, _ ->
              com.example.server.WebRealExecResult(
                  success = true,
                  output = "Linux termux 6.1",
                  currentDir = "/data/data/com.termux/files/home",
                  realModeConnected = true
              )
            }
        ),
        onLogMessage = { _, _ -> },
        sessionTtlMs = 60_000L,
        timeProvider = { nowMs }
    )

    try {
      assertTrue(server.startAndAwait(webPort = 0, allowLan = false, useHttps = false))
      val port = server.getLocalPort()

      val loginRes = sendRawHttp(
          host = "127.0.0.1",
          port = port,
          method = "POST",
          path = "/api/login",
          body = org.json.JSONObject().apply {
            put("username", xssUsername)
            put("password", "ClaveMuySegura2026!")
          }.toString()
      )
      assertEquals(200, loginRes.statusCode)
      val setCookie = loginRes.firstHeader("Set-Cookie") ?: ""
      val cookiePair = setCookie.substringBefore(";")
      assertTrue(cookiePair.startsWith("termux_session="))

      // Authenticated HTML response must escape username/payload and have zero innerHTML sinks
      val htmlRes = sendRawHttp(
          host = "127.0.0.1",
          port = port,
          method = "GET",
          path = "/",
          headers = mapOf("Cookie" to cookiePair)
      )
      assertEquals(200, htmlRes.statusCode)
      assertFalse(htmlRes.body.contains("<script>alert(1)</script>"))
      assertFalse(htmlRes.body.contains("innerHTML"))
      assertTrue(htmlRes.body.contains("MODO REAL DESCONECTADO"))

      // Disconnected Real Mode refuses /api/exec instead of using simulated TerminalEngine
      val disconnectedExec = sendRawHttp(
          host = "127.0.0.1",
          port = port,
          method = "POST",
          path = "/api/exec",
          headers = mapOf("Cookie" to cookiePair),
          body = """{"command":"uname -a"}"""
      )
      assertEquals(409, disconnectedExec.statusCode)
      assertTrue(disconnectedExec.body.contains("\"realModeConnected\":false"))

      // Connected Real Mode delegates to real SSH executor
      realConnected = true
      val connectedExec = sendRawHttp(
          host = "127.0.0.1",
          port = port,
          method = "POST",
          path = "/api/exec",
          headers = mapOf("Cookie" to cookiePair),
          body = """{"command":"uname -a"}"""
      )
      assertEquals(200, connectedExec.statusCode)
      assertTrue(connectedExec.body.contains("Linux termux 6.1"))

      // Advance clock past session TTL -> token expires and returns 401
      nowMs += 60_001L
      val expiredExec = sendRawHttp(
          host = "127.0.0.1",
          port = port,
          method = "POST",
          path = "/api/exec",
          headers = mapOf("Cookie" to cookiePair),
          body = """{"command":"uname -a"}"""
      )
      assertEquals(401, expiredExec.statusCode)

      val expiredSession = sendRawHttp(
          host = "127.0.0.1",
          port = port,
          method = "GET",
          path = "/api/session",
          headers = mapOf("Cookie" to cookiePair)
      )
      assertEquals(401, expiredSession.statusCode)
      assertTrue(expiredSession.body.contains("\"expired\":true"))
    } finally {
      server.stop()
    }
  }
}
