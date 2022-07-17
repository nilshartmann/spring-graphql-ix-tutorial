#! /bin/bash
docker run -it --rm --name zookeeper -p 18181:2181 quay.io/debezium/zookeeper:1.9