package com.medicalsys.identidad.config;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "medicalsys.identity")
public class IdentityProperties {

    private final Jwt jwt = new Jwt();
    private final Lockout lockout = new Lockout();
    private final DemoUsers demoUsers = new DemoUsers();
    private final PlatformAdmin platformAdmin = new PlatformAdmin();

    public Jwt getJwt() {
        return jwt;
    }

    public Lockout getLockout() {
        return lockout;
    }

    public DemoUsers getDemoUsers() {
        return demoUsers;
    }

    public PlatformAdmin getPlatformAdmin() {
        return platformAdmin;
    }

    public static class Jwt {
        private String issuer = "medicalsys-ms-identidad";
        private String secret;
        private Duration accessTokenTtl = Duration.ofHours(1);

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }
    }

    public static class Lockout {
        private int maxFailedAttempts = 5;
        private Duration lockDuration = Duration.ofMinutes(15);

        public int getMaxFailedAttempts() {
            return maxFailedAttempts;
        }

        public void setMaxFailedAttempts(int maxFailedAttempts) {
            this.maxFailedAttempts = maxFailedAttempts;
        }

        public Duration getLockDuration() {
            return lockDuration;
        }

        public void setLockDuration(Duration lockDuration) {
            this.lockDuration = lockDuration;
        }
    }

    public static class DemoUsers {
        private boolean enabled = true;
        private UUID organizationPublicId;
        private String password = "Demo1234!";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public UUID getOrganizationPublicId() {
            return organizationPublicId;
        }

        public void setOrganizationPublicId(UUID organizationPublicId) {
            this.organizationPublicId = organizationPublicId;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    /** Cuenta bootstrap para operar la plataforma y dar de alta nuevos tenants. */
    public static class PlatformAdmin {
        private boolean enabled;
        private UUID publicId = UUID.fromString("00000000-0000-4000-8000-000000000900");
        private UUID organizationPublicId;
        private String email = "platform.admin@medicalsys.demo";
        private String fullName = "Administrador de MedicalSys";
        private String password;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public UUID getPublicId() { return publicId; }
        public void setPublicId(UUID publicId) { this.publicId = publicId; }
        public UUID getOrganizationPublicId() { return organizationPublicId; }
        public void setOrganizationPublicId(UUID organizationPublicId) {
            this.organizationPublicId = organizationPublicId;
        }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
