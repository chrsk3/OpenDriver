# STATE — OpenDriver

**Data/hora:** 2026-10-15 01:36 (-03)
**Branch:** `main` · **Repo:** https://github.com/chrsk3/OpenDriver
**Último commit:** `6950deef6d431e6fc77cd8c66181165510331202`
**Working tree:** limpo

---

## Última task em andamento
**Paridade com GigU (co.gigu.app).** Implementados até o momento:
1. **Deduplicação perceptual de frames (SAD 64x64):** `FrameDeduplicator` descarta screenshots quase idênticos (< 8% SAD) antes do ML Kit.
2. **Reconstrução geométrica de linhas:** `LineReconstructor` agrupa elementos com sobreposição vertical ≥ 50% do baseline e ordena horizontalmente da esquerda para a direita.
3. **Notificação rica + botões Maps + anti-dismiss:** `RideOfferNotificationHelper` com layout `notification_ride_offer.xml` (RemoteViews), canal de alta prioridade, `autoCancel(false)` e intents do Google Maps para Embarque e Destino.
4. **Card flutuante com posições ancoradas e auto-dismiss:** Ancoragem em 3 posições (`CENTER`, `LEFT`, `RIGHT`) via `CardPosition`, default no Centro com `x = 0`, duração configurável (padrão 10s) e timer com descarte manual ao clicar e pausa em arraste.
5. **Histórico em SQLite via Room:** `RideEntity`, `RideDao` e `AppDatabase` (androidx.room) substituindo `ride_history.csv`, com migração única automática dos dados legados, schema detalhado de métricas e suporte na `HistoryScreen`.
6. **Suporte à 99 (app do motorista):** Detecção de pacotes oficiais (`com.app99.driver`, `com.app99.radar.driver`, `com.app99.food.driver`, `com.didiglobal.driver`, `com.taxis99`), parsing de categorias (99Pop, 99Comfort, 99Moto, 99Negocia, etc.), notas no formato 99 (`4.8 ★`), duração com horas (`1 h 15 min`) e extração de endereços de embarque/destino integrada aos botões do Google Maps e persistência no Room.
7. **Menu expandido na bolha copiloto:** Ao tocar no botão flutuante, abre um menu compacto com atalhos rápidos (Abrir app, Histórico de corridas, Ativar/Pausar leitor OCR com feedback via Toast, Ativar/Desativar notificações ricas e Fechar copiloto), fechamento ao tocar fora (`FLAG_WATCH_OUTSIDE_TOUCH`) ou arrastar, e navegação direta para telas via `MainActivity.EXTRA_SCREEN`.
8. **Fix de compilação do APK Android no CI:** Ajuste de escopo de métodos e `companion object` na `MainActivity`, correção de assinatura de `onNewIntent(intent: Intent)` e smart cast seguro de `passengerRating` em módulo cruzado.

---

## O que já foi feito

### Como o app lê as corridas (arquitetura atual)
1. `OpenDriverAccessibilityService` detecta eventos dos apps de corrida (Uber/99/InDrive).
2. **A árvore de acessibilidade NÃO expõe o texto da oferta** (só chrome do sistema).
   Confirmado analisando o APK do GigU (`co.gigu.app`): ele usa
   `AccessibilityService.takeScreenshot()` + **ML Kit Text Recognition (OCR)**.
3. Replicamos: `takeScreenshot()` (API 30+) → recorte da faixa inferior → **ML Kit OCR**
   → parser → `FloatingOverlayService.showResult()`.
4. Dedupe/throttle de ofertas, gate por **botão de aceite** (`Aceitar|Recusar|Selecionar|...`)
   pra descartar home/oportunidades.

### Commits relevantes (mais recente primeiro)
- `6950dee` fix(ui): ajustar assinatura de onNewIntent para Intent nao-nulo
- `31d509d` fix(ui): mover onNewIntent e companion object para dentro da MainActivity
- `1a380ee` fix(build): corrigir escopo de metodos na MainActivity e smart cast de rating
- `0e757ac` feat: menu expandido na bolha copiloto
- `5a90376` feat: suporte a ofertas da 99
- `cf28600` feat: histórico de corridas migrado para Room
- `1e80343` feat: Fase A - paridade essencial com GigU (SAD, LineReconstructor, notificação rica, card posição/duração)
- `84d01a0` docs: add STATE.md (pausa do projeto - status, proximos passos e duvidas)
- `df0ad67` perf(ocr): recorte da faixa da oferta + downscale
- `e8e14fb` fix(prefs): Copiloto/Cálculo iniciam desligados (migração única)
- `4a26fa8` feat(ui): toggles iniciam off, dialog de permissões ao iniciar, ajustes em Detalhes
- `14e4bc4` feat(ui+reader): tamanho do botão vs do card, passos de slider, histórico
- `9abd737` refactor(ui): home sem botões; Copiloto só tamanho/opacidade; ações no Cálculo de Ganhos
- `c5cc780` feat(ui): redesign multi-tela (Copiloto/Detalhes/Editar/Cálculo de Ganhos)
- `75da663` fix(perf+ui): I/O e OCR fora da main thread; metas do semáforo configuráveis
- `733e7a4` feat(reader): OCR via screenshot + ML Kit
- `1fc8c2f` fix(parser): casar NBSP após "R$" (causa-raiz de "oferta não reconhecida")

### Arquivos-fonte
**core/** (Kotlin puro, testável via `./run.sh --test`)
- `model/`: `VehicleProfile`, `DriverPreferences` (faixas ruim..bom), `RideOffer`,
  `ViabilityResult`, `ViabilityStatus`, `FuelType`, `Platform`, `BubbleSize`
- `calculator/ViabilityEngine` (status do semáforo pelas metas do usuário)
- `cli/OpenDriverCli`

**app/**
- `ui/`: `MainActivity` (navegação), `UiKit` (componentes/tema), `CopilotScreen`,
  `EarningsScreen`, `EditScreen`, `DetailsScreen`, `HistoryScreen`
- `service/`: `OpenDriverAccessibilityService`, `FloatingOverlayService`
- `parser/`: `RideTextParser`
- `data/`: `PreferencesRepository`, `RideHistoryEntry`
- `res/xml/accessibility_service_config.xml` (canTakeScreenshot, flags)

### Funcionalidades entregues
- Copiloto: bolha flutuante (botão) com **tamanho** e **opacidade**; arrastável;
  clique abre o app; long-press remove.
- Card de resultado estilo "crachá": `$/Km`, `$/Hora`, `Nota`, rodapé com badge +
  tempo/km/busca; **cor = semáforo** (verde/amarelo/vermelho) pelas metas.
- Cálculo de Ganhos: apps (Uber/99/InDrive), meta semanal, sliders de faixa
  (ganho/km, ganho/hora, nota), aparência do card (tamanho, métricas, tema, posição,
  paleta, opacidade, fonte, tempo), Avançado (notificação de texto), Permissões,
  Diagnóstico, botões Editar/Detalhes/Testar.
- Editar Cálculo: rotina, veículo, categorias, custos, combustível (incl. elétrico).
- Detalhes: lucro por DIA/SEMANA/MÊS, ganho/custo por km e hora, simulador, custos semanais.
- Histórico de Corridas: lista real das corridas capturadas (`ride_history.csv`).

### Diagnóstico em campo
- Arquivos em `Android/media/org.opendriver/`: `capture_log.txt`, `last_capture.txt`,
  `overlay_log.txt` (leitura direta via Termux).
- Seção "Diagnóstico do Leitor" no app (eventos lidos + última captura).

---

## O que falta fazer
- [ ] **Validar amanhã** a velocidade/estabilidade com o recorte do OCR (`df0ad67`).
- [ ] Confirmar que o recorte de 70% (inferior) não perde nenhuma oferta.
- [ ] Possível ganho extra de latência (ver Próximos passos).
- [ ] (Opcional) Tela de histórico com filtros/agrupamento por dia.
- [ ] (Opcional) OCR para 99/inDrive (hoje focado na Uber).

## Próximos passos concretos
1. Rodar o teste do usuário e medir a latência da 1ª leitura.
2. Se ainda lento: recortar **só o card** (região central-baixa, ~y 35%–70%) em vez da
   tela toda inferior; e/ou descer `OCR_MAX_WIDTH` (hoje 900) para ~720.
3. Ajustar `OFFER_TOP_FRACTION` (hoje `0.30f`) se faltar leitura de alguma oferta.
4. Considerar pré-processamento (escala de cinza/contraste) antes do ML Kit.

## Bloqueios / dúvidas abertas
- **Sem build local:** não há `gradlew` nem Android SDK no Termux; build Android só via
  GitHub Actions (`gradle :app:assembleDebug`). `./run.sh --test` roda só o core.
- **Latência:** o ML Kit tem custo fixo por inferência (~1–2s). ~1s do GigU pode exigir
  crop mais agressivo + região exata do card.
- **Tema/cor:** mantivemos o **verde OpenDriver** (o print de referência é o laranja da
  GigU). Trocar a paleta é uma decisão pendente do usuário.
- **APK** é debug (56,6 MB por causa do modelo ML Kit). Release exigirá ProGuard/split ABI.
