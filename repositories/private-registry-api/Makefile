SHELL := /usr/bin/env bash
GRADLE := ./gradlew

.PHONY: test build check run compose-up compose-down compose-logs contracts clean

test:
	$(GRADLE) test

check:
	$(GRADLE) check

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
