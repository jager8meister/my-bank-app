









CREATE SCHEMA IF NOT EXISTS accounts_schema;
COMMENT ON SCHEMA accounts_schema IS 'Accounts Service - User accounts and balances';



CREATE SCHEMA IF NOT EXISTS auth_schema;
COMMENT ON SCHEMA auth_schema IS 'Auth Service - Authentication and authorization data';



CREATE SCHEMA IF NOT EXISTS keycloak_schema;
COMMENT ON SCHEMA keycloak_schema IS 'Keycloak - Identity and Access Management';


DO
$$
BEGIN
   RAISE NOTICE 'All schemas created successfully:';
   RAISE NOTICE '  - accounts_schema (Accounts Service)';
   RAISE NOTICE '  - auth_schema (Auth Service)';
   RAISE NOTICE '  - keycloak_schema (Keycloak IAM)';
END
$$;
