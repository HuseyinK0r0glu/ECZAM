# ECZAM developer task runner.  Run `make help` to list targets.
.DEFAULT_GOAL := help
SHELL := /bin/bash

help: ## Show this help
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

# --- Database / stack (Docker) ---
db-up: ## Start Postgres+pgvector only (for running the API from your IDE)
	cd backend && docker compose up -d db
db-down: ## Stop the database
	cd backend && docker compose stop db
up: ## Start the full stack (db + backend) with a build
	cd backend && docker compose up --build
down: ## Stop and remove the stack
	cd backend && docker compose down
adminer: ## DB browser at http://localhost:8081
	cd backend && docker compose --profile tools up -d adminer

# --- Backend ---
backend: ## Run the API from source (needs a DB on :5432)
	cd backend && ./mvnw spring-boot:run
backend-test: ## Run backend tests (Testcontainers)
	cd backend && ./mvnw -B verify
backend-cov: ## Tests + JaCoCo report (backend/target/site/jacoco/index.html)
	cd backend && ./mvnw -B verify
seed-sample: ## Run the API with a small sample medicine catalog seeded
	cd backend && SEED_SAMPLE=true ./mvnw spring-boot:run

# --- Frontend (Flutter) ---
flutter-get: ## Fetch Flutter dependencies
	cd frontend && flutter pub get
flutter-analyze: ## Static analysis
	cd frontend && flutter analyze
flutter-test: ## Run Flutter unit/widget tests with coverage
	cd frontend && flutter test --coverage
flutter-run: ## Run on a device/emulator against the local backend
	cd frontend && flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1

# --- Misc ---
clean: ## Remove build artifacts
	-cd backend && ./mvnw -q clean
	-cd frontend && flutter clean

.PHONY: help db-up db-down up down adminer backend backend-test backend-cov \
        seed-sample flutter-get flutter-analyze flutter-test flutter-run clean
