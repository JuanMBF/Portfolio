# Atajos para el laboratorio. Sin make, cada comando se puede correr tal cual en la terminal.
LAB = docker compose run --rm -T lab
COUNT ?= 10000

.PHONY: up status load verify crash stepdown down test

up: ## Levanta los 3 nodos e inicializa el replica set
	docker compose up -d mongo1 mongo2 mongo3
	docker compose run --rm init

status: ## Rol, salud y retraso de cada nodo
	$(LAB) status

load: ## Inserta COUNT documentos y verifica que lleguen a los 3 nodos
	$(LAB) load --count $(COUNT)

verify: ## Compara cantidad de documentos y huella entre nodos
	$(LAB) verify

crash: ## Mata el primario de golpe mientras se escribe y mide la recuperación
	./scripts/crash-primary.sh

stepdown: ## Cambio de primario planificado (mantenimiento)
	$(LAB) failover --mode stepdown

down: ## Apaga todo y borra los volúmenes
	docker compose down -v

test: ## Tests unitarios de las herramientas
	python -m pytest -q tests
