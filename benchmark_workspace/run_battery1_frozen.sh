#!/usr/bin/env bash
set -euo pipefail

cd /home/del/Projects/slm-orchestrator

CP="benchmark_workspace:target/classes:target/test-classes:$(cat benchmark_workspace/cp.txt)"
OUT_DIR="risultati/test2/batteria1_3b_guardrail_ablation"
DS_DIR="risultati/test2/datasets"

mkdir -p "$OUT_DIR"

start_spring() {
    local port=$1
    local guards=$2
    local coherence=$3
    local logfile=$4

    echo "[RUNNER] Avvio Spring Boot su porta $port (guards=$guards, coherence=$coherence)..."
    java -cp "$CP" com.tirocinioaltrama.slmorchestrator.SlmOrchestratorApplication \
        --server.port="$port" \
        --router.type=local-ollama \
        --ollama.base-model=qwen2.5:3b-instruct-q8_0 \
        --ollama.num-ctx=2048 \
        --ollama.num-predict=400 \
        --ollama.keep-alive=60m \
        --ollama.temperature=0.0 \
        --ollama.seed=42 \
        --ollama.repeat-penalty=1.0 \
        --orchestrator.guards.enabled="$guards" \
        --orchestrator.coherence.enabled="$coherence" \
        > "$logfile" 2>&1 &
    SPRING_PID=$!

    for i in $(seq 1 60); do
        if grep -q "Started SlmOrchestratorApplication" "$logfile" 2>/dev/null; then
            echo "[RUNNER] Spring Boot pronto (PID=$SPRING_PID)."
            return 0
        fi
        sleep 1
    done
    echo "[ERRORE] Timeout avvio Spring Boot!"
    kill -9 "$SPRING_PID" 2>/dev/null || true
    exit 1
}

stop_spring() {
    if [[ -n "${SPRING_PID:-}" ]]; then
        echo "[RUNNER] Arresto Spring Boot (PID=$SPRING_PID)..."
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
        echo "[SKIP] $summary_file già presente."
        return 0
    fi
    java -cp "$CP" com.tirocinioaltrama.slmorchestrator.eval.Battery1FrozenRunner \
        "$mode" "$cella" "$split" "$ds" "$OUT_DIR" "$@"
}

# ==============================================================================
# 1. Configurazione 1: 3B_CON_GUARDRAIL (guards=true, coherence=true)
# ==============================================================================
if [[ ! -f "$OUT_DIR/3B_CON_GUARDRAIL/heldout/summary.json" || \
      ! -f "$OUT_DIR/3B_CON_GUARDRAIL/heldout_adversarial/summary.json" || \
      ! -f "$OUT_DIR/3B_CON_GUARDRAIL/dev/summary.json" || \
      ! -f "$OUT_DIR/3B_CON_GUARDRAIL/dev_adversarial/summary.json" ]]; then
    start_spring 18080 true true "benchmark_workspace/spring_3b_guards_true.log"
    run_cell_if_missing orchestrator "3B_CON_GUARDRAIL" "heldout" "$DS_DIR/heldout_dataset_v1.json" 18080 true true "qwen2.5:3b-instruct-q8_0"
    run_cell_if_missing orchestrator "3B_CON_GUARDRAIL" "heldout_adversarial" "$DS_DIR/heldout_adversarial_v1.json" 18080 true true "qwen2.5:3b-instruct-q8_0"
    run_cell_if_missing orchestrator "3B_CON_GUARDRAIL" "dev" "$DS_DIR/dataset_60_v2.json" 18080 true true "qwen2.5:3b-instruct-q8_0"
    run_cell_if_missing orchestrator "3B_CON_GUARDRAIL" "dev_adversarial" "$DS_DIR/adversarial_piva_set.json" 18080 true true "qwen2.5:3b-instruct-q8_0"
    stop_spring
fi

# ==============================================================================
# 2. Configurazione 2: 3B_ABLAZIONE_GUARDRAIL (guards=false, coherence=false)
# ==============================================================================
if [[ ! -f "$OUT_DIR/3B_ABLAZIONE_GUARDRAIL/heldout/summary.json" || \
      ! -f "$OUT_DIR/3B_ABLAZIONE_GUARDRAIL/heldout_adversarial/summary.json" || \
      ! -f "$OUT_DIR/3B_ABLAZIONE_GUARDRAIL/dev/summary.json" || \
      ! -f "$OUT_DIR/3B_ABLAZIONE_GUARDRAIL/dev_adversarial/summary.json" ]]; then
    start_spring 18081 false false "benchmark_workspace/spring_3b_guards_false.log"
    run_cell_if_missing orchestrator "3B_ABLAZIONE_GUARDRAIL" "heldout" "$DS_DIR/heldout_dataset_v1.json" 18081 false false "qwen2.5:3b-instruct-q8_0"
    run_cell_if_missing orchestrator "3B_ABLAZIONE_GUARDRAIL" "heldout_adversarial" "$DS_DIR/heldout_adversarial_v1.json" 18081 false false "qwen2.5:3b-instruct-q8_0"
    run_cell_if_missing orchestrator "3B_ABLAZIONE_GUARDRAIL" "dev" "$DS_DIR/dataset_60_v2.json" 18081 false false "qwen2.5:3b-instruct-q8_0"
    run_cell_if_missing orchestrator "3B_ABLAZIONE_GUARDRAIL" "dev_adversarial" "$DS_DIR/adversarial_piva_set.json" 18081 false false "qwen2.5:3b-instruct-q8_0"
    stop_spring
fi

# ==============================================================================
# 3. Configurazione 3: 3B_MONOLITICO_SENZA_GUARDRAIL (OllamaMonolithicClient 3B)
# ==============================================================================
run_cell_if_missing monolithic "3B_MONOLITICO_SENZA_GUARDRAIL" "heldout" "$DS_DIR/heldout_dataset_v1.json" "qwen2.5:3b-instruct-q8_0"
run_cell_if_missing monolithic "3B_MONOLITICO_SENZA_GUARDRAIL" "heldout_adversarial" "$DS_DIR/heldout_adversarial_v1.json" "qwen2.5:3b-instruct-q8_0"
run_cell_if_missing monolithic "3B_MONOLITICO_SENZA_GUARDRAIL" "dev" "$DS_DIR/dataset_60_v2.json" "qwen2.5:3b-instruct-q8_0"
run_cell_if_missing monolithic "3B_MONOLITICO_SENZA_GUARDRAIL" "dev_adversarial" "$DS_DIR/adversarial_piva_set.json" "qwen2.5:3b-instruct-q8_0"

echo "=== TUTTE LE RUN DELLA BATTERIA 1 COMPLETATE CON SUCCESSO ==="
