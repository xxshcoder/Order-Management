SHELL := /bin/bash
COMPOSE := docker compose

.PHONY: up down test build logs verify infra logs-core

## Start the whole platform (core + observability) in the background.
up:
	$(COMPOSE) --env-file .env -f deploy/docker-compose.yml up -d

## Start only infrastructure + services (no observability stack).
up-core:
	$(COMPOSE) --env-file .env -f deploy/docker-compose.yml --profile core up -d

## Stop and remove containers (data volumes are kept).
down:
	$(COMPOSE) --env-file .env -f deploy/docker-compose.yml down

## Stop and remove containers AND data volumes (destructive).
clean:
	$(COMPOSE) --env-file .env -f deploy/docker-compose.yml down -v

## Run unit tests (fast, no Docker required).
test:
	mvn -q test

## Run unit + integration tests (requires Docker for Testcontainers).
verify:
	mvn -q verify

## Build all jars, skipping tests.
build:
	mvn -q clean package -DskipTests

## Tail logs for all containers.
logs:
	$(COMPOSE) --env-file .env -f deploy/docker-compose.yml logs -f

## Tail logs for the four application services only.
logs-core:
	$(COMPOSE) --env-file .env -f deploy/docker-compose.yml logs -f api-gateway order-service inventory-service notification-service
