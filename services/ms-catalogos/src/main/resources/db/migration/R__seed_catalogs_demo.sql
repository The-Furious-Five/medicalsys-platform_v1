-- =============================================================
-- ms-catalogos · catalogs_db
-- R__seed_catalogs_demo.sql  (repetible, idempotente)
--
-- Catalogo de referencia del entorno de demostracion. Los valores imitan los de
-- una clinica privada de La Paz: especialidades reales de un policlinico, codigos
-- CIE-10 vigentes, medicamentos del listado basico y tipos de estudio con sus
-- rangos de referencia. Nada de esto es dato clinico de una persona; son tablas
-- maestras, y por eso pueden ser realistas sin comprometer a nadie.
--
-- Regla: INSERT IGNORE, para no duplicar al reiniciar el contenedor.
-- =============================================================

-- -------------------------------------------------------
-- Especialidades del centro
-- -------------------------------------------------------
INSERT IGNORE INTO specialty (code, name, description, active) VALUES
    ('MEDICINA_GENERAL', 'Medicina General',
     'Atencion ambulatoria de primer contacto y control de salud del adulto.', TRUE),
    ('MEDICINA_INTERNA', 'Medicina Interna',
     'Diagnostico y tratamiento de enfermedades cronicas del adulto.', TRUE),
    ('PEDIATRIA', 'Pediatria',
     'Control de crecimiento y atencion de nino y adolescente hasta los 18 anos.', TRUE),
    ('GINECOLOGIA', 'Ginecologia y Obstetricia',
     'Salud de la mujer, control prenatal y planificacion familiar.', TRUE),
    ('CARDIOLOGIA', 'Cardiologia',
     'Evaluacion y seguimiento de patologia cardiovascular.', TRUE),
    ('TRAUMATOLOGIA', 'Traumatologia y Ortopedia',
     'Lesiones del aparato locomotor, fracturas y rehabilitacion.', TRUE),
    ('DERMATOLOGIA', 'Dermatologia',
     'Patologia de piel, faneras y mucosas.', TRUE),
    ('ODONTOLOGIA', 'Odontologia General',
     'Prevencion, restauracion y cirugia oral menor.', TRUE),
    ('NUTRICION', 'Nutricion y Dietetica',
     'Valoracion nutricional y planes alimentarios.', TRUE),
    ('RADIOLOGIA', 'Radiologia e Imagenologia',
     'Estudios de imagen simple y ecografia.', TRUE),
    ('LABORATORIO', 'Laboratorio Clinico',
     'Analisis hematologicos, bioquimicos y microbiologicos.', TRUE);

-- -------------------------------------------------------
-- CIE-10: los diagnosticos mas frecuentes en consulta ambulatoria
-- -------------------------------------------------------
INSERT IGNORE INTO icd10_catalog
    (code, description, chapter, category, applicable_sex, active)
VALUES
    ('J00',   'Rinofaringitis aguda (resfriado comun)',
              'X - Enfermedades del sistema respiratorio',
              'J00-J06 Infecciones agudas del tracto respiratorio superior',
              'BOTH', TRUE),
    ('J02.9', 'Faringitis aguda, no especificada',
              'X - Enfermedades del sistema respiratorio',
              'J00-J06 Infecciones agudas del tracto respiratorio superior',
              'BOTH', TRUE),
    ('J20.9', 'Bronquitis aguda, no especificada',
              'X - Enfermedades del sistema respiratorio',
              'J20-J22 Otras infecciones agudas de las vias respiratorias inferiores',
              'BOTH', TRUE),
    ('Z00.0', 'Examen medico general de adulto',
              'XXI - Factores que influyen en el estado de salud',
              'Z00-Z13 Consultas para examen y screening',
              'BOTH', TRUE),
    ('Z34.0', 'Supervision de primer embarazo normal',
              'XXI - Factores que influyen en el estado de salud',
              'Z30-Z39 Atencion de la procreacion',
              'F', TRUE),
    ('M54.5', 'Lumbago no especificado',
              'XIII - Enfermedades del sistema osteomuscular',
              'M50-M54 Dorsopatias',
              'BOTH', TRUE),
    ('M17.9', 'Gonartrosis, no especificada',
              'XIII - Enfermedades del sistema osteomuscular',
              'M15-M19 Artrosis',
              'BOTH', TRUE),
    ('R51',   'Cefalea',
              'XVIII - Sintomas y signos',
              'R50-R69 Sintomas generales',
              'BOTH', TRUE),
    ('R10.4', 'Otros dolores abdominales y los no especificados',
              'XVIII - Sintomas y signos',
              'R10-R19 Sintomas del aparato digestivo y el abdomen',
              'BOTH', TRUE),
    ('I10',   'Hipertension esencial (primaria)',
              'IX - Enfermedades del sistema circulatorio',
              'I10-I15 Enfermedades hipertensivas',
              'BOTH', TRUE),
    ('E11.9', 'Diabetes mellitus tipo 2 sin complicaciones',
              'IV - Enfermedades endocrinas, nutricionales y metabolicas',
              'E10-E14 Diabetes mellitus',
              'BOTH', TRUE),
    ('E66.9', 'Obesidad, no especificada',
              'IV - Enfermedades endocrinas, nutricionales y metabolicas',
              'E65-E68 Obesidad y otros tipos de hiperalimentacion',
              'BOTH', TRUE),
    ('D50.9', 'Anemia por deficiencia de hierro, sin especificar',
              'III - Enfermedades de la sangre y organos hematopoyeticos',
              'D50-D53 Anemias nutricionales',
              'BOTH', TRUE),
    ('A09',   'Diarrea y gastroenteritis de presunto origen infeccioso',
              'I - Ciertas enfermedades infecciosas y parasitarias',
              'A00-A09 Enfermedades infecciosas intestinales',
              'BOTH', TRUE),
    ('N39.0', 'Infeccion de vias urinarias, sitio no especificado',
              'XIV - Enfermedades del sistema genitourinario',
              'N30-N39 Otras enfermedades del sistema urinario',
              'BOTH', TRUE),
    ('L20.9', 'Dermatitis atopica, no especificada',
              'XII - Enfermedades de la piel y del tejido subcutaneo',
              'L20-L30 Dermatitis y eccema',
              'BOTH', TRUE),
    ('S52.50', 'Fractura del extremo inferior del radio, no especificada',
              'XIX - Traumatismos y envenenamientos',
              'S50-S59 Traumatismos del codo y del antebrazo',
              'BOTH', TRUE),
    ('S93.4', 'Esguince y torcedura del tobillo',
              'XIX - Traumatismos y envenenamientos',
              'S90-S99 Traumatismos del tobillo y del pie',
              'BOTH', TRUE);

-- -------------------------------------------------------
-- study_type: los estudios que la clinica resuelve en el dia
-- -------------------------------------------------------
INSERT IGNORE INTO study_type
    (code, name, category, requires_fasting, generates_image, result_unit,
     reference_min, reference_max, active)
VALUES
    ('X_RAY',           'Radiografia simple (rayos X)',      'IMAGING',    FALSE, TRUE,  NULL,     NULL,   NULL, TRUE),
    ('US_ABDOMEN',      'Ecografia abdominal completa',      'IMAGING',    TRUE,  TRUE,  NULL,     NULL,   NULL, TRUE),
    ('US_OBSTETRIC',    'Ecografia obstetrica',              'IMAGING',    FALSE, TRUE,  NULL,     NULL,   NULL, TRUE),
    ('LAB_HEMOGRAM',    'Hemograma completo',                'LABORATORY', FALSE, FALSE, 'g/dL',   12.0000, 17.5000, TRUE),
    ('LAB_GLUCOSE',     'Glucemia en ayunas',                'LABORATORY', TRUE,  FALSE, 'mg/dL',  70.0000, 100.0000, TRUE),
    ('LAB_LIPIDS',      'Perfil lipidico',                   'LABORATORY', TRUE,  FALSE, 'mg/dL',  NULL,   200.0000, TRUE),
    ('LAB_CREATININE',  'Creatinina serica',                 'LABORATORY', FALSE, FALSE, 'mg/dL',  0.6000,  1.2000, TRUE),
    ('LAB_TSH',         'Hormona estimulante de tiroides',   'LABORATORY', FALSE, FALSE, 'uUI/mL', 0.4000,  4.0000, TRUE),
    ('LAB_URINE',       'Examen general de orina',           'LABORATORY', FALSE, FALSE, NULL,     NULL,   NULL, TRUE),
    ('ECG',             'Electrocardiograma de 12 derivaciones', 'CARDIOLOGY', FALSE, TRUE, NULL,  NULL,   NULL, TRUE);

-- -------------------------------------------------------
-- medication: subconjunto del listado basico de medicamentos
-- -------------------------------------------------------
INSERT IGNORE INTO medication
    (code, generic_name, trade_name, concentration, pharmaceutical_form,
     administration_route, is_controlled, requires_prescription, active)
VALUES
    ('PARACETAMOL_500',  'Paracetamol',            'Panadol',    '500 mg',        'TABLET',     'ORAL',          FALSE, TRUE,  TRUE),
    ('IBUPROFENO_400',   'Ibuprofeno',             'Ibupirac',   '400 mg',        'TABLET',     'ORAL',          FALSE, TRUE,  TRUE),
    ('AMOXICILINA_500',  'Amoxicilina',            'Amoxil',     '500 mg',        'CAPSULE',    'ORAL',          FALSE, TRUE,  TRUE),
    ('AZITROMICINA_500', 'Azitromicina',           'Zitromax',   '500 mg',        'TABLET',     'ORAL',          FALSE, TRUE,  TRUE),
    ('OMEPRAZOL_20',     'Omeprazol',              'Losec',      '20 mg',         'CAPSULE',    'ORAL',          FALSE, TRUE,  TRUE),
    ('LOSARTAN_50',      'Losartan potasico',      'Cozaar',     '50 mg',         'TABLET',     'ORAL',          FALSE, TRUE,  TRUE),
    ('ENALAPRIL_10',     'Enalapril',              'Renitec',    '10 mg',         'TABLET',     'ORAL',          FALSE, TRUE,  TRUE),
    ('METFORMINA_850',   'Metformina',             'Glucophage', '850 mg',        'TABLET',     'ORAL',          FALSE, TRUE,  TRUE),
    ('SALBUTAMOL_100',   'Salbutamol',             'Ventolin',   '100 mcg/dosis', 'AEROSOL',    'INHALATION',    FALSE, TRUE,  TRUE),
    ('LORATADINA_10',    'Loratadina',             'Clarityne',  '10 mg',         'TABLET',     'ORAL',          FALSE, FALSE, TRUE),
    ('SULFATO_FE_200',   'Sulfato ferroso',        NULL,         '200 mg',        'TABLET',     'ORAL',          FALSE, FALSE, TRUE),
    ('DICLOFENACO_75',   'Diclofenaco sodico',     'Voltaren',   '75 mg/3 mL',    'INJECTABLE', 'INTRAMUSCULAR', FALSE, TRUE,  TRUE),
    ('HIDROCORTISONA_1', 'Hidrocortisona',         NULL,         '1 %',           'CREAM',      'TOPICAL',       FALSE, TRUE,  TRUE),
    ('AMOX_SUSP_250',    'Amoxicilina pediatrica', 'Amoxil',     '250 mg/5 mL',   'SUSPENSION', 'ORAL',          FALSE, TRUE,  TRUE);

-- -------------------------------------------------------
-- tax_catalog: replica local de los catalogos del SIN.
-- La descripcion conserva "SIMULADO": este entorno no se sincroniza con el
-- servicio real de impuestos y ningun documento emitido aqui tiene validez.
-- -------------------------------------------------------
INSERT IGNORE INTO tax_catalog
    (catalog_type, code, description, active)
VALUES
    ('PRODUCT_SERVICE', 'CONSULTA',      'SERVICIO: Consulta medica general - SIMULADO',            TRUE),
    ('PRODUCT_SERVICE', 'CONSULTA_ESP',  'SERVICIO: Consulta medica de especialidad - SIMULADO',    TRUE),
    ('PRODUCT_SERVICE', 'RAYOS_X',       'SERVICIO: Estudio de rayos X - SIMULADO',                 TRUE),
    ('PRODUCT_SERVICE', 'ECOGRAFIA',     'SERVICIO: Estudio ecografico - SIMULADO',                 TRUE),
    ('PRODUCT_SERVICE', 'LABORATORIO',   'SERVICIO: Analisis de laboratorio clinico - SIMULADO',    TRUE),
    ('PRODUCT_SERVICE', 'ODONTOLOGIA',   'SERVICIO: Atencion odontologica - SIMULADO',              TRUE),
    ('PAYMENT_METHOD_TYPE', 'EFECTIVO',  'Pago en efectivo - SIMULADO',                             TRUE),
    ('PAYMENT_METHOD_TYPE', 'TARJETA',   'Tarjeta de debito o credito - SIMULADO',                  TRUE),
    ('PAYMENT_METHOD_TYPE', 'QR',        'Pago con codigo QR bancario - SIMULADO',                  TRUE),
    ('PAYMENT_METHOD_TYPE', 'TRANSFER',  'Transferencia bancaria - SIMULADO',                       TRUE),
    ('IDENTITY_DOCUMENT_TYPE', 'CI',     'Cedula de identidad - SIMULADO',                          TRUE),
    ('IDENTITY_DOCUMENT_TYPE', 'NIT',    'Numero de identificacion tributaria - SIMULADO',          TRUE),
    ('IDENTITY_DOCUMENT_TYPE', 'PAS',    'Pasaporte - SIMULADO',                                    TRUE),
    ('UNIT_OF_MEASURE', '58',            'Unidad (servicio) - SIMULADO',                            TRUE);
