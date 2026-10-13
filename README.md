# 🚀 OpenDriver

[![OpenDriver CI](https://github.com/chrsk3/OpenDriver/actions/workflows/ci.yml/badge.svg)](https://github.com/chrsk3/OpenDriver/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-purple.svg)](https://kotlinlang.org)

**OpenDriver** é uma alternativa **100% gratuita, open-source e voltada para a privacidade** a aplicativos como *Gigu*, *StopClub* e *Rebu*.

O objetivo é dar aos motoristas e entregadores de aplicativo (Uber, 99, inDrive, iFood) o controle total sobre sua rentabilidade, calculando o **lucro líquido real**, custo por km e ganho por hora sem cobrar assinaturas e sem enviar seus dados pessoais para servidores de terceiros.

---

## 💡 Por que o OpenDriver existe?

Muitos motoristas aceitam corridas acreditando que estão lucrando apenas porque a plataforma pagou R$ 20 ou R$ 30, mas não percebem armadilhas como:
1. **Deslocamento excessivo até o passageiro:** Andar 5 km para pegar uma corrida de 4 km.
2. **Desgaste invisível do veículo:** Óleo, pneus, freios, suspensão e depreciação comem silenciosamente de R$ 0,35 a R$ 0,60 por km rodado além da gasolina.
3. **Trânsito pesado:** Corridas curtas que levam 40 minutos e pagam menos de R$ 10/hora líquida.

O **OpenDriver** calcula tudo isso instantaneamente e diz com precisão se a corrida é:
- 🟢 **Excelente:** Muito acima das suas metas mínimas de km e hora.
- 🟢 **Boa:** Cobre todos os custos e bate as metas de ganho.
- 🟡 **Regular:** Paga as contas mas com margem apertada.
- 🔴 **Prejuízo / Recusar:** O motorista paga para trabalhar.

---

## 🧮 A Fórmula do Cálculo

### 1. Custo Operacional por KM
$$\text{Custo/KM} = \left(\frac{\text{Preço do Combustível}}{\text{Consumo (km/l)}}\right) + \text{Manutenção/km} + \text{Depreciação/km} + \text{Custos Fixos/km}$$

### 2. Distância Total Real
$$\text{KM Total} = \text{Distância de Busca} + \text{Distância da Viagem}$$

### 3. Lucro Líquido Real
$$\text{Lucro Líquido} = \text{Valor Bruto da Corrida} - (\text{KM Total} \times \text{Custo/KM})$$

---

## 📱 Como Usar no Celular (via Termux)

O núcleo do OpenDriver foi projetado para rodar de forma ultraleve direto no seu smartphone Motorola via Termux:

### 1. Executar a demonstração com cenários reais
```bash
./run.sh
```

### 2. Calcular uma corrida personalizada (Modo Interativo)
```bash
./run.sh -i
```
Você poderá digitar na hora o valor, a distância até o passageiro, a distância da viagem e o tempo estimado para receber a análise completa.

### 3. Rodar os testes unitários
```bash
./run.sh --test
```

---

## 🗺️ Roadmap de Desenvolvimento

- [x] **Fase 1: Core de Cálculo em Kotlin Puro**
  - [x] Modelagem de veículo (combustível, manutenção, depreciação e fixos).
  - [x] Mecanismo de detecção de armadilhas (busca longa, notas baixas, rendimento/h).
  - [x] Classificação de viabilidade (Excelente, Boa, Regular, Prejuízo).
  - [x] Suíte de testes unitários automatizados.
  - [x] CLI interativa para Termux.
  - [x] Integração contínua (GitHub Actions CI/CD).
- [x] **Fase 2: Módulo Nativo Android & Leitor de Tela**
  - [x] Leitor em tempo real via `AccessibilityService` (`OpenDriverAccessibilityService`) para Uber e 99.
  - [x] Parser inteligente de texto (`RideTextParser`).
  - [x] Bolha flutuante interativa sobre outros apps (`FloatingOverlayService`).
  - [x] Interface gráfica em Jetpack Compose (`MainActivity`).
  - [x] Compilação automática do APK via GitHub Actions CI.
- [ ] **Fase 3: Recursos Avançados & Histórico**
  - [ ] Histórico local diário de corridas e faturamento (SQLite / Room).
  - [ ] Gráficos de metas atingidas por dia e semana.
  - [ ] Suporte a mais plataformas (inDrive, Lalamove, Borzo).

---

## 📦 Como Baixar o APK do OpenDriver

Como o projeto é construído na nuvem pelo GitHub Actions:
1. Acesse a aba **[Actions](https://github.com/chrsk3/OpenDriver/actions)** do repositório no seu navegador.
2. Clique no último build concluído da branch `main`.
3. Na seção **Artifacts**, clique em **`OpenDriver-debug-apk`** para fazer o download do arquivo `.apk` diretamente para o seu celular.
4. Abra o arquivo no Android e toque em Instalar!

---

## 🤝 Como Contribuir

Contribuições são muito bem-vindas!
1. Faça um Fork do projeto
2. Crie sua branch (`git checkout -b feature/minha-feature`)
3. Faça commit das mudanças (`git commit -m 'Adiciona nova feature'`)
4. Faça o push para a branch (`git push origin feature/minha-feature`)
5. Abra um Pull Request

---

## 📄 Licença

Distribuído sob a licença **MIT**. Veja [`LICENSE`](LICENSE) para mais detalhes.
