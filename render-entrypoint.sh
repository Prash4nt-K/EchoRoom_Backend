#!/bin/sh
set -eu

sed \
	-e "s/__PORT__/${PORT:-10000}/g" \
	-e "s/__SERVER_PORT__/${SERVER_PORT:-8080}/g" \
	-e "s/__SOCKETIO_PORT__/${SOCKETIO_PORT:-9092}/g" \
	/app/nginx-render.conf.template > /tmp/nginx-render.conf

java $JAVA_OPTS -jar app.jar &
JAVA_PID="$!"

nginx -c /app/nginx.conf -g "daemon off;" &
NGINX_PID="$!"

term() {
	kill "$NGINX_PID" "$JAVA_PID" 2>/dev/null || true
	wait "$NGINX_PID" "$JAVA_PID" 2>/dev/null || true
}

trap term INT TERM

wait -n "$JAVA_PID" "$NGINX_PID"
term
