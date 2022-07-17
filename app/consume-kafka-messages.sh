#! /bin/bash

docker-compose -f docker-compose.yml exec kafka /kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server kafka:9092 \
    --from-beginning \
    --property print.key=true \
    --topic dbserver1.publy_db.comments

#docker exec kafka /kafka/bin/kafka-console-consumer.sh \
#    --bootstrap-server localhost:18092 \
#    --from-beginning \
#    --property print.key=true \
#    --topic dbserver1.publy_db.comments