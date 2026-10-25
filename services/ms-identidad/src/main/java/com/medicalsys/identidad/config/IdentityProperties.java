package com.medicalsys.identidad.config;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "medicalsys.identity")
public class IdentityProperties {

    private final Jwt jwt = new Jwt();
    private final Keycloak keycloak = new Keycloak();
    private final DemoUsers demoUsers = new DemoUsers();
    private final PlatformAdmin platformAdmin = new PlatformAdmin();
    /** ISO-01: primera cuenta OSI. Mismos campos que la de plataforma, otro rol y otra cuenta. */
    private final PlatformAdmin securityOfficer = new PlatformAdmin();
    private final Mfa mfa = new Mfa();

    public Jwt getJwt() {
        return jwt;
    }

    public Keycloak getKeycloak() {
        return keycloak;
    }

    public DemoUsers getDemoUsers() {
        return demoUsers;
    }

    public PlatformAdmin getPlatformAdmin() {
        return platformAdmin;
    }

    public PlatformAdmin getSecurityOfficer() {
        return securityOfficer;
    }

    public Mfa getMfa() {
        return mfa;
    }

    /**
     * Segundo factor TOTP (IAM-07), obligatorio para todas las cuentas. {@code encryptionKey} cifra los
     * secretos en la base (AES-256-GCM); sin ella se deriva de JWT_SECRET con otra etiqueta, para no
     * romper un despliegue que todavía no la define.
     */
    public static class Mfa {
        private String issuer = "MedicalSys";
        private String encryptionKey;
        private Duration challengeTtl = Duration.ofMinutes(5);
        private int maxAttemptsPerChallenge = 5;
        private int maxFailures = 10;
        private Duration failureWindow = Duration.ofMinutes(15);

        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public String getEncryptionKey() { return encryptionKey; }
        public void setEncryptionKey(String encryptionKey) { this.encryptionKey = encryptionKey; }
        public Duration getChallengeTtl() { return challengeTtl; }
        public void setChallengeTtl(Duration challengeTtl) { this.challengeTtl = challengeTtl; }
        public int getMaxAttemptsPerChallenge() { return maxAttemptsPerChallenge; }
        public void setMaxAttemptsPerChallenge(int maxAttemptsPerChallenge) {
            this.maxAttemptsPerChallenge = maxAttemptsPerChallenge;
        }
        public int getMaxFailures() { return maxFailures; }
        public void setMaxFailures(int maxFailures) { this.maxFailures = maxFailures; }
        public Duration getFailureWindow() { return failureWindow; }
        public void setFailureWindow(Duration failureWindow) { this.failureWindow = failureWindow; }
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

    /**
     * Cliente confidencial ms-identidad del realm medicalsys (IAM-03): valida credenciales con
     * Direct Access Grants y administra usuarios con su service account.
     */
    public static class Keycloak {
        private String baseUrl = "http://keycloak:8080";
        private String realm = "medicalsys";
        private String clientId = "ms-identidad";
        private String clientSecret;
        private Duration timeout = Duration.ofSeconds(5);

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getRealm() { return realm; }
        public void setRealm(String realm) { this.realm = realm; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
    }

    /**
     * Cuentas del centro de demostracion. No hay contrasena comun: cada una nace con una temporal
     * aleatoria propia. {@code rotatePasswords} se usa una vez, para retirar la contrasena comun de
     * bases sembradas por versiones anteriores.
     */
    public static class DemoUsers {
        private boolean enabled = true;
        private UUID organizationPublicId;
        private boolean rotatePasswords;

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

        public boolean isRotatePasswords() {
            return rotatePasswords;
        }

        public void setRotatePasswords(boolean rotatePasswords) {
            this.rotatePasswords = rotatePasswords;
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
