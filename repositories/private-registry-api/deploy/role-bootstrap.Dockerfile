FROM postgres:16-alpine

COPY deploy/bootstrap-runtime-roles.sql /bootstrap-runtime-roles.sql
