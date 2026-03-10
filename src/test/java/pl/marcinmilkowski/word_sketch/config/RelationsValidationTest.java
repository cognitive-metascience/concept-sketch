package pl.marcinmilkowski.word_sketch.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that all relations in the default grammar config have required fields.
 */
public class RelationsValidationTest {

    @Test
    public void testAllRelationsHaveNonNullPattern() {
        GrammarConfigLoader loader = GrammarConfigLoader.createDefaultEnglish();
        List<GrammarConfigLoader.RelationConfig> relations = loader.getRelations();

        assertFalse(relations.isEmpty(), "Grammar config should have at least one relation");

        for (GrammarConfigLoader.RelationConfig rel : relations) {
            assertNotNull(rel.pattern(),
                "Relation '" + rel.id() + "' has a null pattern");
            assertFalse(rel.pattern().isBlank(),
                "Relation '" + rel.id() + "' has a blank pattern");
        }
    }

    @Test
    public void testAllRelationsHaveValidPositions() {
        GrammarConfigLoader loader = GrammarConfigLoader.createDefaultEnglish();
        List<GrammarConfigLoader.RelationConfig> relations = loader.getRelations();

        for (GrammarConfigLoader.RelationConfig rel : relations) {
            assertTrue(rel.headPosition() >= 1,
                "Relation '" + rel.id() + "' has invalid headPosition: " + rel.headPosition());
            assertTrue(rel.collocatePosition() >= 1,
                "Relation '" + rel.id() + "' has invalid collocatePosition: " + rel.collocatePosition());
            assertNotEquals(rel.headPosition(), rel.collocatePosition(),
                "Relation '" + rel.id() + "' has same head and collocate position: " + rel.headPosition());
        }
    }

    @Test
    public void testAllRelationsHaveNonNullId() {
        GrammarConfigLoader loader = GrammarConfigLoader.createDefaultEnglish();
        List<GrammarConfigLoader.RelationConfig> relations = loader.getRelations();

        for (GrammarConfigLoader.RelationConfig rel : relations) {
            assertNotNull(rel.id(), "Relation has a null id");
            assertFalse(rel.id().isBlank(), "Relation has a blank id");
        }
    }
}
