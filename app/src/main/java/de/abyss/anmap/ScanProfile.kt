package de.abyss.anmap

enum class ScanProfile(
    val label: String,
    val description: String,
    val enablesVersionDetection: Boolean,
    val needsNseSelector: Boolean,
    val enablesVulscan: Boolean
) {
    FAST_TCP(
        label = "Schnell – TCP Connect",
        description = "Unprivilegierter TCP-Connect-Scan der 100 häufigsten Ports.",
        enablesVersionDetection = false,
        needsNseSelector = false,
        enablesVulscan = false
    ),
    SERVICE_AUDIT(
        label = "Service-Audit",
        description = "TCP-Connect-Scan mit leichter Service- und Versionserkennung.",
        enablesVersionDetection = true,
        needsNseSelector = false,
        enablesVulscan = false
    ),
    VULSCAN(
        label = "Vulscan – offline",
        description = "Service-Erkennung mit dem lokal gebündelten Vulscan-Skript und CSV-Datenbanken.",
        enablesVersionDetection = true,
        needsNseSelector = false,
        enablesVulscan = true
    ),
    NSE_CUSTOM(
        label = "NSE – freie Auswahl",
        description = "Führt jeden gebündelten Nmap-NSE-Scriptnamen oder jede Nmap-Kategorie aus.",
        enablesVersionDetection = true,
        needsNseSelector = true,
        enablesVulscan = false
    )
}

data class ScanRequest(
    val target: String,
    val ports: String?,
    val profile: ScanProfile,
    val nseSelector: String?,
    val nseArguments: String?,
    val resolveNames: Boolean
)

data class ScanResult(
    val exitCode: Int,
    val command: List<String>,
    val elapsedMillis: Long
)
