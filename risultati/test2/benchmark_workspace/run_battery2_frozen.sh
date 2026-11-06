#!/usr/bin/env bash
set -euo pipefail

cd /home/del/Projects/slm-orchestrator

MODE="${1:-b_and_c}" # "b_and_c" (default) oppure "all" (include anche 7B orchestrator)
CP="risultati/test2/benchmark_workspace:target/classes:target/test-classes:$(cat risultati/test2/benchmark_workspace/cp.txt 2>/dev/null || true)"
OUT_DIR="risultati/test2/batteria2_3b_vs_7b"
DS_DIR="risultati/test2/datasets"

mkdir -p "$OUT_DIR"

# Copia 3B_CON_GUARDRAIL già eseguita nella Batteria 1 per avere il riferimento completo nella cartella
if [[ ! -d "$OUT_DIR/3B_CON_GUARDRAIL" ]]; then
    cp -rp "risultati/test2/batteria1_3b_guardrail_ablation/3B_CON_GUARDRAIL" "$OUT_DIR/"
fi

start_spring_7b() {
    local port=$1
    local guards=$2
    local coherence=$3
    local logfile=$4

    echo "[RUNNER 7B] Avvio Spring Boot su porta $port con qwen2.5:7b-instruct-q8_0 (guards=$guards, coherence=$coherence)..."
    java -cp "$CP" com.tirocinioaltrama.slmorchestrator.SlmOrchestratorApplication \
        --server.port="$port" \
        --router.type=local-ollama \
        --ollama.base-model=qwen2.5:7b-instruct-q8_0 \
        --ollama.num-ctx=2048 \
        --ollama.num-predict=400 \
        --ollama.keep-alive=60m \
        --ollama.temperature=0.0 \
        --ollama.seed=42 \
        --ollama.repeat-penalty=1.0 \
        --orchestrator.guards.enabled="$guards" \
        --orchestrator.coherence.enabled="$coherence" \
        --orchestrator.pipeline.max-duration-ms=300000 \
        > "$logfile" 2>&1 &
    SPRING_PID=$!

    for i in $(seq 1 60); do
        if grep -q "Started SlmOrchestratorApplication" "$logfile" 2>/dev/null; then
            echo "[RUNNER 7B] Spring Boot pronto (PID=$SPRING_PID)."
            return 0
        fi
        sleep 1
    done
    echo "[ERRORE] Timeout avvio Spring Boot 7B!"
    kill -9 "$SPRING_PID" 2>/dev/null || true
    exit 1
}

stop_spring() {
    if [[ -n "${SPRING_PID:-}" ]]; then
        echo "[RUNNER 7B] Arresto Spring Boot (PID=$SPRING_PID)..."
        kill "$SPRING_PID" 2>/dev/null || true
        wait "$SPRING_PID" 2>/dev/null || true
        SPRING_PID=""
    fi
}
trap stop_spring EXIT

run_cell_if_missing() {
    local mode=$1
    local cella=$2
    local split=$3
    local ds=$4
    shift 4
    local summary_file="$OUT_DIR/$cella/$split/summary.json"
    if [[ -f "$summary_file" ]]; then
        echo "[SKIP] $summary_file già completato."
        return 0
    fi
    java -cp "$CP" com.tirocinioaltrama.slmorchestrator.eval.Battery1FrozenRunner \
        "$mode" "$cella" "$split" "$ds" "$OUT_DIR" "$@"
}

derive_cell_if_missing() {
    local src=$1
    local tgt=$2
    local split=$3
    local ds=$4
    local summary_file="$OUT_DIR/$tgt/$split/summary.json"
    if [[ -f "$summary_file" ]]; then
        echo "[SKIP] $summary_file già completato."
        return 0
    fi
    java -cp "$CP" com.tirocinioaltrama.slmorchestrator.eval.PostHocDeriveRunner \
        "$src" "$tgt" "$split" "$ds" "$OUT_DIR"
}

# ==============================================================================
# 1. Cella B: 7B_MONOLITICO_SENZA_GUARDRAIL (OllamaMonolithicClient 7B)
#    (Supporta salvataggio incrementale docs/<id>.json e resume automatico)
# ==============================================================================
run_cell_if_missing monolithic "7B_MONOLITICO_SENZA_GUARDRAIL" "heldout" "$DS_DIR/heldout_dataset_v1.json" "qwen2.5:7b-instruct-q8_0"
run_cell_if_missing monolithic "7B_MONOLITICO_SENZA_GUARDRAIL" "heldout_adversarial" "$DS_DIR/heldout_adversarial_v1.json" "qwen2.5:7b-instruct-q8_0"
run_cell_if_missing monolithic "7B_MONOLITICO_SENZA_GUARDRAIL" "dev" "$DS_DIR/dataset_60_v2.json" "qwen2.5:7b-instruct-q8_0"
run_cell_if_missing monolithic "7B_MONOLITICO_SENZA_GUARDRAIL" "dev_adversarial" "$DS_DIR/adversarial_piva_set.json" "qwen2.5:7b-instruct-q8_0"

# ==============================================================================
# 2. Cella C: 7B_MONOLITICO_POSTHOC_GUARDRAIL (Derivata da Cella B con PostHocGuardrails)
# ==============================================================================
derive_cell_if_missing "7B_MONOLITICO_SENZA_GUARDRAIL" "7B_MONOLITICO_POSTHOC_GUARDRAIL" "heldout" "$DS_DIR/heldout_dataset_v1.json"
derive_cell_if_missing "7B_MONOLITICO_SENZA_GUARDRAIL" "7B_MONOLITICO_POSTHOC_GUARDRAIL" "heldout_adversarial" "$DS_DIR/heldout_adversarial_v1.json"
derive_cell_if_missing "7B_MONOLITICO_SENZA_GUARDRAIL" "7B_MONOLITICO_POSTHOC_GUARDRAIL" "dev" "$DS_DIR/dataset_60_v2.json"
derive_cell_if_missing "7B_MONOLITICO_SENZA_GUARDRAIL" "7B_MONOLITICO_POSTHOC_GUARDRAIL" "dev_adversarial" "$DS_DIR/adversarial_piva_set.json"

if [[ "$MODE" == "all" ]]; then
    # ==============================================================================
    # 3. 7B_ABLAZIONE_GUARDRAIL (Orchestratore 7B con guards=false, coherence=false)
    # ==============================================================================
    if [[ ! -f "$OUT_DIR/7B_ABLAZIONE_GUARDRAIL/heldout/summary.json" || \
          ! -f "$OUT_DIR/7B_ABLAZIONE_GUARDRAIL/heldout_adversarial/summary.json" || \
          ! -f "$OUT_DIR/7B_ABLAZIONE_GUARDRAIL/dev/summary.json" || \
          ! -f "$OUT_DIR/7B_ABLAZIONE_GUARDRAIL/dev_adversarial/summary.json" ]]; then
        start_spring_7b 18082 false false "risultati/test2/benchmark_workspace/spring_7b_guards_false.log"
        run_cell_if_missing orchestrator "7B_ABLAZIONE_GUARDRAIL" "heldout" "$DS_DIR/heldout_dataset_v1.json" 18082 false false "qwen2.5:7b-instruct-q8_0"
        run_cell_if_missing orchestrator "7B_ABLAZIONE_GUARDRAIL" "heldout_adversarial" "$DS_DIR/heldout_adversarial_v1.json" 18082 false false "qwen2.5:7b-instruct-q8_0"
        run_cell_if_missing orchestrator "7B_ABLAZIONE_GUARDRAIL" "dev" "$DS_DIR/dataset_60_v2.json" 18082 false false "qwen2.5:7b-instruct-q8_0"
        run_cell_if_missing orchestrator "7B_ABLAZIONE_GUARDRAIL" "dev_adversarial" "$DS_DIR/adversarial_piva_set.json" 18082 false false "qwen2.5:7b-instruct-q8_0"
        stop_spring
    fi

    # ==============================================================================
    # 4. Cella A_7B: 7B_CON_GUARDRAIL (Orchestratore 7B con guards=true, coherence=true)
    # ==============================================================================
    if [[ ! -f "$OUT_DIR/7B_CON_GUARDRAIL/heldout/summary.json" || \
          ! -f "$OUT_DIR/7B_CON_GUARDRAIL/heldout_adversarial/summary.json" || \
          ! -f "$OUT_DIR/7B_CON_GUARDRAIL/dev/summary.json" || \
          ! -f "$OUT_DIR/7B_CON_GUARDRAIL/dev_adversarial/summary.json" ]]; then
        start_spring_7b 18083 true true "risultati/test2/benchmark_workspace/spring_7b_guards_true.log"
        run_cell_if_missing orchestrator "7B_CON_GUARDRAIL" "heldout" "$DS_DIR/heldout_dataset_v1.json" 18083 true true "qwen2.5:7b-instruct-q8_0"
        run_cell_if_missing orchestrator "7B_CON_GUARDRAIL" "heldout_adversarial" "$DS_DIR/heldout_adversarial_v1.json" 18083 true true "qwen2.5:7b-instruct-q8_0"
        run_cell_if_missing orchestrator "7B_CON_GUARDRAIL" "dev" "$DS_DIR/dataset_60_v2.json" 18083 true true "qwen2.5:7b-instruct-q8_0"
        run_cell_if_missing orchestrator "7B_CON_GUARDRAIL" "dev_adversarial" "$DS_DIR/adversarial_piva_set.json" 18083 true true "qwen2.5:7b-instruct-q8_0"
        stop_spring
    fi
fi

python3 risultati/test2/benchmark_workspace/aggregate_battery2.py

echo "=== ESECUZIONE BATTERIA 2 (MODE=$MODE) COMPLETATA CON SUCCESSO ==="
