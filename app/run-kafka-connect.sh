#! /bin/sh

docker run -it --rm --name connect -p 18083:8083 \
  -e GROUP_ID=1 \
  -e CONFIG_STORAGE_TOPIC=my_connect_configs \
  -e OFFSET_STORAGE_TOPIC=my_connect_offsets \
  -e STATUS_STORAGE_TOPIC=my_connect_statuses \
  --link zookeeper:zookeeper --link kafka:kafka --link publy_db:publy_db quay.io/debezium/connect:1.9