#!/usr/bin/env bash

# ==============================================================================
#  🎬 Movie Booking Microservices - Realtime Dev Mode Launcher (Services + Frontend)
# ==============================================================================

CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
MAGENTA='\033[0;35m'
BLUE='\033[0;34m'
WHITE='\033[1;37m'
NC='\033[0m'

echo -e "${CYAN}====================================================${NC}"
echo -e "${CYAN} 🎬 Starting Movie Booking Platform (Dev Mode)      ${NC}"
echo -e "${CYAN}====================================================${NC}"

echo -e "${GREEN}🚀 [1/3] Starting Infrastructure in Docker (MySQL, Redis, Kafka, Debezium)...${NC}"
docker compose up -d mysql redis kafka debezium kafka-ui

echo -e "${GREEN}📦 [2/3] Compiling common module...${NC}"
mvn compile -pl common -DskipTests -q

echo -e "${GREEN}🔥 [3/3] Launching 7 Microservices + Frontend (React/Vite :3000) with Hot Reload...${NC}"
echo -e "${YELLOW}💡 Press Ctrl+C at any time to stop all services.${NC}\n"

pids=()

run_service() {
    local service_name=$1
    local color=$2
    mvn spring-boot:run -pl "$service_name" 2>&1 | while IFS= read -r line; do
        printf "${color}[%-22s]${NC} %s\n" "$service_name" "$line"
    done &
    pids+=($!)
}

run_frontend() {
    local color=$1
    (cd frontend && npm run dev) 2>&1 | while IFS= read -r line; do
        printf "${color}[%-22s]${NC} %s\n" "frontend" "$line"
    done &
    pids+=($!)
}

cleanup() {
    echo -e "\n${RED}🛑 Stopping all services (Microservices + Frontend)...${NC}"
    for pid in "${pids[@]}"; do
        if kill -0 "$pid" 2>/dev/null; then
            kill -9 "$pid" 2>/dev/null
        fi
    done
    pkill -f "spring-boot:run" 2>/dev/null || true
    pkill -f "vite" 2>/dev/null || true
    echo -e "${GREEN}✓ All services stopped cleanly.${NC}"
    exit 0
}

trap cleanup INT TERM

run_service "gateway"                "$CYAN"
run_service "auth-service"           "$GREEN"
run_service "booking-service"        "$MAGENTA"
run_service "seat-service"           "$YELLOW"
run_service "movie-service"          "$BLUE"
run_service "payment-service"        "$RED"
run_service "ai-recommender-service" "$CYAN"
run_frontend                        "$WHITE"

wait
