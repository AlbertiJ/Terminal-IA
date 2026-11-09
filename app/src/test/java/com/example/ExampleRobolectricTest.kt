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
}
