#! /bin/bash

docker run -it --rm --name kafka -p 18092:9092 --link zookeeper:zookeeper quay.io/debezium/kafka:1.9