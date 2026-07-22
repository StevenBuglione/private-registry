SHELL := /usr/bin/env bash
GRADLE := ./gradlew

.PHONY: format test build check quality-pr quality-nightly run compose-up compose-down compose-logs contracts clean

format:
	bash scripts/quality.sh format

test:
	$(GRADLE) test

check:
	bash scripts/quality.sh local

quality-pr:
	bash scripts/quality.sh pr

quality-nightly:
	bash scripts/quality.sh nightly

build:
	$(GRADLE) bootJar

run:
	SPRING_PROFILES_ACTIVE=local $(GRADLE) bootRun

compose-up:
	docker compose up --build --wait

compose-down:
	docker compose down

compose-logs:
	docker compose logs -f api

contracts:
	python3 scripts/validate-contracts.py

clean:
	$(GRADLE) clean
