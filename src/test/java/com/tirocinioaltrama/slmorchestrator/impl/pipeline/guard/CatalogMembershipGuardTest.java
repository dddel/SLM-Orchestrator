package com.tirocinioaltrama.slmorchestrator.impl.pipeline.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tirocinioaltrama.slmorchestrator.core.validation.FieldError;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationErrorCode;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class CatalogMembershipGuardTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private final String sampleRolesJson = """
            {
              "direzione": {
                "ruolo": "Direttore",
                "persona": "Stefano Vena",
                "email": "stefano.vena@azienda.it"
              },
              "risorse_umane": {
                "ruolo": "H.R.",
                "persona": "Claudia Frammartino",
                "email": "claudia.frammartino@azienda.it",
                "ambiti": ["recruiting", "colloqui"]
              },
              "testers": {
                "ruolo": "Testers",
                "persona": "Luca Bianchi",
                "collaboratori": ["Sara Verdi"],
                "email": "testers@azienda.it"
              }
            }
            """;

    @Test
    @DisplayName("extractAllowedValues estrae chiavi, ruoli, persone, collaboratori ed email dal catalogo JSON")
    void testExtractAllowedValues() {
        Set<String> values = CatalogMembershipGuard.extractAllowedValues(sampleRolesJson);

        assertTrue(values.contains("direzione"));
        assertTrue(values.contains("Direttore"));
        assertTrue(values.contains("Stefano Vena"));
        assertTrue(values.contains("stefano.vena@azienda.it"));
        assertTrue(values.contains("risorse_umane"));
        assertTrue(values.contains("H.R."));
        assertTrue(values.contains("Claudia Frammartino"));
        assertTrue(values.contains("Luca Bianchi"));
        assertTrue(values.contains("Sara Verdi"));
        assertTrue(values.contains("recruiting"));
    }

    @Test
    @DisplayName("isMember valida membri con tolleranza a case, punteggiatura e spazi")
    void testIsMemberNormalized() {
        Set<String> catalog = CatalogMembershipGuard.extractAllowedValues(sampleRolesJson);

        assertTrue(CatalogMembershipGuard.isMember("Stefano Vena", catalog));
        assertTrue(CatalogMembershipGuard.isMember("stefano vena", catalog));
        assertTrue(CatalogMembershipGuard.isMember("H.R.", catalog));
        assertTrue(CatalogMembershipGuard.isMember("HR", catalog)); // normalizzazione punteggiatura
        assertTrue(CatalogMembershipGuard.isMember("claudia.frammartino@azienda.it", catalog));
        assertTrue(CatalogMembershipGuard.isMember("Sara Verdi", catalog));

        // Candidati non presenti nel catalogo
        assertFalse(CatalogMembershipGuard.isMember("Babbo Natale", catalog));
        assertFalse(CatalogMembershipGuard.isMember("Marketing Manager", catalog));
        assertFalse(CatalogMembershipGuard.isMember("sconosciuto@esterno.com", catalog));
    }

    @Test
    @DisplayName("validateField produce VALUE_NOT_IN_CATALOG per valori non ammessi")
    void testValidateField() {
        Set<String> catalog = CatalogMembershipGuard.extractAllowedValues(sampleRolesJson);

        ValidationResult ok = CatalogMembershipGuard.validateField("destinatario", "Claudia Frammartino", catalog);
        assertTrue(ok.isValid());
        assertTrue(ok.fieldErrors().isEmpty());

        ValidationResult bad = CatalogMembershipGuard.validateField("destinatario", "Mario Rossi Esterno", catalog);
        assertFalse(bad.isValid());
        assertEquals(1, bad.fieldErrors().size());
        FieldError fe = bad.fieldErrors().get(0);
        assertEquals("destinatario", fe.path());
        assertEquals(ValidationErrorCode.VALUE_NOT_IN_CATALOG, fe.code());
    }

    @Test
    @DisplayName("validateJson controlla la conformità dei campi destinatario e ruoloDestinatario")
    void testValidateJson() throws Exception {
        String validJson = """
                {
                  "destinatario": "Stefano Vena",
                  "ruoloDestinatario": "Direttore",
                  "email": "stefano.vena@azienda.it"
                }
                """;
        JsonNode validNode = mapper.readTree(validJson);
        ValidationResult validRes = CatalogMembershipGuard.validateJson(validNode, sampleRolesJson, List.of("destinatario", "ruoloDestinatario"));
        assertTrue(validRes.isValid());

        String invalidJson = """
                {
                  "destinatario": "Impostore Non Censito",
                  "ruoloDestinatario": "H.R."
                }
                """;
        JsonNode invalidNode = mapper.readTree(invalidJson);
        ValidationResult invalidRes = CatalogMembershipGuard.validateJson(invalidNode, sampleRolesJson, List.of("destinatario", "ruoloDestinatario"));
        assertFalse(invalidRes.isValid());
        assertEquals(1, invalidRes.fieldErrors().size());
        assertEquals("destinatario", invalidRes.fieldErrors().get(0).path());
        assertEquals(ValidationErrorCode.VALUE_NOT_IN_CATALOG, invalidRes.fieldErrors().get(0).code());
    }

    @Test
    @DisplayName("CatalogMembershipGuard come istanza Validator<String>")
    void testValidatorInstance() {
        CatalogMembershipGuard guard = new CatalogMembershipGuard(sampleRolesJson, "company_roles");
        assertEquals("catalog_membership", guard.getName());
        assertTrue(guard.validate("Claudia Frammartino").isValid());
        assertFalse(guard.validate("Soggetto Inesistente").isValid());
    }

    @Test
    @DisplayName("Limite noto: la guardia valida l'appartenenza al catalogo ma non la correttezza semantica della scelta")
    void testGuardPassesSyntacticallyValidChoiceEvenIfSemanticallyWrong() throws Exception {
        // Esempio: richiesta ferie del personale (pertinente a H.R. / Claudia Frammartino)
        // erroneamente indirizzata alla Direzione Generale (Stefano Vena / Direttore).
        // Poiché Stefano Vena e Direttore appartengono al catalogo, la guardia lascia passare la scelta.
        String semanticallyWrongJson = """
                {
                  "destinatario": "Stefano Vena",
                  "ruoloDestinatario": "Direttore"
                }
                """;
        JsonNode node = mapper.readTree(semanticallyWrongJson);
        ValidationResult res = CatalogMembershipGuard.validateJson(node, sampleRolesJson, List.of("destinatario", "ruoloDestinatario"));
        assertTrue(res.isValid(), "Limite noto: la guardia non rileva incongruenze semantiche se i valori appartengono al catalogo");
    }
}
