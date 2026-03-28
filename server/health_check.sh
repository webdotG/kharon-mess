#!/bin/bash
RESULT=$(docker exec kharon-server wget -q -O- http://127.0.0.1:3000/health 2>/dev/null)
if [ -z "$RESULT" ]; then
    # Проверяем что контейнер вообще запущен
    STATUS=$(docker inspect -f '{{.State.Status}}' kharon-server 2>/dev/null)
    if [ "$STATUS" = "running" ]; then
        # Контейнер запущен но не отвечает — ждём ещё
        STARTED=$(docker inspect -f '{{.State.StartedAt}}' kharon-server)
        echo "$(date): [~] server running but not responding yet (started: $STARTED)" >> ~/health.log
    else
        echo "$(date): [!] Kharon server DOWN — restarting" >> ~/health.log
        cd ~/aProject/mes/server && docker compose up -d
    fi
else
    echo "$(date): [*] OK $RESULT" >> ~/health.log
fi
tail -1000 ~/health.log > ~/health.log.tmp && mv ~/health.log.tmp ~/health.log
