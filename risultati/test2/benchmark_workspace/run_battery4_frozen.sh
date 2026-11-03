#!/usr/bin/env bash
set -euo pipefail

cd /home/del/Projects/slm-orchestrator

CP="risultati/test2/benchmark_workspace:target/classes:target/test-classes:$(cat risultati/test2/benchmark_workspace/cp.txt 2>/dev/null || true)"
OUT_DIR="risultati/test2/batteria4_smart_retry"
DS_DIR="risultati/test2/datasets"

mkdir -p "$OUT_DIR"

# Avvia Ollama se non è attivo
ensure_ollama() {
    if ! curl -s http://127.0.0.1:11434/api/tags >/dev/null 2>&1; then
        echo "[RUNNER B4] Avvio demone Ollama con Vulkan..."
        OLLAMA_VULKAN=1 OLLAMA_IGPU_ENABLE=1 /usr/local/bin/ollama serve > /tmp/ollama_vulkan.log 2>&1 &
        OLLAMA_PID=$!
        for i in $(seq 1 30); do
            if curl -s http://127.0.0.1:11434/api/tags >/dev/null 2>&1; then
                echo "[RUNNER B4] Demone Ollama pronto (PID=$OLLAMA_PID)."
                return 0
            fi
            sleep 1
        done
        echo "[ERRORE] Timeout avvio Ollama!"
        exit 1
    else
        echo "[RUNNER B4] Demone Ollama già attivo."
    fi
}

# 1. Copia le configurazioni con Retry RF7 Abilitato (max_retries=2) già eseguite
if [[ ! -d "$OUT_DIR/3B_CON_GUARDRAIL_E_RETRY" ]]; then
    cp -rp "risultati/test2/batteria1_3b_guardrail_ablation/3B_CON_GUARDRAIL" "$OUT_DIR/3B_CON_GUARDRAIL_E_RETRY"
fi

if [[ ! -d "$OUT_DIR/7B_CON_GUARDRAIL_E_RETRY" ]]; then
    cp -rp "risultati/test2/batteria2_3b_vs_7b/7B_CON_GUARDRAIL" "$OUT_DIR/7B_CON_GUARDRAIL_E_RETRY"
fi

start_spring_no_retry() {
    local port=$1
    local logfile=$2

    echo "[RUNNER B4] Avvio Spring Boot su porta $port (guards=true, coherence=true, NO RETRY)..."
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
        --orchestrator.guards.enabled=true \
        --orchestrator.coherence.enabled=true \
        --orchestrator.pipelines.dir=risultati/test2/benchmark_workspace/pipelines_no_retry \
        > "$logfile" 2>&1 &
    SPRING_PID=$!

    for i in $(seq 1 60); do
        if grep -q "Started SlmOrchestratorApplication" "$logfile" 2>/dev/null; then
            echo "[RUNNER B4] Spring Boot pronto (PID=$SPRING_PID)."
            return 0
        fi
        sleep 1
    done
    echo "[ERRORE] Timeout avvio Spring Boot B4!"
    kill -9 "$SPRING_PID" 2>/dev/null || true
    exit 1
}

cleanup() {
    if [[ -n "${SPRING_PID:-}" ]]; then
        echo "[RUNNER B4] Arresto Spring Boot (PID=$SPRING_PID)..."
        kill "$SPRING_PID" 2>/dev/null || true
        wait "$SPRING_PID" 2>/dev/null || true
        SPRING_PID=""
    fi
    if [[ -n "${OLLAMA_PID:-}" ]]; then
        echo "[RUNNER B4] Arresto Ollama (PID=$OLLAMA_PID)..."
        kill "$OLLAMA_PID" 2>/dev/null || true
        wait "$OLLAMA_PID" 2>/dev/null || true
        OLLAMA_PID=""
    fi
}
trap cleanup EXIT

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

# 2. Esecuzione Ablazione Smart Retry (3B_CON_GUARDRAIL_SENZA_RETRY)
if [[ ! -f "$OUT_DIR/3B_CON_GUARDRAIL_SENZA_RETRY/heldout/summary.json" || \
      ! -f "$OUT_DIR/3B_CON_GUARDRAIL_SENZA_RETRY/heldout_adversarial/summary.json" || \
      ! -f "$OUT_DIR/3B_CON_GUARDRAIL_SENZA_RETRY/dev/summary.json" || \
      ! -f "$OUT_DIR/3B_CON_GUARDRAIL_SENZA_RETRY/dev_adversarial/summary.json" ]]; then
    ensure_ollama
    start_spring_no_retry 18084 "risultati/test2/benchmark_workspace/spring_3b_no_retry.log"
    run_cell_if_missing orchestrator "3B_CON_GUARDRAIL_SENZA_RETRY" "heldout" "$DS_DIR/heldout_dataset_v1.json" 18084 true true "qwen2.5:3b-instruct-q8_0"
    run_cell_if_missing orchestrator "3B_CON_GUARDRAIL_SENZA_RETRY" "heldout_adversarial" "$DS_DIR/heldout_adversarial_v1.json" 18084 true true "qwen2.5:3b-instruct-q8_0"
    run_cell_if_missing orchestrator "3B_CON_GUARDRAIL_SENZA_RETRY" "dev" "$DS_DIR/dataset_60_v2.json" 18084 true true "qwen2.5:3b-instruct-q8_0"
    run_cell_if_missing orchestrator "3B_CON_GUARDRAIL_SENZA_RETRY" "dev_adversarial" "$DS_DIR/adversarial_piva_set.json" 18084 true true "qwen2.5:3b-instruct-q8_0"
fi

echo "=== TUTTE LE RUN DELLA BATTERIA 4 COMPLETATE CON SUCCESSO ==="
