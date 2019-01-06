#!/usr/bin/env bash

DB_NAME=test_${DB_NAME}

# recreate database to avoid chained failures
echo ${DB_PASSWORD} | mysql -h ${DB_HOST} -u ${DB_USERNAME} -p -e \
"DROP DATABASE IF EXISTS ${DB_NAME}; CREATE DATABASE ${DB_NAME} CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

# uncomment the next line if you are importing the tables from an existing connection
#bin/cake migrations migrate --no-lock -c test