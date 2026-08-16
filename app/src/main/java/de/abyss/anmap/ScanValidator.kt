package de.abyss.anmap

object ScanValidator {
    private val targetCharacters = Regex("^[A-Za-z0-9._:\\[\\]/-]+$")
    private val portSegment = Regex("^(\\d{1,5})(?:-(\\d{1,5}))?$")

    fun normalizeTarget(rawTarget: String): String {
        val target = rawTarget.trim()
        require(target.isNotEmpty()) { "Bitte ein Ziel eingeben." }
        require(target.length <= 253) { "Das Ziel ist zu lang." }
        require(!target.startsWith('-')) { "Ein Ziel darf nicht mit einem Bindestrich beginnen." }
        require(target.matches(targetCharacters)) {
            "Erlaubt sind Hostname, IPv4/IPv6-Adresse oder ein einzelnes CIDR-Netz."
        }

        val slash = target.indexOf('/')
        if (slash >= 0) {
            require(slash == target.lastIndexOf('/')) { "Das Ziel darf nur eine CIDR-Präfixlänge enthalten." }
            val prefix = target.substring(slash + 1).toIntOrNull()
            require(prefix != null && prefix in 0..128) { "Ungültige CIDR-Präfixlänge." }
        }

        return target
    }

    fun normalizePorts(rawPorts: String): String? {
        val ports = rawPorts.trim()
        if (ports.isEmpty()) return null

        ports.split(',').forEach { segment ->
            val match = portSegment.matchEntire(segment)
                ?: throw IllegalArgumentException("Ungültige Portliste: $ports")
            val first = match.groupValues[1].toInt()
            val second = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: first
            require(first in 1..65535 && second in 1..65535 && first <= second) {
                "Ports müssen zwischen 1 und 65535 liegen."
            }
        }

        return ports
    }

    fun normalizeNseSelector(rawSelector: String): String {
        val selector = rawSelector.trim()
        require(selector.isNotEmpty()) { "Bitte einen NSE-Scriptnamen oder eine Kategorie angeben." }
        require(selector.length <= 1024) { "Die NSE-Auswahl ist zu lang." }
        require(selector.none { it.isISOControl() }) { "Die NSE-Auswahl enthält ungültige Steuerzeichen." }
        return selector
    }

    fun normalizeNseArguments(rawArguments: String): String? {
        val arguments = rawArguments.trim()
        if (arguments.isEmpty()) return null
        require(arguments.length <= 4096) { "Die NSE-Argumente sind zu lang." }
        require(arguments.none { it.isISOControl() }) { "Die NSE-Argumente enthalten ungültige Steuerzeichen." }
        return arguments
    }
}
