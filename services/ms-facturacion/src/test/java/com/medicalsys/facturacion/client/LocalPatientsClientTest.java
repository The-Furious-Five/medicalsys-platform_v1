package com.medicalsys.facturacion.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.medicalsys.facturacion.support.TestFixtures;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El DTO fiscal local es ficticio, evidente y estable para el mismo paciente. */
class LocalPatientsClientTest {

    private final LocalPatientsClient client = new LocalPatientsClient();

    @Test
    @DisplayName("el mismo paciente produce siempre el mismo cliente fiscal")
    void isDeterministic() {
        FiscalCustomer first = client.fiscalCustomer(TestFixtures.ORGANIZATION_ID, TestFixtures.PATIENT_ID);
        FiscalCustomer second = client.fiscalCustomer(TestFixtures.ORGANIZATION_ID, TestFixtures.PATIENT_ID);

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("los datos son evidentemente ficticios y caben en las columnas de invoice")
    void producesObviouslyFictitiousData() {
        FiscalCustomer customer = client.fiscalCustomer(TestFixtures.ORGANIZATION_ID, TestFixtures.PATIENT_ID);

        assertThat(customer.legalName()).startsWith("PACIENTE DEMO ").hasSizeLessThanOrEqualTo(200);
        assertThat(customer.documentNumber()).hasSize(8).containsOnlyDigits();
        assertThat(customer.identityDocumentTypeCode()).isEqualTo(1);
        assertThat(customer.normalizedComplement()).isNull();
    }

    @Test
    @DisplayName("dos pacientes distintos no comparten documento")
    void differentPatientsGetDifferentDocuments() {
        FiscalCustomer one = client.fiscalCustomer(TestFixtures.ORGANIZATION_ID, TestFixtures.PATIENT_ID);
        FiscalCustomer other = client.fiscalCustomer(TestFixtures.ORGANIZATION_ID,
                UUID.fromString("00000000-0000-4000-8000-0000000000b2"));

        assertThat(other.documentNumber()).isNotEqualTo(one.documentNumber());
    }
}
