#! /bin/bash

export DEBEZIUM_VERSION=1.9
docker-compose -f docker-compose.yml $1 $2 $3 $4