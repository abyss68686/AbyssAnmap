package de.abyss.anmap

import android.content.Context
import java.io.File
import java.io.FileNotFoundException

/** Copies immutable scanner data from the APK to private app storage once. */
class DataInstaller(private val context: Context) {
    fun ensureInstalled(report: (String) -> Unit): File {
        val sourceVersion = readAssetText("nmap-data/asset-version.txt").trim()
        val target = File(context.filesDir, "nmap-data")
        val installedMarker = File(target, "asset-version.txt")

        if (installedMarker.isFile && installedMarker.readText().trim() == sourceVersion) {
            return target
        }

        report("Entpacke Nmap-Daten, NSE-Katalog und Vulscan-Datenbanken …\n")
        val staging = File(context.filesDir, "nmap-data.installing")
        deleteTree(staging)
        check(staging.mkdirs() || staging.isDirectory) { "Temporäres Datenverzeichnis konnte nicht angelegt werden." }

        copyAssetTree("nmap-data", staging)
        check(File(staging, "asset-version.txt").readText().trim() == sourceVersion) {
            "Die eingebetteten Scanner-Daten sind unvollständig."
        }

        deleteTree(target)
        check(staging.renameTo(target)) { "Scanner-Daten konnten nicht aktiviert werden." }
        report("Lokale Daten bereit.\n")
        return target
    }

    private fun copyAssetTree(assetPath: String, destination: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            try {
                context.assets.open(assetPath).use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (error: FileNotFoundException) {
                throw IllegalStateException("APK-Asset fehlt: $assetPath", error)
            }
            return
        }

        check(destination.mkdirs() || destination.isDirectory) {
            "Datenverzeichnis konnte nicht angelegt werden: ${destination.absolutePath}"
        }
        children.forEach { child ->
            copyAssetTree("$assetPath/$child", File(destination, child))
        }
    }

    private fun readAssetText(assetPath: String): String =
        context.assets.open(assetPath).bufferedReader().use { it.readText() }

    private fun deleteTree(path: File) {
        if (!path.exists()) return
        path.listFiles()?.forEach { child ->
            if (child.isDirectory) deleteTree(child) else check(child.delete()) { "Datei konnte nicht entfernt werden: $child" }
        }
        check(path.delete()) { "Verzeichnis konnte nicht entfernt werden: $path" }
    }
}

