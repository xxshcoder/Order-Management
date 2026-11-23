#!/bin/sh
# One-shot Kafka topic bootstrap for the Compose stack.
# Creates the five saga topics plus a .DLT topic for each, with 3 partitions.
# Invoked by the `kafka-init` service in deploy/docker-compose.yml.

set -eu

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
PARTITIONS="${TOPIC_PARTITIONS:-3}"
REPLICATION="${TOPIC_REPLICATION_FACTOR:-1}"
KAFKA_BIN="${KAFKA_BIN:-/opt/kafka/bin}"

TOPICS="order.created stock.reserved stock.rejected order.confirmed order.cancelled"

echo "Waiting for Kafka broker at ${BOOTSTRAP} ..."
attempt=0
until "${KAFKA_BIN}/kafka-topics.sh" --bootstrap-server "${BOOTSTRAP}" --list >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  if [ "${attempt}" -ge 60 ]; then
    echo "ERROR: Kafka did not become available after 60 attempts." >&2
    exit 1
  fi
  sleep 2
done

echo "Broker is available. Creating topics (partitions=${PARTITIONS}, replication=${REPLICATION})."
for base in ${TOPICS}; do
  for topic in "${base}" "${base}.DLT"; do
    "${KAFKA_BIN}/kafka-topics.sh" \
      --bootstrap-server "${BOOTSTRAP}" \
      --create --if-not-exists \
      --topic "${topic}" \
      --partitions "${PARTITIONS}" \
      --replication-factor "${REPLICATION}"
  done
done

echo "Topics present on the broker:"
"${KAFKA_BIN}/kafka-topics.sh" --bootstrap-server "${BOOTSTRAP}" --list
echo "Kafka topic initialisation complete."
