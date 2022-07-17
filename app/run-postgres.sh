#! /bin/bash


# https://access.redhat.com/documentation/zh-cn/red_hat_integration/2020.q1/html/debezium_user_guide/debezium-connector-for-postgresql

docker run -it --rm --name publy_db -p 17432:5432 \
  -v ${PWD}/docker/initdb:/docker-entrypoint-initdb.d \
  -v ${PWD}/docker/db-data:/var/lib/postgresql/data \
  -e POSTGRES_PASSWORD=secretpw \
  -e POSTGRES_USER=klaus \
  -e POSTGRES_DB=publy_db \
  postgres:14.1-alpine \
  -c wal_level=logical \
  -c max_wal_senders=1 \
  -c max_replication_slots=1