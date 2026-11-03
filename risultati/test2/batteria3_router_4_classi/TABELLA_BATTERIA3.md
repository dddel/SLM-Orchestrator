# Batteria 3: Accuratezza e Latenza Router Locale a 4 Classi (`LocalOllamaRouter`)

- **Commit Git (`freeze-1`)**: `bf3d125a7e7811283e7799e49725f8be605c2cf8`
- **Modello Router**: `qwen2.5:3b-instruct-q8_0`
- **Architettura**: Invocazione del router leggero a 4 classi (`FATTURA`, `EMAIL`, `ANAGRAFICA`, `ALTRO`) con prompt di sistema dedicato (`prompts/router-system.txt`).

## 1. Set Held-Out Out-of-Sample (50 documenti: 14 FATTURA, 14 ANAGRAFICA, 14 EMAIL, 8 ALTRO)

| Metrica | Valore |
| :--- | :---: |
| Documenti Totali | 50 |
| Instradamenti Corretti | 49 / 50 |
| Accuratezza di Routing (Wilson 95%) | 49/50 (98.0%, IC 95%: 89.5–99.6%) |
| Latenza Mediana | 421 ms |
| Latenza Media | 435.7 ms |
| Latenza P95 | 635 ms |

### Matrice di Confusione

| Tipo Effettivo \ Predetto | FATTURA | EMAIL | ANAGRAFICA | ALTRO | Accuratezza per Classe |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **FATTURA** | 14 | 0 | 0 | 0 | 100.0% (14/14) |
| **EMAIL** | 0 | 14 | 0 | 0 | 100.0% (14/14) |
| **ANAGRAFICA** | 0 | 0 | 14 | 0 | 100.0% (14/14) |
| **ALTRO** | 0 | 1 | 0 | 7 | 87.5% (7/8) |

## 2. Set di Sviluppo (60 documenti: 20 FATTURA, 20 ANAGRAFICA, 20 EMAIL)

| Metrica | Valore |
| :--- | :---: |
| Documenti Totali | 60 |
| Instradamenti Corretti | 60 / 60 |
| Accuratezza di Routing (Wilson 95%) | 60/60 (100.0%, IC 95%: 94.0–100.0%) |
| Latenza Mediana | 341 ms |
| Latenza Media | 342.0 ms |
| Latenza P95 | 412 ms |

### Matrice di Confusione

| Tipo Effettivo \ Predetto | FATTURA | EMAIL | ANAGRAFICA | ALTRO | Accuratezza per Classe |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **FATTURA** | 20 | 0 | 0 | 0 | 100.0% (20/20) |
| **EMAIL** | 0 | 20 | 0 | 0 | 100.0% (20/20) |
| **ANAGRAFICA** | 0 | 0 | 20 | 0 | 100.0% (20/20) |
| **ALTRO** | 0 | 0 | 0 | 0 | - |

