package org.opendriver.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.opendriver.core.calculator.ViabilityEngine
import org.opendriver.data.PreferencesRepository
import org.opendriver.parser.RideTextParser
import java.io.File

/**
 * Serviço de acessibilidade que monitora a tela para capturar dados
 * de corridas da Uber e 99 no instante em que a chamada toca.
 */
class OpenDriverAccessibilityService : AccessibilityService() {

    private lateinit var preferencesRepo: PreferencesRepository
    private var lastOfferKey: String = ""
    private var lastOfferTimestamp: Long = 0
    private var eventsSeen: Int = 0
    private var lastCounterPersist: Long = 0

    override fun onCreate() {
        super.onCreate()
        preferencesRepo = PreferencesRepository(this)
        eventsSeen = preferencesRepo.getReaderEventCount()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (!isTargetApp(packageName)) return

        countEvent()

        if (!preferencesRepo.getCopilotEnabled() || !preferencesRepo.getEarningsCalcEnabled()) {
            return
        }

        val ownPackage = this.packageName
        val texts = LinkedHashSet<String>()

        // 1) Texto carregado no próprio evento — captura direta do que mudou na tela.
        for (i in event.text.indices) {
            event.text[i]?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }
        }
        event.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }

        // 2) Varredura das janelas (oferta costuma vir em popup/janela secundária).
        val activeRoot = rootInActiveWindow
        if (activeRoot != null && activeRoot.packageName?.toString() != ownPackage) {
            extractTextNodes(activeRoot, texts)
        }
        try {
            windows?.forEach { window ->
                val root = window.root ?: return@forEach
                if (root.packageName?.toString() == ownPackage) return@forEach
                extractTextNodes(root, texts)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao enumerar janelas: ${e.message}")
        }

        if (texts.isEmpty()) return

        // Ignora ruído (Recentes, barra de notificações, etc.): só registra/analisa
        // capturas que pareçam conter uma oferta (valor em R$, km ou min).
        if (!looksLikeOffer(texts)) return

        val offer = RideTextParser.parse(packageName, texts.toList())
        if (offer == null) {
            persistRelevantCapture(
                "pkg=$packageName • ${texts.size} textos, oferta NÃO reconhecida\n" +
                    "[" + texts.joinToString(" | ").take(1600) + "]"
            )
            return
        }

        val offerKey = "${offer.platform}_${offer.grossFare}_${offer.totalDistanceKm}"
        val now = System.currentTimeMillis()
        if (offerKey == lastOfferKey && (now - lastOfferTimestamp) < 6000) return

        lastOfferKey = offerKey
        lastOfferTimestamp = now
        Log.i(TAG, "Oferta reconhecida: $offer")

        val profile = preferencesRepo.getVehicleProfile()
        val preferences = preferencesRepo.getDriverPreferences()
        val result = ViabilityEngine.evaluate(offer, profile, preferences)

        persistRelevantCapture(
            "pkg=$packageName • OK\n" +
                "R$ ${"%.2f".format(result.offer.grossFare)} • " +
                "${"%.1f".format(result.offer.totalDistanceKm)} km • " +
                "lucro R$ ${"%.2f".format(result.netProfit)} • ${result.status.label}"
        )

        FloatingOverlayService.showResult(this, result)
    }

    private fun countEvent() {
        eventsSeen++
        val now = System.currentTimeMillis()
        if (now - lastCounterPersist >= 1000) {
            lastCounterPersist = now
            preferencesRepo.setReaderEventCount(eventsSeen)
        }
    }

    private fun isTargetApp(packageName: String): Boolean =
        packageName.contains("ubercab", ignoreCase = true) ||
                packageName.contains("taxis99", ignoreCase = true) ||
                packageName.contains("didiglobal", ignoreCase = true) ||
                packageName.contains("indrive", ignoreCase = true)

    /** Heurística: a captura tem cara de oferta se contém valor (R$), distância (km) ou tempo (min). */
    private fun looksLikeOffer(texts: Collection<String>): Boolean = texts.any { t ->
        t.contains("R$", ignoreCase = true) ||
            Regex("""\d[.,]?\d*\s*km""", RegexOption.IGNORE_CASE).containsMatchIn(t) ||
            Regex("""\d+\s*min""", RegexOption.IGNORE_CASE).containsMatchIn(t)
    }

    private fun persistRelevantCapture(info: String) {
        preferencesRepo.setLastCaptureInfo(info)
        // Espelha a captura completa num arquivo compartilhável para depuração.
        try {
            getExternalFilesDir(null)?.let { dir ->
                File(dir, "last_capture.txt").writeText(info)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao gravar arquivo de captura: ${e.message}")
        }
    }

    private fun extractTextNodes(node: AccessibilityNodeInfo?, output: MutableCollection<String>) {
        if (node == null) return

        node.text?.toString()?.trim()?.let {
            if (it.isNotEmpty()) output.add(it)
        }

        node.contentDescription?.toString()?.trim()?.let {
            if (it.isNotEmpty()) output.add(it)
        }

        for (i in 0 until node.childCount) {
            extractTextNodes(node.getChild(i), output)
        }
    }

    override fun onInterrupt() {
        // Chamado quando o sistema interrompe o serviço de acessibilidade
    }

    companion object {
        private const val TAG = "OpenDriver"
    }
}
