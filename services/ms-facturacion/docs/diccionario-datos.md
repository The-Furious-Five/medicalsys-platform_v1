# Diccionario de datos de `billing_db` — ms-facturación

Verificación de conformidad contra **MedicalSys_Diccionario_Datos_Microservicios_MVP.pdf, capítulo 11**
(«11. ms-facturacion — billing_db», páginas 60–69) y la matriz de referencias externas (páginas 71–72).

| | |
| --- | --- |
| Base lógica | `billing_db` |
| Motor | MySQL 8 |
| Tablas del diccionario | 12 |
| Tablas implementadas | 12 |
| Migraciones | `V1__create_schema.sql` (7 tablas) · `V2__create_remaining_fiscal_tables.sql` (5 tablas) |
| Estado | **Conforme.** Ver §14 para las tres desviaciones documentadas, todas originadas en el propio PDF. |

Este documento describe el esquema **realmente creado por Flyway**, no una intención. Los tipos,
defaults, índices y constraints de las tablas se contrastaron contra `information_schema` de un
MySQL 8 con ambas migraciones aplicadas.

## Cómo leer las tablas

| Marca | Significado |
| --- | --- |
| PK | Clave primaria local |
| UQ | Restricción de unicidad (sola o compuesta) |
| FK | Clave foránea **local**, dentro de `billing_db` |
| REF | UUID de otro servicio. **Sin FK física**; se valida por HTTP y lleva índice propio |

Convenciones del capítulo «Cómo leer este documento» del PDF que este esquema respeta:

- El `id` numérico es clave técnica local y nunca aparece en URLs ni contratos HTTP.
- Las entidades expuestas conservan un `public_id` UUID. En MySQL viaja como `BINARY(16)`.
- Ninguna FK cruza la frontera del servicio: `declaresOnlyLocalForeignKeys` lo comprueba.
- El dinero es `DECIMAL`, nunca `FLOAT` ni `DOUBLE`; en Java es `BigDecimal`.
- Fechas de negocio `DATE`, instantes en UTC, API en ISO-8601 con zona.
- Los registros fiscales se anulan o cambian de estado; no se borran físicamente.

---

## 1. `billing_configuration` — original: `configuracion_facturacion`

Configuración fiscal de una organización. Una fila por tenant.

| Campo | Tipo físico | Nulo | Default | Clave | Nota |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED AUTO_INCREMENT | NO | — | PK | |
| `organization_public_id` | BINARY(16) | NO | — | UQ · REF | → `organization.public_id` |
| `nit` | VARCHAR(15) | NO | — | | |
| `legal_name` | VARCHAR(180) | NO | — | | |
| `modality` | ENUM | NO | `ONLINE_COMPUTERIZED` | | |
| `environment` | ENUM | NO | `PILOT` | | |
| `encrypted_delegated_token` | VARBINARY(2048) | SÍ | — | | Token del SIN cifrado con DEK. En el MVP siempre `NULL` |
| `token_valid_until` | TIMESTAMP | SÍ | — | | |
| `encryption_key_public_id` | BINARY(16) | SÍ | — | REF | → `encryption_key.public_id` |
| `digital_certificate_public_id` | BINARY(16) | SÍ | — | REF | → `digital_certificate.public_id` |
| `vat_caption` | VARCHAR(300) | SÍ | — | | Leyenda IVA |
| `notification_email` | VARCHAR(160) | SÍ | — | | |
| `status` | ENUM | NO | `CONFIGURING` | | |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | | |
| `updated_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP ON UPDATE | | |

**Reglas.** `pk_billing_configuration` (id) · `uq_billing_configuration_organization_public_id` ·
`ix_billing_configuration_encryption_key_public_id` · `ix_billing_configuration_digital_certificate_public_id`.

**Valores controlados.**
`modality`: `ONLINE_ELECTRONIC`, `ONLINE_COMPUTERIZED`, `MANUAL` ·
`environment`: `PILOT`, `PRODUCTION` ·
`status`: `CONFIGURING`, `ACTIVE`, `SUSPENDED`.

---

## 2. `economic_activity` — original: `actividad_economica`

Actividad económica habilitada para facturar.

| Campo | Tipo físico | Nulo | Default | Clave | Nota |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED AUTO_INCREMENT | NO | — | PK | |
| `billing_configuration_id` | BIGINT UNSIGNED | NO | — | FK · UQ | → `billing_configuration.id` |
| `caeb_code` | VARCHAR(20) | NO | — | UQ | Código CAEB del SIN |
| `description` | VARCHAR(300) | NO | — | | |
| `type` | ENUM | NO | `SECONDARY` | | |
| `active` | BOOLEAN | NO | `TRUE` | | |

**Reglas.** `pk_economic_activity` (id) ·
`uq_economic_activity_billing_configuration_id_caeb_code` (billing_configuration_id, caeb_code) ·
`fk_economic_activity_billing_configuration` → `billing_configuration(id)` **ON DELETE CASCADE**.

**Valores controlados.** `type`: `PRIMARY`, `SECONDARY`.

---

## 3. `point_of_sale` — original: `punto_venta`

Punto de venta fiscal asociado a una sucursal.

| Campo | Tipo físico | Nulo | Default | Clave | Nota |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED AUTO_INCREMENT | NO | — | PK | |
| `organization_public_id` | BINARY(16) | NO | — | UQ · REF | → `organization.public_id` |
| `branch_public_id` | BINARY(16) | NO | — | REF | → `branch.public_id` |
| `tax_branch_code` | SMALLINT UNSIGNED | NO | — | UQ | `0` = casa matriz |
| `point_of_sale_code` | SMALLINT UNSIGNED | NO | — | UQ | |
| `name` | VARCHAR(120) | NO | — | | |
| `type` | ENUM | NO | `COLLECTION_DESK` | | |
| `status` | ENUM | NO | `ACTIVE` | | |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | | |

**Reglas.** `pk_point_of_sale` (id) ·
`uq_point_of_sale_organization_public_id_tax_branch_c_key` (organization_public_id, tax_branch_code, point_of_sale_code) ·
`ix_point_of_sale_branch_public_id_status`.

**Valores controlados.**
`type`: `COMMISSION_AGENT`, `COLLECTION_DESK`, `MOBILE`, `YPFB`, `ATM`, `OTHER` ·
`status`: `ACTIVE`, `CLOSED`.

---

## 4. `cuis` — original: `cuis`

Código Único de Inicio de Sistemas y su vigencia. En el MVP lo produce el adaptador SIN simulado.

| Campo | Tipo físico | Nulo | Default | Clave | Nota |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED AUTO_INCREMENT | NO | — | PK | |
| `point_of_sale_id` | BIGINT UNSIGNED | NO | — | FK · UQ | → `point_of_sale.id` |
| `code` | VARCHAR(30) | NO | — | UQ | |
| `valid_until` | DATE | NO | — | | Fecha de negocio, no instante |
| `requested_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | | |
| `status` | ENUM | NO | `ACTIVE` | | |

**Reglas.** `pk_cuis` (id) · `uq_cuis_point_of_sale_id_code` ·
`ix_cuis_point_of_sale_id_status_valid_until` · `fk_cuis_point_of_sale` → `point_of_sale(id)`.

**Valores controlados.** `status`: `ACTIVE`, `EXPIRED`.

---

## 5. `cufd` — original: `cufd`

Código Único de Facturación Diaria y su vigencia.

| Campo | Tipo físico | Nulo | Default | Clave | Nota |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED AUTO_INCREMENT | NO | — | PK | |
| `point_of_sale_id` | BIGINT UNSIGNED | NO | — | FK | → `point_of_sale.id` |
| `cuis_id` | BIGINT UNSIGNED | NO | — | FK | → `cuis.id` |
| `code` | VARCHAR(200) | NO | — | UQ | |
| `control_code` | VARCHAR(30) | NO | — | | |
| `address` | VARCHAR(250) | NO | — | | Dirección que se imprime en la factura |
| `valid_from` | TIMESTAMP | NO | — | | |
| `valid_until` | TIMESTAMP | NO | — | | |
| `requested_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | | |

**Reglas.** `pk_cufd` (id) · `uq_cufd_code` · `ix_cufd_point_of_sale_id_valid_until` ·
`ix_cufd_cuis_id` · `fk_cufd_point_of_sale` · `fk_cufd_cuis` ·
`ck_cufd_validity_range` CHECK `valid_until > valid_from`.

---

## 6. `document_sequence` — original: `correlativo_documento`

Contador bloqueable por tipo de documento y punto de venta. Es una tabla, no un `AUTO_INCREMENT`,
porque el correlativo fiscal pertenece al punto de venta y debe poder auditarse.

| Campo | Tipo físico | Nulo | Default | Clave | Nota |
| --- | --- | --- | --- | --- | --- |
| `point_of_sale_id` | BIGINT UNSIGNED | NO | — | PK · FK | → `point_of_sale.id` |
| `tax_document_type` | VARCHAR(30) | NO | — | PK | |
| `last_number` | BIGINT UNSIGNED | NO | `0` | | |
| `updated_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP ON UPDATE | | |

**Reglas.** `pk_document_sequence` (point_of_sale_id, tax_document_type) ·
`fk_document_sequence_point_of_sale` → `point_of_sale(id)`.

El servicio lo lee con `SELECT ... FOR UPDATE` dentro de la transacción de emisión; la garantía final
la impone `uq_invoice_point_of_sale_id_tax_document_type_invoice_number`.

---

## 7. `invoice` — original: `factura`

Cabecera fiscal y monetaria de una factura. Los datos del cliente son un *snapshot* congelado del DTO
fiscal mínimo de ms-pacientes: la factura no cambia si el paciente se edita después.

| Campo | Tipo físico | Nulo | Default | Clave | Nota |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED AUTO_INCREMENT | NO | — | PK | |
| `public_id` | BINARY(16) | NO | — | UQ | |
| `organization_public_id` | BINARY(16) | NO | — | REF | → `organization.public_id` |
| `branch_public_id` | BINARY(16) | NO | — | REF | → `branch.public_id` |
| `patient_public_id` | BINARY(16) | NO | — | REF | → `patient.public_id`. **Ver §14.1** |
| `point_of_sale_id` | BIGINT UNSIGNED | NO | — | FK · UQ | → `point_of_sale.id` |
| `cufd_id` | BIGINT UNSIGNED | NO | — | FK | → `cufd.id` |
| `tax_document_type` | ENUM | NO | `PURCHASE_SALE_INVOICE` | UQ | |
| `emission_type` | ENUM | NO | `ONLINE` | | |
| `invoice_number` | BIGINT UNSIGNED | NO | — | UQ | Correlativo por punto de venta |
| `cuf` | VARCHAR(80) | NO | — | UQ | Código Único de Factura, con dígito verificador |
| `cafc` | VARCHAR(30) | SÍ | — | | Solo en emisión fuera de línea |
| `identity_document_type_code` | TINYINT UNSIGNED | NO | `1` | | 1=CI, 2=CEX, 3=PAS, 4=OD, 5=NIT (catálogo SIN) |
| `customer_document_number` | VARCHAR(20) | NO | — | | |
| `customer_document_complement` | VARCHAR(5) | SÍ | — | | |
| `customer_legal_name` | VARCHAR(200) | NO | — | | |
| `customer_email` | VARCHAR(160) | SÍ | — | | |
| `exchange_rate` | DECIMAL(12,5) | NO | `1.00000` | | |
| `total_amount` | DECIMAL(14,2) | NO | — | | |
| `vat_taxable_amount` | DECIMAL(14,2) | NO | — | | |
| `total_discount_amount` | DECIMAL(14,2) | NO | `0.00` | | |
| `gift_card_amount` | DECIMAL(14,2) | NO | `0.00` | | |
| `currency_total_amount` | DECIMAL(14,2) | NO | — | | |
| `payment_method_code` | TINYINT UNSIGNED | NO | `1` | | 1=Efectivo, 2=Tarjeta, 7=Transferencia… (catálogo SIN) |
| `card_number_suffix` | VARCHAR(4) | SÍ | — | | Solo últimos 4 dígitos (PCI-DSS) |
| `issued_at` | TIMESTAMP | NO | — | | |
| `tax_receipt_code` | VARCHAR(50) | SÍ | — | | Código de recepción del SIN |
| `validated_at` | TIMESTAMP | SÍ | — | | |
| `caption` | VARCHAR(500) | SÍ | — | | En el MVP lleva `NO VALIDA TRIBUTARIAMENTE` |
| `qr_url` | VARCHAR(500) | SÍ | — | | |
| `xml_hash` | CHAR(64) | SÍ | — | | |
| `xml_document_public_id` | BINARY(16) | SÍ | — | REF | → `document.public_id` |
| `pdf_document_public_id` | BINARY(16) | SÍ | — | REF | → `document.public_id` |
| `status` | ENUM | NO | `ISSUED` | | **Ver §14.2** |
| `cancellation_reason_code` | TINYINT UNSIGNED | SÍ | — | | Catálogo SIN de motivos de anulación |
| `voided_at` | TIMESTAMP | SÍ | — | | |
| `issued_by_user_public_id` | BINARY(16) | SÍ | — | REF | → `user_account.public_id` |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | | |
| `updated_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP ON UPDATE | | |

**Unicidad.** `uq_invoice_public_id` · `uq_invoice_cuf` ·
`uq_invoice_point_of_sale_id_tax_document_type_invoice_number`.

**Índices.** `ix_invoice_organization_public_id_issued_at` · `ix_invoice_patient_public_id_issued_at`
(**ver §14.3**) · `ix_invoice_status_issued_at` · `ix_invoice_cufd_id` · `ix_invoice_branch_public_id` ·
`ix_invoice_xml_document_public_id` · `ix_invoice_pdf_document_public_id` ·
`ix_invoice_issued_by_user_public_id`.

**FK locales.** `fk_invoice_point_of_sale` → `point_of_sale(id)` · `fk_invoice_cufd` → `cufd(id)`.

**CHECK.**

| Constraint | Regla |
| --- | --- |
| `ck_invoice_amounts` | `total_amount >= 0 AND vat_taxable_amount >= 0 AND total_discount_amount >= 0` |
| `ck_invoice_offline_requires_cafc` | `emission_type <> 'OFFLINE' OR cafc IS NOT NULL` |
| `ck_invoice_voided_requires_reason` | `status <> 'VOIDED' OR (voided_at IS NOT NULL AND cancellation_reason_code IS NOT NULL)` |

**Valores controlados.**
`tax_document_type`: `PURCHASE_SALE_INVOICE`, `HEALTH_SECTOR_INVOICE`, `PROFESSIONAL_FEES_RECEIPT` ·
`emission_type`: `ONLINE`, `OFFLINE`, `BULK` ·
`status`: `ISSUED`, `SEND_PENDING`, `DEMO_ACCEPTED`, `VALIDATED`, `REJECTED`, `VOIDED`.

---

## 8. `invoice_item` — original: `factura_detalle`

Ítem facturado con descripción y precio congelados en el momento de la emisión. El precio proviene de
ms-tenencia, nunca del cliente HTTP.

| Campo | Tipo físico | Nulo | Default | Clave | Nota |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED AUTO_INCREMENT | NO | — | PK | |
| `invoice_id` | BIGINT UNSIGNED | NO | — | FK · UQ | → `invoice.id` |
| `item_number` | SMALLINT UNSIGNED | NO | — | UQ | |
| `service_offering_public_id` | BINARY(16) | SÍ | — | REF | → `service_offering.public_id` |
| `appointment_public_id` | BINARY(16) | SÍ | — | REF | → `appointment.public_id`. Trazabilidad clínica ↔ fiscal |
| `clinical_encounter_public_id` | BINARY(16) | SÍ | — | REF | → `clinical_encounter.public_id` |
| `economic_activity_code` | VARCHAR(20) | NO | — | | |
| `tax_product_code` | VARCHAR(30) | NO | — | | |
| `internal_product_code` | VARCHAR(30) | SÍ | — | | |
| `description` | VARCHAR(500) | NO | — | | |
| `quantity` | DECIMAL(12,4) | NO | `1` | | |
| `unit_of_measure` | SMALLINT UNSIGNED | NO | `58` | | Catálogo SIN (58 = unidad) |
| `unit_price` | DECIMAL(14,2) | NO | — | | |
| `discount_amount` | DECIMAL(14,2) | NO | `0.00` | | |
| `subtotal` | DECIMAL(14,2) | NO | — | | |

**Reglas.** `pk_invoice_item` (id) · `uq_invoice_item_invoice_id_item_number` ·
`ix_invoice_item_service_offering_public_id` · `ix_invoice_item_appointment_public_id` ·
`ix_invoice_item_clinical_encounter_public_id` ·
`fk_invoice_item_invoice` → `invoice(id)` **ON DELETE CASCADE** ·
`ck_invoice_item_quantity` CHECK `quantity > 0` ·
`ck_invoice_item_amounts` CHECK `unit_price >= 0 AND discount_amount >= 0 AND subtotal >= 0`.

> Estos tres UUID son **la única** trazabilidad hacia la atención. No hay ninguna columna de narrativa
> clínica en toda la base; `containsNoClinicalNarrativeColumn` lo comprueba.

---

## 9. `payment` — original: `pago`

Pago aplicado a una factura.

| Campo | Tipo físico | Nulo | Default | Clave | Nota |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED AUTO_INCREMENT | NO | — | PK | |
| `public_id` | BINARY(16) | NO | — | UQ | |
| `organization_public_id` | BINARY(16) | NO | — | REF | → `organization.public_id` |
| `invoice_id` | BIGINT UNSIGNED | SÍ | — | FK | → `invoice.id`. `NULL` en anticipos o pagos a cuenta |
| `patient_public_id` | BINARY(16) | NO | — | REF | → `patient.public_id` |
| `amount` | DECIMAL(14,2) | NO | — | | |
| `currency` | CHAR(3) | NO | `BOB` | | |
| `method` | ENUM | NO | `CASH` | | |
| `gateway` | VARCHAR(80) | SÍ | — | UQ | Ej. QR Simple BCP, Tigo Money, Libélula |
| `external_transaction_id` | VARCHAR(120) | SÍ | — | UQ | |
| `status` | ENUM | NO | `PENDING` | | |
| `received_at` | TIMESTAMP | SÍ | — | | |
| `recorded_by_user_public_id` | BINARY(16) | SÍ | — | REF | → `user_account.public_id` |
| `observation` | VARCHAR(300) | SÍ | — | | |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | | |

**Reglas.** `pk_payment` (id) · `uq_payment_public_id` ·
`uq_payment_gateway_external_transaction_id` (gateway, external_transaction_id) ·
`ix_payment_invoice_id` · `ix_payment_patient_public_id_created_at` ·
`ix_payment_organization_public_id_status_created_at` · `ix_payment_recorded_by_user_public_id` ·
`fk_payment_invoice` → `invoice(id)` · `ck_payment_amount` CHECK `amount > 0`.

**Valores controlados.**
`method`: `CASH`, `CREDIT_CARD`, `DEBIT_CARD`, `QR_SIMPLE`, `TRANSFER`, `MOBILE_WALLET`, `INSURANCE`, `INTERNAL_CREDIT` ·
`status`: `PENDING`, `CONFIRMED`, `REJECTED`, `REVERSED`.

---

## 10. `credit_debit_note` — original: `nota_credito_debito`

Documento que modifica una factura emitida.

| Campo | Tipo físico | Nulo | Default | Clave | Nota |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED AUTO_INCREMENT | NO | — | PK | |
| `organization_public_id` | BINARY(16) | NO | — | REF | → `organization.public_id` |
| `original_invoice_id` | BIGINT UNSIGNED | NO | — | FK | → `invoice.id` |
| `point_of_sale_id` | BIGINT UNSIGNED | NO | — | FK · UQ | → `point_of_sale.id` |
| `cufd_id` | BIGINT UNSIGNED | NO | — | FK | → `cufd.id` |
| `type` | ENUM | NO | `CREDIT` | UQ | |
| `note_number` | BIGINT UNSIGNED | NO | — | UQ | |
| `cuf` | VARCHAR(80) | NO | — | UQ | |
| `issued_at` | TIMESTAMP | NO | — | | |
| `credit_effect_amount` | DECIMAL(14,2) | NO | — | | |
| `reason` | VARCHAR(300) | NO | — | | |
| `status` | ENUM | NO | `ISSUED` | | |
| `tax_receipt_code` | VARCHAR(50) | SÍ | — | | |
| `xml_document_public_id` | BINARY(16) | SÍ | — | REF | → `document.public_id` |
| `pdf_document_public_id` | BINARY(16) | SÍ | — | REF | → `document.public_id` |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | | |

**Reglas.** `pk_credit_debit_note` (id) · `uq_credit_debit_note_cuf` ·
`uq_credit_debit_note_point_of_sale_id_type_note_number` ·
`ix_credit_debit_note_original_invoice_id` · `ix_credit_debit_note_organization_public_id_issued_at` ·
`ix_credit_debit_note_cufd_id` · `ix_credit_debit_note_xml_document_public_id` ·
`ix_credit_debit_note_pdf_document_public_id` ·
`fk_credit_debit_note_original_invoice` · `fk_credit_debit_note_point_of_sale` · `fk_credit_debit_note_cufd` ·
`ck_credit_debit_note_amount` CHECK `credit_effect_amount > 0`.

**Valores controlados.**
`type`: `CREDIT`, `DEBIT` ·
`status`: `ISSUED`, `SEND_PENDING`, `VALIDATED`, `REJECTED`, `VOIDED`.

---

## 11. `significant_event` — original: `evento_significativo`

Contingencia reportada al SIN.

| Campo | Tipo físico | Nulo | Default | Clave | Nota |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED AUTO_INCREMENT | NO | — | PK | |
| `point_of_sale_id` | BIGINT UNSIGNED | NO | — | FK | → `point_of_sale.id` |
| `cufd_id` | BIGINT UNSIGNED | NO | — | FK | → `cufd.id` |
| `event_cufd_id` | BIGINT UNSIGNED | SÍ | — | FK | → `cufd.id`. CUFD vigente al momento de reportar |
| `event_code` | TINYINT UNSIGNED | NO | — | | 1=corte internet, 2=inaccesibilidad SIN, 3=corte energía… |
| `description` | VARCHAR(300) | NO | — | | |
| `starts_at` | TIMESTAMP | NO | — | | |
| `ends_at` | TIMESTAMP | SÍ | — | | |
| `offline_invoice_count` | INT UNSIGNED | NO | `0` | | |
| `event_receipt_code` | VARCHAR(50) | SÍ | — | | |
| `status` | ENUM | NO | `OPEN` | | |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | | |

**Reglas.** `pk_significant_event` (id) · `ix_significant_event_point_of_sale_id_starts_at` ·
`ix_significant_event_status` · `ix_significant_event_cufd_id` · `ix_significant_event_event_cufd_id` ·
`fk_significant_event_point_of_sale` · `fk_significant_event_cufd` · `fk_significant_event_event_cufd` ·
`ck_significant_event_period` CHECK `ends_at IS NULL OR ends_at >= starts_at`.

**Valores controlados.** `status`: `OPEN`, `REPORTED`, `CLOSED`.

---

## 12. `tax_submission` — original: `envio_sin`

Intento de envío simulado o real hacia el sistema fiscal.

| Campo | Tipo físico | Nulo | Default | Clave | Nota |
| --- | --- | --- | --- | --- | --- |
| `id` | BIGINT UNSIGNED AUTO_INCREMENT | NO | — | PK | |
| `organization_public_id` | BINARY(16) | NO | — | REF | → `organization.public_id` |
| `entity_type` | ENUM | NO | — | | |
| `entity_public_id` | BIGINT UNSIGNED | SÍ | — | | Tipado así por el diccionario pese al nombre. **Ver §14.4** |
| `operation` | VARCHAR(80) | NO | — | | Método del servicio SIAT invocado |
| `payload_hash` | CHAR(64) | SÍ | — | | |
| `status` | ENUM | NO | `PENDING` | | |
| `attempt_count` | SMALLINT UNSIGNED | NO | `0` | | |
| `next_attempt_at` | TIMESTAMP | SÍ | — | | Backoff exponencial ante fallas de red |
| `last_error` | VARCHAR(500) | SÍ | — | | |
| `response_code` | VARCHAR(20) | SÍ | — | | |
| `response` | JSON | SÍ | — | | |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | | |
| `updated_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP ON UPDATE | | |

**Reglas.** `pk_tax_submission` (id) · `ix_tax_submission_status_next_attempt_at` ·
`ix_tax_submission_entity_type_entity_public_id` ·
`ix_tax_submission_organization_public_id_created_at`. Sin FK locales.

**Valores controlados.**
`entity_type`: `INVOICE`, `CREDIT_DEBIT_NOTE`, `SIGNIFICANT_EVENT`, `CANCELLATION`, `OFFLINE_PACKAGE`, `CUFD_REQUEST`, `CUIS_REQUEST`, `CATALOG_SYNC` ·
`status`: `PENDING`, `SENDING`, `SENT`, `ERROR`, `DISMISSED`.

---

## 13. Matriz de referencias externas

Todos los identificadores que cruzan la frontera del servicio. **Ninguno tiene FK física**; cada uno
lleva índice propio para búsquedas y joins en memoria. Coincide con las páginas 71–72 del diccionario.

| Tabla | Campo | Destino lógico | Servicio propietario |
| --- | --- | --- | --- |
| `billing_configuration` | `organization_public_id` | `organization.public_id` | ms-tenencia |
| `billing_configuration` | `encryption_key_public_id` | `encryption_key.public_id` | ms-criptografía |
| `billing_configuration` | `digital_certificate_public_id` | `digital_certificate.public_id` | ms-criptografía |
| `point_of_sale` | `organization_public_id` | `organization.public_id` | ms-tenencia |
| `point_of_sale` | `branch_public_id` | `branch.public_id` | ms-tenencia |
| `invoice` | `organization_public_id` | `organization.public_id` | ms-tenencia |
| `invoice` | `branch_public_id` | `branch.public_id` | ms-tenencia |
| `invoice` | `patient_public_id` | `patient.public_id` | ms-pacientes |
| `invoice` | `xml_document_public_id` | `document.public_id` | ms-documental |
| `invoice` | `pdf_document_public_id` | `document.public_id` | ms-documental |
| `invoice` | `issued_by_user_public_id` | `user_account.public_id` | ms-identidad |
| `invoice_item` | `service_offering_public_id` | `service_offering.public_id` | ms-tenencia |
| `invoice_item` | `appointment_public_id` | `appointment.public_id` | ms-agenda |
| `invoice_item` | `clinical_encounter_public_id` | `clinical_encounter.public_id` | ms-historial |
| `payment` | `organization_public_id` | `organization.public_id` | ms-tenencia |
| `payment` | `patient_public_id` | `patient.public_id` | ms-pacientes |
| `payment` | `recorded_by_user_public_id` | `user_account.public_id` | ms-identidad |
| `credit_debit_note` | `organization_public_id` | `organization.public_id` | ms-tenencia |
| `credit_debit_note` | `xml_document_public_id` | `document.public_id` | ms-documental |
| `credit_debit_note` | `pdf_document_public_id` | `document.public_id` | ms-documental |
| `tax_submission` | `organization_public_id` | `organization.public_id` | ms-tenencia |

Validación del MVP según §1.1 del diccionario: `patient_public_id` se resuelve con un **DTO fiscal
mínimo** de ms-pacientes —no se copia historia clínica—; `service_offering_public_id` se resuelve con
un *snapshot* de código, descripción y precio de ms-tenencia.

---

## 14. Desviaciones respecto al PDF

Tres, y las tres nacen de una inconsistencia interna del propio diccionario. No hay ninguna
divergencia de tipo, nulabilidad, default ni valor controlado en ninguna de las 12 tablas.

### 14.1 `invoice.patient_public_id` no aparece en la lista de columnas

El PDF no lista esta columna en la tabla `invoice` (páginas 64–65), pero **sí** declara el índice
`ix_invoice_patient_id_issued_at` sobre `patient_id, issued_at`, y §1.1 asigna
`billing_db → patient_public_id → ms-pacientes`. La columna es necesaria para que el índice del propio
diccionario exista. Se implementa como `BINARY(16) NOT NULL` sin FK, con el nombre canónico de la
plataforma.

### 14.2 `invoice.status` tampoco aparece en la lista de columnas

Mismo caso: el PDF declara el índice `ix_invoice_status_issued_at` y el CHECK
`estado <> 'VOIDED' OR (voided_at IS NOT NULL AND cancellation_reason_code IS NOT NULL)`, ambos
imposibles sin la columna. Se implementa como `ENUM` con los cinco valores que el diccionario define
para `credit_debit_note.status`, más `DEMO_ACCEPTED`.

`DEMO_ACCEPTED` **es un añadido nuestro, no está en el PDF**: es el estado con el que sale toda factura
emitida por el adaptador fiscal simulado del MVP. Ver el aviso permanente en `contracts.md`.

### 14.3 Nombre del índice de paciente

| PDF | Implementado |
| --- | --- |
| `ix_invoice_patient_id_issued_at` sobre `patient_id, issued_at` | `ix_invoice_patient_public_id_issued_at` sobre `patient_public_id, issued_at` |

Se prefirió que el nombre del índice concuerde con el nombre real de la columna. La convención del
propio diccionario («los datos de otro servicio se representan como UUID externo») exige
`patient_public_id`, no `patient_id`.

### 14.4 `tax_submission.entity_public_id` es `BIGINT UNSIGNED`

No es una desviación: es lo que dice el PDF (página 69). Se anota porque el nombre sugiere un UUID y
el tipo dice lo contrario. Se implementó **literal al diccionario**.

---

## 15. Cómo se verifica esto automáticamente

`SchemaConformanceContainerTest` contrasta el esquema creado por Flyway contra este documento sobre un
MySQL 8 real (Testcontainers; se salta si no hay Docker):

| Prueba | Qué afirma |
| --- | --- |
| `createsExactlyTheDictionaryTables` | `billing_db` tiene exactamente las 12 tablas del capítulo 11 |
| `columnTypesMatchDictionary` | Los tipos físicos de las columnas representativas de cada tabla |
| `uniqueConstraintsExist` | Las restricciones de unicidad, por nombre |
| `documentSequenceIsKeyedByPointOfSaleAndType` | La clave compuesta del correlativo |
| `declaresOnlyLocalForeignKeys` | Ninguna FK apunta a otra base |
| `containsNoClinicalTable` | No existe ninguna de las 9 tablas clínicas |
| `containsNoClinicalNarrativeColumn` | Ninguna columna guarda narrativa clínica |

```bash
./mvnw -pl services/ms-facturacion test -Dtest=SchemaConformanceContainerTest
```

## 16. Alcance de código sobre el esquema

Las 12 tablas existen en la base. El código Java cubre las 7 del guion de la demostración:

| Tabla | DDL | Entidad JPA + repositorio |
| --- | --- | --- |
| `billing_configuration`, `point_of_sale`, `cuis`, `cufd`, `document_sequence`, `invoice`, `invoice_item` | ✅ `V1` | ✅ |
| `economic_activity`, `payment`, `credit_debit_note`, `significant_event`, `tax_submission` | ✅ `V2` | ❌ — sin flujo que las use todavía |

Los flujos que faltan (pagos, anulación, notas de crédito/débito, contingencia offline, envío al SIAT)
siguen en la deuda consciente del `README.md`, §10. El esquema, en cambio, ya está completo y conforme.
