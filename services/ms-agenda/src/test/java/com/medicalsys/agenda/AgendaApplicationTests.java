package com.medicalsys.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import com.medicalsys.agenda.domain.AppointmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Maquina de estados de la cita. No requiere contexto de Spring ni base de datos: el
 * antisolapamiento y las migraciones se verifican contra PostgreSQL real (ver README del servicio).
 */
class AgendaApplicationTests {

    @Test
    @DisplayName("el recorrido normal de una cita atendida es valido paso a paso")
    void happyPathIsAllowed() {
        assertThat(AppointmentStatus.REQUESTED.canMoveTo(AppointmentStatus.CONFIRMED)).isTrue();
        assertThat(AppointmentStatus.CONFIRMED.canMoveTo(AppointmentStatus.WAITING)).isTrue();
        assertThat(AppointmentStatus.WAITING.canMoveTo(AppointmentStatus.IN_PROGRESS)).isTrue();
        assertThat(AppointmentStatus.IN_PROGRESS.canMoveTo(AppointmentStatus.ATTENDED)).isTrue();
    }

    @Test
    @DisplayName("no se puede saltar de solicitada a en progreso ni retroceder de atendida")
    void invalidJumpsAreRejected() {
        assertThat(AppointmentStatus.REQUESTED.canMoveTo(AppointmentStatus.IN_PROGRESS)).isFalse();
        assertThat(AppointmentStatus.REQUESTED.canMoveTo(AppointmentStatus.ATTENDED)).isFalse();
        assertThat(AppointmentStatus.ATTENDED.canMoveTo(AppointmentStatus.CANCELLED)).isFalse();
        assertThat(AppointmentStatus.CANCELLED.canMoveTo(AppointmentStatus.CONFIRMED)).isFalse();
    }

    @Test
    @DisplayName("los estados terminales no admiten transiciones")
    void terminalStatesAreFinal() {
        assertThat(AppointmentStatus.ATTENDED.isFinal()).isTrue();
        assertThat(AppointmentStatus.CANCELLED.isFinal()).isTrue();
        assertThat(AppointmentStatus.NO_SHOW.isFinal()).isTrue();
        assertThat(AppointmentStatus.RESCHEDULED.isFinal()).isTrue();
        assertThat(AppointmentStatus.REQUESTED.isFinal()).isFalse();
    }

    @Test
    @DisplayName("los estados canceladas y reprogramadas liberan la franja del profesional")
    void cancelledAndRescheduledDoNotBlockTheAgenda() {
        assertThat(AppointmentStatus.BLOCKING).doesNotContain(
                AppointmentStatus.CANCELLED, AppointmentStatus.RESCHEDULED);
        assertThat(AppointmentStatus.BLOCKING).contains(
                AppointmentStatus.REQUESTED, AppointmentStatus.CONFIRMED, AppointmentStatus.IN_PROGRESS);
    }
}
