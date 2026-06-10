APP     := ai-workspace-backend
MAIN    := ./cmd/server
BINARY  := bin/$(APP)

.PHONY: tidy run build swag

tidy:
	go mod tidy

run:
	go run $(MAIN) -config config.yaml

build:
	go build -o $(BINARY) $(MAIN)

swag:
	swag init -g cmd/server/main.go -o docs
