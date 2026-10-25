package com.medicalsys.identidad.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.medicalsys.identidad.repository.UserAccountRepository;
import com.medicalsys.identidad.service.UsernameGenerator.NameParts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** IAM-07: el estándar nombre.apellido.inicial, sin tildes ni partículas, único y estable. */
class UsernameGeneratorTest {

    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final UsernameGenerator generator = new UsernameGenerator(users);

    @Test
    @DisplayName("primer nombre, primer apellido y la inicial del segundo")
    void buildsTheStandardUsername() {
        assertThat(UsernameGenerator.base(new NameParts("Ricardo", "Salazar", "Peredo"))).isEqualTo("ricardo.salazar.p");
        assertThat(UsernameGenerator.base(new NameParts("María José", "Núñez", "del Castillo"))).isEqualTo("maria.nunez.c");
        assertThat(UsernameGenerator.base(new NameParts("Juan", "Pérez", null))).isEqualTo("juan.perez");
        assertThat(UsernameGenerator.base(new NameParts("Iñaki", "de la Cruz", "O'Brien"))).isEqualTo("inaki.cruz.o");
        assertThat(UsernameGenerator.base(new NameParts("Ana", "San Martín-Ríos", ""))).isEqualTo("ana.sanmartinrios");
    }

    @Test
    @DisplayName("nombre completo en un campo: los dos últimos bloques son los apellidos")
    void parsesAFullName() {
        assertThat(UsernameGenerator.base(UsernameGenerator.parse("Patricia Loayza Bernal"))).isEqualTo("patricia.loayza.b");
        assertThat(UsernameGenerator.base(UsernameGenerator.parse("Lucía Quispe de Rojas"))).isEqualTo("lucia.quispe.r");
        assertThat(UsernameGenerator.base(UsernameGenerator.parse("Elena Cruz"))).isEqualTo("elena.cruz");
        assertThat(UsernameGenerator.base(UsernameGenerator.parse("Administrador de MedicalSys")))
                .isEqualTo("administrador.medicalsys");
    }

    @Test
    @DisplayName("si el usuario ya existe se agrega un número")
    void resolvesCollisionsAndReservedNames() {
        when(users.existsByUsername("ricardo.salazar.p")).thenReturn(true);
        when(users.existsByUsername("ricardo.salazar.p2")).thenReturn(true);

        assertThat(generator.next(new NameParts("Ricardo", "Salazar", "Peredo"))).isEqualTo("ricardo.salazar.p3");
    }

    @Test
    @DisplayName("sin apellido no hay usuario: el alta lo rechaza con 400")
    void requiresAGivenNameAndASurname() {
        when(users.existsByUsername(anyString())).thenReturn(false);

        assertThatThrownBy(() -> generator.next(new NameParts("Lucía", "", "")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("el largo nunca supera la columna, ni con el número de desempate")
    void staysWithinTheColumnLength() {
        String base = UsernameGenerator.base(new NameParts("Maximilianoalejandro", "Sotomayorgutierrezdelvalle", "Zeta"));

        assertThat(base.length() + 3).isLessThanOrEqualTo(UsernameGenerator.MAX_LENGTH);
        assertThat(base).matches("^[a-z][a-z0-9.]{2,39}$");
    }
}
