package com.medicalsys.profesionales.web;

import com.medicalsys.profesionales.domain.ProfessionalType;
import jakarta.validation.constraints.Size;

/**
 * Edicion parcial de la ficha del profesional.
 *
 * <p>Todos los campos son opcionales: null significa "no tocar". Solo se admiten los datos
 * descriptivos. Documento, matricula y cuenta de usuario quedan fuera a proposito: son la
 * identidad del profesional, tienen restricciones UNIQUE en la tabla y cambiarlas por PATCH
 * convertiria una edicion de perfil en una suplantacion. Si hubiera que corregirlos, es un
 * caso de alta nueva y baja del vinculo, no una edicion.
 */
public record UpdateProfessionalRequest(
        @Size(max = 120) String givenNames,
        @Size(max = 80) String paternalSurname,
        @Size(max = 80) String maternalSurname,
        ProfessionalType professionalType,
        @Size(max = 160) String title,
        @Size(max = 1000) String biography) { }
