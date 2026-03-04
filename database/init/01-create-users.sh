#!/bin/sh
# Creates the application database user if it does not already exist.
# Uses POSTGRES_USER and POSTGRES_PASSWORD from the container environment.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<EOSQL
DO \$\$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_user WHERE usename = '${POSTGRES_USER}') THEN
      CREATE USER ${POSTGRES_USER} WITH PASSWORD '${POSTGRES_PASSWORD}';
      RAISE NOTICE 'User ${POSTGRES_USER} created successfully';
   ELSE
      RAISE NOTICE 'User ${POSTGRES_USER} already exists';
   END IF;
END \$\$;
EOSQL
