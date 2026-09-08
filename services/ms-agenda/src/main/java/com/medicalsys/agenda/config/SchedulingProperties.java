package com.medicalsys.agenda.config;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "medicalsys.scheduling")
public class SchedulingProperties {

    /** Zona horaria de negocio: schedule_availability guarda TIME local, no instantes. */
    private ZoneId zone = ZoneId.of("America/La_Paz");

    private final Validation validation = new Validation();
    private final Slots slots = new Slots();
    private final Notifications notifications = new Notifications();

    public ZoneId getZone() {
        return zone;
    }

    public void setZone(ZoneId zone) {
        this.zone = zone;
    }

    public Validation getValidation() {
        return validation;
    }

    public Slots getSlots() {
        return slots;
    }

    public Notifications getNotifications() {
        return notifications;
    }

    public static class Validation {
        private boolean enabled;
        private String patientsBaseUrl = "http://ms-pacientes:8085";
        private String professionalsBaseUrl = "http://ms-profesionales:8086";
        private Duration timeout = Duration.ofSeconds(3);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPatientsBaseUrl() {
            return patientsBaseUrl;
        }

        public void setPatientsBaseUrl(String patientsBaseUrl) {
            this.patientsBaseUrl = patientsBaseUrl;
        }

        public String getProfessionalsBaseUrl() {
            return professionalsBaseUrl;
        }

        public void setProfessionalsBaseUrl(String professionalsBaseUrl) {
            this.professionalsBaseUrl = professionalsBaseUrl;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    public static class Slots {
        private int maxDaysAhead = 120;

        public int getMaxDaysAhead() {
            return maxDaysAhead;
        }

        public void setMaxDaysAhead(int maxDaysAhead) {
            this.maxDaysAhead = maxDaysAhead;
        }
    }

    public static class Notifications {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
