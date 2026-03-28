package com.moqaddas.handsoff.data.shizuku

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuCommandRunner @Inject constructor() {

    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && hasPermission()
        } catch (e: Exception) {
            false
        }
    }

    private fun hasPermission(): Boolean {
        return if (Shizuku.isPreV11()) {
            false
        } else {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermission(requestCode: Int) {
        Shizuku.requestPermission(requestCode)
    }

    // SEC-001 fix: each argument is a separate array element passed directly to the process.
    // No shell interpreter (`sh -c`) is involved — metacharacters in packageName, permission,
    // or value are treated as literal data and cannot execute arbitrary shell commands.
    //
    // SEC-002 note: Shizuku.newProcess() is annotated private in the 13.1.5 compiled jar
    // despite being public in the Java spec. Reflection is the only path without a library
    // upgrade. The isAccessible flag is contained here and not used anywhere else.
    // TODO: remove reflection when upgrading Shizuku beyond 13.1.5 (use Shizuku.newProcess directly).
    suspend fun execute(vararg args: String): CommandResult = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext CommandResult(success = false, output = "", error = "Shizuku not available")
        }
        var process: Process? = null
        try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            // Pass args directly — NOT wrapped in ["sh", "-c", joined-string].
            // This is the critical difference that closes the injection surface.
            process = method.invoke(null, args, null, null) as Process
            val output   = process.inputStream.bufferedReader().readText()
            val error    = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            // Never surface raw command output in error messages to the UI layer.
            CommandResult(
                success = exitCode == 0,
                output = output.trim(),
                error = if (exitCode == 0) "" else "Command failed (exit $exitCode)"
            )
        } catch (e: Exception) {
            CommandResult(success = false, output = "", error = "Shizuku command failed")
        } finally {
            process?.destroy()
        }
    }

    // Convenience methods — each token is a separate argument, never concatenated into a
    // shell string. packageName / permission / value cannot inject shell commands.
    suspend fun setSetting(namespace: String, key: String, value: String) =
        execute("settings", "put", namespace, key, value)

    suspend fun getSetting(namespace: String, key: String) =
        execute("settings", "get", namespace, key)

    suspend fun uninstallPackage(packageName: String) =
        execute("pm", "uninstall", "-k", "--user", "0", packageName)

    suspend fun reinstallPackage(packageName: String) =
        execute("pm", "install-existing", "--user", "0", packageName)

    suspend fun disablePackage(packageName: String) =
        execute("pm", "disable-user", "--user", "0", packageName)

    suspend fun enablePackage(packageName: String) =
        execute("pm", "enable", "--user", "0", packageName)

    suspend fun revokePermission(packageName: String, permission: String) =
        execute("pm", "revoke", packageName, permission)

    suspend fun denyAppOp(packageName: String, op: String) =
        execute("cmd", "appops", "set", packageName, op, "deny")

    suspend fun forceStop(packageName: String) =
        execute("am", "force-stop", packageName)

    suspend fun getAlarms() =
        execute("dumpsys", "alarm")

    suspend fun getJobScheduler() =
        execute("dumpsys", "jobscheduler")
}

data class CommandResult(
    val success: Boolean,
    val output: String,
    val error: String
)
