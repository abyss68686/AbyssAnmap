package de.abyss.anmap

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : Activity() {
    companion object {
        private const val MAX_OUTPUT_CHARS = 500_000
        private const val BACKGROUND = "#090D12"
        private const val SURFACE_ALT = "#1A2430"
        private const val CYAN = "#40E0D0"
        private const val TEXT = "#ECF7F8"
        private const val MUTED = "#A8BBC0"
        private const val WARNING = "#FFCF70"
    }

    private val scanExecutor = Executors.newSingleThreadExecutor()
    private val runner by lazy { NmapRunner(applicationContext) }
    private val outputBuffer = StringBuilder()

    private lateinit var targetInput: EditText
    private lateinit var portsInput: EditText
    private lateinit var nseInput: EditText
    private lateinit var nseArgumentsInput: EditText
    private lateinit var profileSpinner: Spinner
    private lateinit var resolveNamesCheck: CheckBox
    private lateinit var profileDescription: TextView
    private lateinit var nseContainer: LinearLayout
    private lateinit var scanButton: Button
    private lateinit var stopButton: Button
    private lateinit var copyButton: Button
    private lateinit var statusView: TextView
    private lateinit var outputView: TextView
    private lateinit var outputScroll: ScrollView

    private var scanRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)
        setContentView(createScreen())
        appendOutputUi("Abyss Anmap bereit – lokale Nmap- und Vulscan-Daten werden beim ersten Scan eingerichtet.\n")
    }

    override fun onDestroy() {
        runner.cancel()
        scanExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun createScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(BACKGROUND))
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(
            textView("Abyss Anmap", 25f, CYAN, Typeface.BOLD),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        headerRow.addView(button("Info", primary = false).apply { setOnClickListener { showAbout() } })
        root.addView(headerRow, matchWidth())
        root.addView(
            textView("Lokaler Nmap-Client • vollständiger NSE-Katalog • Vulscan offline", 13f, MUTED),
            matchWidth(top = 2, bottom = 10)
        )

        val pageScroll = ScrollView(this).apply { isFillViewport = true }
        root.addView(pageScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pageScroll.addView(page)

        page.addView(sectionTitle("Ziel und Profil"), matchWidth(bottom = 4))
        targetInput = labeledInput(
            page,
            "Ziel",
            "Hostname, IP-Adresse oder einzelnes CIDR-Netz",
            InputType.TYPE_CLASS_TEXT
        )
        portsInput = labeledInput(
            page,
            "Ports (optional)",
            "z. B. 22,80,443 oder 1-1024",
            InputType.TYPE_CLASS_TEXT
        )

        page.addView(label("Profil"), matchWidth(top = 12, bottom = 4))
        profileSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                ScanProfile.entries.map { it.label }
            )
            setSelection(ScanProfile.SERVICE_AUDIT.ordinal)
        }
        page.addView(profileSpinner, matchWidth())
        profileDescription = textView("", 13f, MUTED).apply { setPadding(dp(4), dp(8), dp(4), dp(2)) }
        page.addView(profileDescription, matchWidth())

        nseContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nseInput = labeledInput(
            nseContainer,
            "NSE-Auswahl",
            "z. B. default,safe  |  http-title  |  vuln",
            InputType.TYPE_CLASS_TEXT
        ).apply { setText("default,safe") }
        nseArgumentsInput = labeledInput(
            nseContainer,
            "NSE-Argumente (optional)",
            "z. B. key=value,another=value",
            InputType.TYPE_CLASS_TEXT
        )
        nseContainer.addView(
            textView(
                "Alle 611 offiziellen NSE-Scripts, Kategorien und die Vulscan-Erweiterung liegen lokal bei. " +
                    "Die Auswahl folgt der normalen Nmap- --script-Syntax.",
                12f,
                MUTED
            ),
            matchWidth(top = 2, bottom = 5)
        )
        nseContainer.addView(button("NSE-Katalog anzeigen", primary = false).apply {
            setOnClickListener { showNseCatalog() }
        })
        page.addView(nseContainer, matchWidth(top = 8, bottom = 2))

        resolveNamesCheck = CheckBox(this).apply {
            text = "Reverse DNS-Namen auflösen"
            setTextColor(Color.parseColor(TEXT))
            isChecked = false
        }
        page.addView(resolveNamesCheck, matchWidth(top = 8))
        page.addView(
            textView(
                "Die GUI verwendet -sT und --unprivileged. Sie benötigt keinen Root-Zugriff und fragt keine Raw-Paket-Scans an.",
                12f,
                WARNING
            ),
            matchWidth(top = 1, bottom = 12)
        )

        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scanButton = button("Scan starten", primary = true).apply { setOnClickListener { confirmAndStart() } }
        stopButton = button("Stop", primary = false).apply {
            isEnabled = false
            setOnClickListener {
                runner.cancel()
                appendOutputUi("\nAbbruch angefordert …\n")
                updateStatus("Abbruch wird an Nmap übergeben …", WARNING)
            }
        }
        actionRow.addView(scanButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(8)
        })
        actionRow.addView(stopButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.42f))
        page.addView(actionRow, matchWidth(bottom = 10))

        statusView = textView("Bereit", 13f, CYAN).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(SURFACE_ALT, 8)
        }
        page.addView(statusView, matchWidth(bottom = 12))

        page.addView(sectionTitle("Ausgabe"), matchWidth(bottom = 4))
        outputScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#05080C"))
            isFillViewport = true
        }
        outputView = textView("", 12.5f, TEXT, Typeface.NORMAL).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        outputScroll.addView(outputView)
        page.addView(outputScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)))

        copyButton = button("Ausgabe kopieren", primary = false).apply {
            isEnabled = false
            setOnClickListener { copyOutput() }
        }
        page.addView(copyButton, matchWidth(top = 8, bottom = 12))

        profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                updateProfileUi()
            }

            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
        updateProfileUi()
        return root
    }

    private fun confirmAndStart() {
        val request = try {
            ScanRequest(
                target = ScanValidator.normalizeTarget(targetInput.text.toString()),
                ports = ScanValidator.normalizePorts(portsInput.text.toString()),
                profile = selectedProfile(),
                nseSelector = selectedProfile().takeIf { it.needsNseSelector }
                    ?.let { ScanValidator.normalizeNseSelector(nseInput.text.toString()) },
                nseArguments = selectedProfile().takeIf { it.needsNseSelector }
                    ?.let { ScanValidator.normalizeNseArguments(nseArgumentsInput.text.toString()) },
                resolveNames = resolveNamesCheck.isChecked
            )
        } catch (error: IllegalArgumentException) {
            showMessage("Eingabe prüfen", error.message ?: "Ungültige Eingabe.")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Berechtigung bestätigen")
            .setMessage(
                "Abyss Anmap startet einen lokalen Netzwerk-Scan gegen ${request.target}. " +
                    "Bestätige nur, wenn du das Ziel bzw. das Netz testen darfst."
            )
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Scan starten") { _, _ -> startScan(request) }
            .show()
    }

    private fun startScan(request: ScanRequest) {
        if (scanRunning) return
        outputBuffer.clear()
        outputView.text = ""
        setScanRunning(true)
        updateStatus("Initialisiere lokalen Scanner …", CYAN)

        scanExecutor.execute {
            try {
                appendOutput("Abyss Anmap — ${request.profile.label}\n")
                appendOutput("Ziel: ${request.target}\n\n")
                val result = runner.execute(request, ::appendOutput)
                appendOutput("\nNmap beendet (Exit ${result.exitCode}, ${result.elapsedMillis} ms).\n")
                runOnUiThread {
                    val color = if (result.exitCode == 0) CYAN else WARNING
                    updateStatus("Beendet: Exit ${result.exitCode}", color)
                }
            } catch (error: Exception) {
                appendOutput("\nFehler: ${error.message ?: error.javaClass.simpleName}\n")
                runOnUiThread { updateStatus("Scan konnte nicht ausgeführt werden", WARNING) }
            } finally {
                runOnUiThread { setScanRunning(false) }
            }
        }
    }

    private fun updateProfileUi() {
        val profile = selectedProfile()
        profileDescription.text = profile.description
        nseContainer.visibility = if (profile.needsNseSelector) View.VISIBLE else View.GONE
    }

    private fun selectedProfile(): ScanProfile = ScanProfile.entries[profileSpinner.selectedItemPosition]

    private fun setScanRunning(running: Boolean) {
        scanRunning = running
        scanButton.isEnabled = !running
        stopButton.isEnabled = running
    }

    private fun updateStatus(message: String, color: String) {
        statusView.text = message
        statusView.setTextColor(Color.parseColor(color))
    }

    private fun appendOutput(text: String) {
        runOnUiThread { appendOutputUi(text) }
    }

    private fun appendOutputUi(text: String) {
        outputBuffer.append(text)
        if (outputBuffer.length > MAX_OUTPUT_CHARS) {
            outputBuffer.delete(0, outputBuffer.length - MAX_OUTPUT_CHARS)
            outputBuffer.insert(0, "[Ältere Ausgabe wurde gekürzt.]\n")
        }
        outputView.text = outputBuffer
        copyButton.isEnabled = outputBuffer.isNotEmpty()
        outputScroll.post { outputScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun copyOutput() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Abyss Anmap output", outputBuffer.toString()))
        Toast.makeText(this, "Ausgabe in die Zwischenablage kopiert", Toast.LENGTH_SHORT).show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("Abyss Anmap")
            .setMessage(
                "Abyss Anmap führt den Nmap Security Scanner lokal auf dem Gerät aus.\n\n" +
                    "Enthalten: kompletter bereitgestellter Nmap-NSE-Katalog, nselib, Nmap-Daten " +
                    "und Vulscan 2.1 mit Offline-CSV-Datenbanken.\n\n" +
                    "Vulscan-Ergebnisse sind mögliche Produkt-/Versionskorrelationen, keine bestätigten Schwachstellen.\n\n" +
                    "Nmap ist eine eingetragene Marke von Nmap Software LLC. " +
                    "Abyss Anmap ist kein offizielles Nmap-Produkt."
            )
            .setNeutralButton("Volltexte") { _, _ ->
                showLongTextDialog(
                    "Open-Source-Lizenzen",
                    readAsset("licenses/NMAP-NPSL.txt") + "\n\n\n" + readAsset("licenses/VULSCAN-GPL-3.0.txt")
                )
            }
            .setPositiveButton("Schließen", null)
            .show()
    }

    private fun showNseCatalog() {
        showLongTextDialog(
            "NSE-Katalog (611 Nmap + Vulscan)",
            readAsset("nmap-data/scripts/script.db") +
                "\n\nZusätzlich eingebunden:\n  vulscan/vulscan.nse\n"
        )
    }

    private fun showLongTextDialog(title: String, content: String) {
        val text = textView(content, 11.5f, TEXT).apply {
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val scroll = ScrollView(this).apply { addView(text) }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Schließen", null)
            .show()
    }

    private fun showMessage(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun readAsset(path: String): String = assets.open(path).bufferedReader().use { it.readText() }

    private fun labeledInput(parent: LinearLayout, title: String, hint: String, inputType: Int): EditText {
        parent.addView(label(title), matchWidth(top = 10, bottom = 4))
        return EditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            setSingleLine(true)
            setTextColor(Color.parseColor(TEXT))
            setHintTextColor(Color.parseColor(MUTED))
            background = rounded(SURFACE_ALT, 8)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            parent.addView(this, matchWidth())
        }
    }

    private fun sectionTitle(value: String): TextView = textView(value, 17f, CYAN, Typeface.BOLD)

    private fun label(value: String): TextView = textView(value, 13f, TEXT, Typeface.BOLD)

    private fun textView(value: String, sizeSp: Float, color: String, style: Int = Typeface.NORMAL): TextView =
        TextView(this).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(Color.parseColor(color))
            setTypeface(typeface, style)
        }

    private fun button(value: String, primary: Boolean): Button = Button(this).apply {
        text = value
        isAllCaps = false
        setTextColor(Color.parseColor(if (primary) BACKGROUND else TEXT))
        background = rounded(if (primary) CYAN else SURFACE_ALT, 8)
        minHeight = dp(42)
        setPadding(dp(10), 0, dp(10), 0)
    }

    private fun rounded(color: String, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(Color.parseColor(color))
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun matchWidth(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}
