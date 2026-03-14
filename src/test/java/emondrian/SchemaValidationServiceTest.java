package emondrian;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

public class SchemaValidationServiceTest {

    @Test
    public void warnsOnUnknownAggLevelRef() {
        SchemaValidationService.ValidationResult result =
            new SchemaValidationService().validateSchemaXml(
                schemaWithUnknownAggLevel(),
                "inline-schema.xml",
                false);

        assertHasCode(result.getMessages(), "AGG_LEVEL_UNKNOWN_LEVEL_REF");
    }

    @Test
    public void warnsOnDuplicateAggColumnAcrossHierarchyRefs() {
        SchemaValidationService.ValidationResult result =
            new SchemaValidationService().validateSchemaXml(
                schemaWithFlatAndHierarchicalAggRefs(),
                "inline-schema.xml",
                false);

        assertHasCode(result.getMessages(), "AGG_DUPLICATE_COLUMN_LEVEL_REFS");
        assertHasCode(result.getMessages(), "AGG_FLAT_HIERARCHY_COLUMN_REUSE");
    }

    private static void assertHasCode(
        List<SchemaValidationService.ValidationMessage> messages,
        String expectedCode)
    {
        for (SchemaValidationService.ValidationMessage message : messages) {
            if (expectedCode.equals(message.code)) {
                return;
            }
        }
        assertTrue("Expected validation message code: " + expectedCode, false);
    }

    private static String schemaWithUnknownAggLevel() {
        return "<Schema name=\"Test\">"
            + "<Cube name=\"Sales\">"
            + "<Table name=\"fact_sales\"/>"
            + "<Dimension name=\"Product\">"
            + "<Hierarchy name=\"Product\">"
            + "<Level name=\"Category\" column=\"category\"/>"
            + "</Hierarchy>"
            + "</Dimension>"
            + "<AggName name=\"agg_sales\">"
            + "<AggLevel name=\"[Product].[Missing]\" column=\"category\"/>"
            + "</AggName>"
            + "</Cube>"
            + "</Schema>";
    }

    private static String schemaWithFlatAndHierarchicalAggRefs() {
        return "<Schema name=\"Test\">"
            + "<Cube name=\"Sales\">"
            + "<Table name=\"fact_sales\"/>"
            + "<Dimension name=\"Product flat\">"
            + "<Hierarchy name=\"Product flat\">"
            + "<Level name=\"Manufacturer\" column=\"manufacturer_group\"/>"
            + "</Hierarchy>"
            + "</Dimension>"
            + "<Dimension name=\"Product hier\">"
            + "<Hierarchy name=\"Product hier\">"
            + "<Level name=\"Manufacturer\" column=\"manufacturer_group\"/>"
            + "</Hierarchy>"
            + "</Dimension>"
            + "<AggName name=\"agg_sales\">"
            + "<AggLevel name=\"[Product flat].[Manufacturer]\" column=\"manufacturer_group\"/>"
            + "<AggLevel name=\"[Product hier].[Manufacturer]\" column=\"manufacturer_group\"/>"
            + "</AggName>"
            + "</Cube>"
            + "</Schema>";
    }
}
