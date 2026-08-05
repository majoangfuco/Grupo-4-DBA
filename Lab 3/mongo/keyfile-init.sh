#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════
# keyfile-init.sh  ·  Generación del keyfile del Replica Set
#
# Los miembros de un replica set con autenticación habilitada se
# autentican entre sí mediante un keyfile compartido. MongoDB exige que
# el archivo tenga permisos 400 y pertenezca al usuario que corre mongod
# (uid 999 en la imagen oficial); si no, mongod se niega a arrancar.
#
# Por eso el keyfile NO se versiona ni se monta desde el host: se genera
# dentro de un volumen de Docker, donde los permisos POSIX sí se respetan
# (un bind mount desde Windows/WSL rompe el chmod 400).
# ═══════════════════════════════════════════════════════════════
set -euo pipefail

KEYFILE=/keyfile/mongo-keyfile

if [ ! -s "$KEYFILE" ]; then
    echo "[keyfile-init] Generando keyfile nuevo en $KEYFILE"
    openssl rand -base64 756 > "$KEYFILE"
else
    echo "[keyfile-init] Keyfile ya existente, se reutiliza"
fi

chown 999:999 "$KEYFILE"
chmod 400 "$KEYFILE"

echo "[keyfile-init] Listo: $(ls -l "$KEYFILE")"
