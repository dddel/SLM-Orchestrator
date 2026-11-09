# ============================================================================
# Makefile - SLM Orchestrator (Backend + Frontend Docker Runner)
# ============================================================================

.DEFAULT_GOAL := up
.PHONY: up start down stop restart logs ps build clean help

# Rilevamento automatico strumento compose (docker compose, podman-compose o docker-compose)
DOCKER_COMPOSE ?= $(shell if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then echo "docker compose"; elif command -v podman-compose >/dev/null 2>&1; then echo "podman-compose"; else echo "docker compose"; fi)

FRONTEND_URL := http://localhost:4200
BACKEND_URL  := http://localhost:8080
OLLAMA_DATA_DIR ?=

# Colori terminale per output pulito ed elegante
CYAN    := \033[0;36m
GREEN   := \033[0;32m
YELLOW  := \033[0;33m
BLUE    := \033[0;34m
BOLD    := \033[1m
RESET   := \033[0m

## up: Avvia backend, frontend e motore SLM con Docker e restituisce il link del frontend
up: start

start:
	@printf "$(BLUE)$(BOLD)Avvio di SLM Orchestrator (Backend + Frontend + Ollama) con Docker...$(RESET)\n"
	@set -eu; \
	default_dir="$${OLLAMA_DATA_DIR:-$$HOME/.ollama}"; \
	if [ -n "$${OLLAMA_DATA_DIR:-}" ]; then \
		selected_dir="$$OLLAMA_DATA_DIR"; \
	else \
		printf "Directory dati Ollama [$$default_dir]: "; \
		IFS= read -r selected_dir; \
	fi; \
	selected_dir="$${selected_dir:-$$default_dir}"; \
	case "$$selected_dir" in \
		"~") selected_dir="$$HOME/.ollama" ;; \
		"~/"*) selected_dir="$$HOME/$${selected_dir#~/}" ;; \
	esac; \
	case "$$selected_dir" in \
		/*) ;; \
		*) printf "$(YELLOW)Il percorso deve essere assoluto.$(RESET)\n" >&2; exit 1 ;; \
	esac; \
	mkdir -p "$$selected_dir"; \
	printf "$(CYAN)Uso directory Ollama: %s$(RESET)\n" "$$selected_dir"; \
	OLLAMA_DATA_DIR="$$selected_dir" $(DOCKER_COMPOSE) up -d --build
	@printf "$(YELLOW)Attesa disponibilità dei servizi...$(RESET)\n"
	@for i in $$(seq 1 30); do \
		if curl -fs -o /dev/null $(FRONTEND_URL) 2>/dev/null; then \
			break; \
		fi; \
		sleep 1; \
	done
	@printf "\n"
	@printf "$(GREEN)$(BOLD)========================================================================$(RESET)\n"
	@printf "$(GREEN)$(BOLD)  🚀 SLM Orchestrator è attivo e pronto!$(RESET)\n"
	@printf "\n"
	@printf "  👉 $(BOLD)Frontend SPA:$(RESET)   $(CYAN)$(BOLD)$(FRONTEND_URL)$(RESET)\n"
	@printf "  👉 $(BOLD)Backend API:$(RESET)    $(CYAN)$(BACKEND_URL)/swagger-ui.html$(RESET)\n"
	@printf "$(GREEN)$(BOLD)========================================================================$(RESET)\n"
	@printf "\n"

## down: Ferma tutti i container Docker in esecuzione
down: stop

stop:
	@printf "$(YELLOW)Arresto dei container in corso...$(RESET)\n"
	@$(DOCKER_COMPOSE) down
	@printf "$(GREEN)Tutti i container sono stati arrestati.$(RESET)\n"

## restart: Riavvia tutti i container e restituisce il link del frontend
restart:
	@printf "$(YELLOW)Riavvio dei servizi...$(RESET)\n"
	@$(MAKE) up

## logs: Visualizza i log in tempo reale di tutti i container
logs:
	@$(DOCKER_COMPOSE) logs -f

## ps: Mostra lo stato dei container
ps:
	@$(DOCKER_COMPOSE) ps

## build: Ricostruisce le immagini Docker di backend e frontend
build:
	@printf "$(BLUE)Ricompilazione immagini Docker di backend e frontend...$(RESET)\n"
	@$(DOCKER_COMPOSE) build

## clean: Arresta i container e rimuove le risorse Compose senza cancellare i pesi locali
clean:
	@printf "$(YELLOW)Pulizia completa container e volumi...$(RESET)\n"
	@$(DOCKER_COMPOSE) down -v --remove-orphans
	@printf "$(GREEN)Pulizia completata; la directory dati di Ollama non è stata modificata.$(RESET)\n"

## help: Mostra questo messaggio di aiuto
help:
	@printf "$(BOLD)SLM Orchestrator - Comandi disponibili:$(RESET)\n"
	@printf "  $(CYAN)make$(RESET) oppure $(CYAN)make up$(RESET)   - Avvia backend e frontend con Docker e mostra il link\n"
	@printf "  $(CYAN)make down$(RESET)          - Ferma tutti i container\n"
	@printf "  $(CYAN)make restart$(RESET)       - Riavvia i container e mostra il link\n"
	@printf "  $(CYAN)make logs$(RESET)          - Segue i log dei servizi in tempo reale\n"
	@printf "  $(CYAN)make ps$(RESET)            - Mostra lo stato dei servizi\n"
	@printf "  $(CYAN)make build$(RESET)         - Ricostruisce le immagini Docker\n"
	@printf "  $(CYAN)make clean$(RESET)         - Rimuove container e risorse Compose, non i pesi Ollama\n"
