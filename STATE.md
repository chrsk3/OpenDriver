# STATE — OpenDriver

**Data/hora:** 2026-10-14 04:44 (-03)
**Branch:** `main` · **Repo:** https://github.com/chrsk3/OpenDriver
**Último commit:** `df0ad67867c19a0d06799edfc879de0344d3d510`
**Working tree:** limpo

---

## Última task em andamento
**Otimizar a latência do OCR.** O usuário relatou que a leitura da primeira corrida
demorava (~14s, depois ~2–4s). Já implementado nesta reta final:
- `perf(ocr): recorta a faixa da oferta e reduz resolução antes do OCR` (`df0ad67`)
  — recorta os ~70% de baixo da tela (onde fica o card da oferta da Uber) e limita a
  largura a 900px antes de mandar pro ML Kit (menos pixels = mais rápido).
- Antes disso: warm-up do modelo ML Kit no `onCreate` e throttle de OCR em 1s.

> **Pendente de validação:** o usuário vai testar **amanhã** o comportamento/velocidade
> com a captura recortada.

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
