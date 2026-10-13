package com.medicalsys.identidad.service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Year;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

/**
 * User ID estándar MS-AA-NNNNN (IAM-04, decisión de C-01): MS de MedicalSys, AA los dos últimos
 * dígitos del año de alta y NNNNN un correlativo de user_code_sequence, que nunca se reutiliza.
 */
@Component
public class UserCodeGenerator {

    /** Código emitido y la fila de la secuencia, para anotar después a qué cuenta se asignó. */
    public record IssuedCode(long sequenceId, String code) { }

    private final JdbcTemplate jdbc;

    public UserCodeGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public IssuedCode next() {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO user_code_sequence (issued_at) VALUES (CURRENT_TIMESTAMP)",
                    Statement.RETURN_GENERATED_KEYS);
            return statement;
        }, key);
        long sequence = key.getKey().longValue();
        return new IssuedCode(sequence, format(Year.now(ZoneOffset.UTC).getValue(), sequence));
    }

    public void assign(IssuedCode code, Long userAccountId) {
        jdbc.update("UPDATE user_code_sequence SET user_account_id = ? WHERE id = ?", userAccountId, code.sequenceId());
    }

    static String format(int year, long sequence) {
        return String.format("MS-%02d-%05d", year % 100, sequence);
    }
}
