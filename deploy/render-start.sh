#!/usr/bin/env bash
set -euo pipefail

export SOM_DB_URL="jdbc:postgresql://${SOM_DB_HOST:?}:${SOM_DB_PORT:-5432}/${SOM_DB_NAME:?}"
/opt/venv/bin/python -m uvicorn som_engine.api:app --host 127.0.0.1 --port 8000 &
java -Xmx192m -XX:ActiveProcessorCount=2 -Dserver.port="${PORT:-8080}" -jar /app/som.jar &
wait -n
