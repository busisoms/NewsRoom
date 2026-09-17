#!/usr/bin/env bash
#
# Builds NewsRoom, starts the app and the ngrok tunnel, and waits until the
# webhook is reachable from the internet. Press Ctrl+C to stop everything.

# Stop on errors, on unset variables, and on failures inside pipes
set -euo pipefail

# Run from the project folder, wherever the script is called from
cd "$(dirname "$0")"

NGROK_DOMAIN="germicide-anime-cubbyhole.ngrok-free.dev"
APP_JAR="target/newsroom-bot-1.0-SNAPSHOT.jar"
NGROK_LOG="target/ngrok.log"
HEALTH_URL="https://$NGROK_DOMAIN/health"


load_env() {
    if [ ! -f .env ]; then
        echo "Missing .env file. Copy .env.example to .env and fill it in."
        exit 1
    fi

    set -a
    source .env
    set +a
}

build() {
    echo "Building..."
    mvn -q package
}

stop_background_processes() {
    local running
    running=$(jobs -p)

    if [ -n "$running" ]; then
        echo "Stopping app and ngrok..."
        kill $running 2>/dev/null || true
    fi
}

start_ngrok() {
    ngrok http --url="$NGROK_DOMAIN" 7000 --log=stdout > "$NGROK_LOG" &
}

start_app() {
    java -jar "$APP_JAR" &
    APP_PID=$!
}

wait_until_healthy() {
    echo "Waiting for $HEALTH_URL ..."

    for attempt in $(seq 1 30); do
        if curl --silent --fail "$HEALTH_URL" > /dev/null; then
            return 0
        fi
        sleep 1
    done

    echo "Health check failed after 30 seconds. Check the app output above and $NGROK_LOG"
    exit 1
}


load_env
build

# Clean up on any exit; Ctrl+C (INT) and TERM exit first so cleanup runs once
trap stop_background_processes EXIT
trap "exit 130" INT TERM

start_ngrok
start_app
wait_until_healthy

echo
echo "NewsRoom is up"
echo "  Webhook:   https://$NGROK_DOMAIN/webhook"
echo "  Inspector: http://127.0.0.1:4040"
echo "  Press Ctrl+C to stop"
echo

wait "$APP_PID"
