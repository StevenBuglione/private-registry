\set ON_ERROR_STOP on
\getenv registry_web_password REGISTRY_WEB_DB_PASSWORD
\getenv registry_indexer_password REGISTRY_INDEXER_DB_PASSWORD

ALTER ROLE registry_web PASSWORD :'registry_web_password';
ALTER ROLE registry_indexer PASSWORD :'registry_indexer_password';
