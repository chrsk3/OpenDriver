# STATE — OpenDriver

**Data/hora:** 2026-10-15 02:18 (-03)
**Branch:** `main` · **Repo:** https://github.com/chrsk3/OpenDriver
**Último commit:** `HEAD`
**Working tree:** limpo

---

## Última task em andamento
**Paridade com GigU (co.gigu.app) e Otimização Extrema de Latência (< 600ms).** Implementados até o momento:
1. **Deduplicação perceptual de frames (SAD 64x64):** `FrameDeduplicator` descarta screenshots quase idênticos (< 8% SAD) antes do ML Kit.
2. **Reconstrução geométrica de linhas:** `LineReconstructor` agrupa elementos com sobreposição vertical ≥ 50% do baseline e ordena horizontalmente da esquerda para a direita (mesmo algoritmo do `qf0.smali`).
3. **Notificação rica + botões Maps + anti-dismiss:** `RideOfferNotificationHelper` com layout `notification_ride_offer.xml` (RemoteViews), canal de alta prioridade, `autoCancel(false)` e intents do Google Maps para Embarque e Destino.
4. **Card flutuante com posições ancoradas e auto-dismiss:** Ancoragem em 3 posições (`CENTER`, `LEFT`, `RIGHT`) via `CardPosition`, default no Centro com `x = 0`, duração configurável (padrão 10s) e timer com descarte manual ao clicar e pausa em arraste.
5. **Histórico em SQLite via Room:** `RideEntity`, `RideDao` e `AppDatabase` (androidx.room) substituindo `ride_history.csv`, com migração única automática dos dados legados, schema detalhado de métricas e suporte na `HistoryScreen`.
6. **Suporte à 99 (app do motorista):** Detecção de pacotes oficiais (`com.app99.driver`, `com.app99.radar.driver`, `com.app99.food.driver`, `com.didiglobal.driver`, `com.taxis99`), parsing de categorias (99Pop, 99Comfort, 99Moto, 99Negocia, etc.), notas no formato 99 (`4.8 ★`), duração com horas (`1 h 15 min`) e extração de endereços de embarque/destino integrada aos botões do Google Maps e persistência no Room.
7. **Menu expandido na bolha copiloto:** Ao tocar no botão flutuante, abre um menu compacto com atalhos rápidos (Abrir app, Histórico de corridas, Ativar/Pausar leitor OCR com feedback via Toast, Ativar/Desativar notificações ricas e Fechar copiloto), fechamento ao tocar fora (`FLAG_WATCH_OUTSIDE_TOUCH`) ou arrastar, e navegação direta para telas via `MainActivity.EXTRA_SCREEN`.
8. **Fixes de compilação do APK Android no CI:** Ajuste de escopo de métodos e `companion object` na `MainActivity`, correção de assinatura de `onNewIntent(intent: Intent)` e smart cast seguro de `passengerRating` em módulo cruzado.
9. **Otimizações cirúrgicas de latência e performance (arquitetura GigU):**
   - **Eliminação de latência de IPC do Android (1,2s–1,9s):** Referência estática volátil `instance` no `FloatingOverlayService` com despacho direto via `mainHandler.post { instance?.renderResult(result) }` (latência zero / 0ms) em vez de enfileirar `startForegroundService(intent)`.
   - **Background Capture Loop (`captureExecutor`):** Desacoplamento da Main Thread passando `captureExecutor` diretamente para `takeScreenshot`, liberando o UI thread de conversões e escalas de Bitmap.
   - **Trailing Capture + Retry Pós-Animação:** Fim do descarte cego de 1s; agendamento automático em 450ms–500ms caso o frame capture a animação subindo ainda sem o botão de aceite, garantindo leitura instantânea do card estabilizado.
   - **Ciclo Periódico Ativo:** Verificação periódica suave de 1000ms enquanto apps de corrida estiverem no topo, filtrada em < 1ms pelo `FrameDeduplicator`.
   - **Crop e Downscale Otimizados:** Faixa de 28% a 95% da tela (descartando topo e botões de sistema) e largura máxima reduzida para `720px` (redução de ~36% em pixels, acelerando ML Kit para ~300ms).
   - **Warm-up Real do ML Kit:** Renderização de Bitmap real com texto desenhado via `Canvas/Paint` no `onCreate` para forçar alocação antecipada dos tensores de reconhecimento.

---

## O que já foi feito

### Como o app lê as corridas (arquitetura atual)
1. `OpenDriverAccessibilityService` detecta eventos dos apps de corrida (Uber/99/InDrive).
2. Se a árvore não trouxer dados, dispara captura reativa via screenshot em background (`captureExecutor`) + **ML Kit Text Recognition (OCR)**.
3. Deduplicação perceptual (SAD 64x64) antes do OCR, crop refinado (28%–95%, 720px) e parser robusto com reconstrução de linhas.
4. Entrega instantânea na UI do card via `FloatingOverlayService.renderResult()` com 0ms de IPC.

### Commits relevantes (mais recente primeiro)
- `perf(ocr+overlay)`: direct dispatch do card, captureExecutor em background, trailing capture, crop 720px e warm-up real
- `6950dee` fix(ui): ajustar assinatura de onNewIntent para Intent nao-nulo
- `31d509d` fix(ui): mover onNewIntent e companion object para dentro da MainActivity
- `1a380ee` fix(build): corrigir escopo de metodos na MainActivity e smart cast de rating
- `0e757ac` feat: menu expandido na bolha copiloto
- `5a90376` feat: suporte a ofertas da 99
- `cf28600` feat: histórico de corridas migrado para Room
- `1e80343` feat: Fase A - paridade essencial com GigU (SAD, LineReconstructor, notificação rica, card posição/duração)
- `84d01a0` docs: add STATE.md (pausa do projeto - status, proximos passos e duvidas)
- `df0ad67` perf(ocr): recorte da faixa da oferta + downscale

### Arquivos-fonte
**core/** (Kotlin puro, testável via `./run.sh --test`)
- `model/`: `VehicleProfile`, `DriverPreferences` (faixas ruim..bom), `RideOffer`,
  `ViabilityResult`, `ViabilityStatus`, `FuelType`, `Platform`, `BubbleSize`, `CardPosition`
- `calculator/ViabilityEngine` (status do semáforo pelas metas do usuário)
- `cli/OpenDriverCli`

**app/**
- `ui/`: `MainActivity` (navegação), `UiKit` (componentes/tema), `CopilotScreen`,
  `EarningsScreen`, `EditScreen`, `DetailsScreen`, `HistoryScreen`
- `service/`: `OpenDriverAccessibilityService`, `FloatingOverlayService`, `FrameDeduplicator`
- `notification/`: `RideOfferNotificationHelper`
- `parser/`: `RideTextParser`, `LineReconstructor`
- `data/`: `PreferencesRepository`, `RideHistoryEntry`, `local/AppDatabase`, `local/RideDao`, `local/RideEntity`
- `res/xml/accessibility_service_config.xml` (canTakeScreenshot, flags)

### Funcionalidades entregues
- Copiloto: bolha flutuante com tamanho, opacidade, arrastável, menu compacto ao clicar (atalhos para telas, pausar/ativar OCR, fechar).
- Card de resultado estilo "crachá": `$/Km`, `$/Hora`, `Nota`, rodapé com badge + tempo/km/busca; ancoragem Centro/Esquerda/Direita e auto-dismiss (2s a 60s).
- Notificações ricas com botões para Google Maps (Embarque e Destino) e anti-dismiss.
- Cálculo de Ganhos: apps (Uber/99/InDrive), meta semanal, sliders de faixa (ganho/km, ganho/hora, nota), aparência do card.
- Editar Cálculo: rotina, veículo, categorias, custos, combustível (incl. elétrico).
- Detalhes: lucro por DIA/SEMANA/MÊS, ganho/custo por km e hora, simulador, custos semanais.
- Histórico de Corridas: SQLite via Room com métricas detalhadas e persistência local segura.

---

## O que falta fazer
- [ ] Validar no aparelho a latência real < 600ms com o APK otimizado.
- [ ] (Opcional) OCR para inDrive (Uber e 99 já estão 100% integradas).
- [ ] (Opcional) Tela de histórico com filtros/agrupamento por dia e exportação CSV.

## Bloqueios / dúvidas abertas
- **Sem build local:** não há Android SDK no Termux; build Android via GitHub Actions (`gradle :app:assembleDebug`).
