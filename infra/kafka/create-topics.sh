#!/bin/bash
set -euo pipefail

bootstrap_server="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"

create_topic() {
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "${bootstrap_server}" \
    --create --if-not-exists --topic "$1" --partitions "$2" --replication-factor 1 \
    ${3:+--config cleanup.policy="$3"}
}

create_topic tenencia.organizacion 6 compact
create_topic pacientes.paciente 6 compact
create_topic pacientes.preferencia 6 compact
create_topic agenda.cita 6 delete
create_topic historial.resultado 6 delete
create_topic facturacion.factura 6 delete
create_topic marketing.campana 6 delete
create_topic notificaciones.mensaje 6 delete
create_topic dlq.medicalsys 3 delete
