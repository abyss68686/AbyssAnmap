package de.abyss.anmap

import android.content.Context
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

/** Starts the NDK-built Nmap binary and streams its merged stdout/stderr. */
class NmapRunner(context: Context) {
    private val appContext = context.applicationContext

    @Volatile
    private var activeProcess: Process? = null

    fun execute(request: ScanRequest, onOutput: (String) -> Unit): ScanResult {
        val dataDirectory = DataInstaller(appContext).ensureInstalled(onOutput)
        val binary = File(appContext.applicationInfo.nativeLibraryDir, "libnmap.so")
        check(binary.isFile) {
            "Die native Nmap-Komponente fehlt. Die APK muss nach native/build-nmap.sh gebaut werden."
        }
        val nativeLibraryDir = requireNotNull(binary.parentFile)
        val cxxRuntime = File(nativeLibraryDir, "libc++_shared.so")
        check(cxxRuntime.isFile) {
            "Die NDK-C++-Runtime fehlt. Bitte die aktuelle Abyss-Anmap-Version installieren."
        }
        binary.setExecutable(true, false)

        val command = buildCommand(binary, dataDirectory, request)
        onOutput("Befehl: ${renderCommand(command)}\n\n")

        val process = try {
            ProcessBuilder(command)
                .directory(appContext.filesDir)
                .redirectErrorStream(true)
                .apply {
                    environment()["NMAPDIR"] = dataDirectory.absolutePath
                    environment()["HOME"] = appContext.filesDir.absolutePath
                    environment()["LD_LIBRARY_PATH"] = listOf(
                        nativeLibraryDir.absolutePath,
                        environment()["LD_LIBRARY_PATH"]
                    )
                        .filterNotNull()
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(File.pathSeparator)
                }
                .start()
        } catch (error: IOException) {
            throw IllegalStateException(
                "Nmap konnte auf diesem Gerät nicht gestartet werden: ${error.message}",
                error
            )
        }

        activeProcess = process
        val started = System.nanoTime()
        try {
            process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.forEach { line -> onOutput("$line\n") }
            }
            val exitCode = process.waitFor()
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000
            return ScanResult(exitCode, command, elapsedMillis)
        } finally {
            if (process.isAlive) process.destroyForcibly()
            if (activeProcess === process) activeProcess = null
        }
    }

    fun cancel() {
        activeProcess?.takeIf { it.isAlive }?.destroy()
    }

    private fun buildCommand(binary: File, dataDirectory: File, request: ScanRequest): List<String> {
        val command = mutableListOf(
            binary.absolutePath,
            "--datadir", dataDirectory.absolutePath,
            "--unprivileged",
            "-Pn",
            "-sT",
            "-T3",
            "--max-retries", "2",
            "--host-timeout", "5m",
            "--stats-every", "5s"
        )

        if (!request.resolveNames) command += "-n"

        if (request.profile.enablesVersionDetection) {
            command += listOf("-sV", "--version-light")
        }

        if (request.ports != null) {
            command += listOf("-p", request.ports)
        } else {
            command += listOf("--top-ports", "100")
        }

        when (request.profile) {
            ScanProfile.VULSCAN -> {
                command += listOf(
                    "--script",
                    File(dataDirectory, "scripts/vulscan/vulscan.nse").absolutePath,
                    "--script-args",
                    "vulscanoutput=details"
                )
            }

            ScanProfile.NSE_CUSTOM -> {
                val selector = requireNotNull(request.nseSelector)
                command += listOf("--script", resolveNseSelector(selector, dataDirectory))
                request.nseArguments?.let { arguments ->
                    command += listOf("--script-args", arguments)
                }
            }

            else -> Unit
        }

        command += request.target
        return command
    }

    private fun resolveNseSelector(selector: String, dataDirectory: File): String = when (selector.trim()) {
        "vulscan", "vulscan/vulscan.nse" -> File(dataDirectory, "scripts/vulscan/vulscan.nse").absolutePath
        else -> selector
    }

    private fun renderCommand(command: List<String>): String = command.joinToString(" ") { argument ->
        if (argument.any { it.isWhitespace() }) "\"$argument\"" else argument
    }
}
