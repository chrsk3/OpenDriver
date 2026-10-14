package org.opendriver.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.opendriver.core.calculator.ViabilityEngine
import org.opendriver.data.PreferencesRepository
import org.opendriver.parser.RideTextParser

/**
 * Serviço de acessibilidade que monitora a tela para capturar dados
 * de corridas da Uber e 99 no instante em que a chamada toca.
 */
class OpenDriverAccessibilityService : AccessibilityService() {

    private lateinit var preferencesRepo: PreferencesRepository
    private var lastOfferKey: String = ""
    private var lastOfferTimestamp: Long = 0

    override fun onCreate() {
        super.onCreate()
        preferencesRepo = PreferencesRepository(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // Verifica se o evento pertence a algum app monitorado
        val isTargetApp = packageName.contains("ubercab", ignoreCase = true) ||
                packageName.contains("taxis99", ignoreCase = true) ||
                packageName.contains("didiglobal", ignoreCase = true) ||
                packageName.contains("indrive", ignoreCase = true)

        if (!isTargetApp) return

        // Verifica se o Copiloto e o Cálculo de Ganhos estão ativos
        if (!preferencesRepo.getCopilotEnabled() || !preferencesRepo.getEarningsCalcEnabled()) {
            return
        }

        // Extrai todo o conteúdo visível da janela atual
        val rootNode = rootInActiveWindow ?: return
        val texts = mutableListOf<String>()
        extractTextNodes(rootNode, texts)
        rootNode.recycle()

        if (texts.isEmpty()) return

        // Analisa o texto capturado procurando uma oferta válida
        val offer = RideTextParser.parse(packageName, texts) ?: return

        // Evita reprocessar a mesma oferta repetidamente em intervalos menores que 6 segundos
        val offerKey = "${offer.platform}_${offer.grossFare}_${offer.totalDistanceKm}"
        val now = System.currentTimeMillis()
        if (offerKey == lastOfferKey && (now - lastOfferTimestamp) < 6000) {
            return
        }

        lastOfferKey = offerKey
        lastOfferTimestamp = now

        // Carrega as configurações do motorista e calcula a viabilidade
        val profile = preferencesRepo.getVehicleProfile()
        val preferences = preferencesRepo.getDriverPreferences()
        val result = ViabilityEngine.evaluate(offer, profile, preferences)

        // Envia o resultado para a bolha flutuante
        FloatingOverlayService.showResult(this, result)
    }

    private fun extractTextNodes(node: AccessibilityNodeInfo?, output: MutableList<String>) {
        if (node == null) return

        node.text?.toString()?.trim()?.let {
            if (it.isNotEmpty()) output.add(it)
        }

        node.contentDescription?.toString()?.trim()?.let {
            if (it.isNotEmpty() && !output.contains(it)) output.add(it)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractTextNodes(child, output)
                child.recycle()
            }
        }
    }

    override fun onInterrupt() {
        // Chamado quando o sistema interrompe o serviço de acessibilidade
    }
}
