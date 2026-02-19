








GRANT ALL PRIVILEGES ON SCHEMA accounts_schema TO bankuser;
GRANT ALL PRIVILEGES ON SCHEMA auth_schema TO bankuser;
GRANT ALL PRIVILEGES ON SCHEMA keycloak_schema TO bankuser;



ALTER DEFAULT PRIVILEGES IN SCHEMA accounts_schema GRANT ALL ON TABLES TO bankuser;
ALTER DEFAULT PRIVILEGES IN SCHEMA accounts_schema GRANT ALL ON SEQUENCES TO bankuser;

ALTER DEFAULT PRIVILEGES IN SCHEMA auth_schema GRANT ALL ON TABLES TO bankuser;
ALTER DEFAULT PRIVILEGES IN SCHEMA auth_schema GRANT ALL ON SEQUENCES TO bankuser;

ALTER DEFAULT PRIVILEGES IN SCHEMA keycloak_schema GRANT ALL ON TABLES TO bankuser;
ALTER DEFAULT PRIVILEGES IN SCHEMA keycloak_schema GRANT ALL ON SEQUENCES TO bankuser;



ALTER USER bankuser SET search_path TO accounts_schema, auth_schema, keycloak_schema, public;


DO
$$
BEGIN
   RAISE NOTICE 'Privileges granted successfully:';
   RAISE NOTICE '  - bankuser: ALL on accounts_schema';
   RAISE NOTICE '  - bankuser: ALL on auth_schema';
   RAISE NOTICE '  - bankuser: ALL on keycloak_schema';
   RAISE NOTICE 'Search path configured: accounts_schema, auth_schema, keycloak_schema, public';
END
$$;
