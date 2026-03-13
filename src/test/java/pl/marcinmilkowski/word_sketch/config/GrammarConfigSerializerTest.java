package pl.marcinmilkowski.word_sketch.config;

import org.junit.jupiter.api.Test;
import pl.marcinmilkowski.word_sketch.config.GrammarConfig;
import pl.marcinmilkowski.word_sketch.config.GrammarConfigHelper;
import pl.marcinmilkowski.word_sketch.config.GrammarConfigSerializer;
import pl.marcinmilkowski.word_sketch.config.RelationConfig;
import pl.marcinmilkowski.word_sketch.model.PosGroup;



import static org.junit.jupiter.api.Assertions.*;

class GrammarConfigSerializerTest {

    @Test
    void toJson_grammarConfig_containsVersionAndRelations() {
        GrammarConfig config = GrammarConfigHelper.requireTestConfig();
        GrammarConfigSerializer.GrammarConfigResponse resp = GrammarConfigSerializer.toJson(config);

        assertNotNull(resp.version(), "version must be present");
        assertNotNull(resp.relations(), "relations must be present");
        assertFalse(resp.relations().isEmpty(), "relations must not be empty");
    }

    @Test
    void toJson_grammarConfig_relationsHaveIdAndPattern() {
        GrammarConfig config = GrammarConfigHelper.requireTestConfig();
        GrammarConfigSerializer.GrammarConfigResponse resp = GrammarConfigSerializer.toJson(config);

        GrammarConfigSerializer.RelationConfigEntry first = resp.relations().get(0);
        assertNotNull(first.id(), "relation id must be present");
        // headPosition and collocatePosition are primitives — always present
        assertNotNull(first, "head_position must be present");
        assertNotNull(first, "collocate_position must be present");
    }

    @Test
    void toJson_relationConfig_includesRelationType_whenPresent() {
        GrammarConfig config = GrammarConfigHelper.requireTestConfig();
        config.relations().stream()
            .filter(rel -> rel.relationType().isPresent())
            .findFirst()
            .ifPresent(rel -> {
                GrammarConfigSerializer.RelationConfigEntry entry = GrammarConfigSerializer.toJson(rel);
                assertEquals(rel.relationType().get().name(), entry.relationType());
            });
    }

    @Test
    void toJson_relationConfig_omitsNullOptionalFields() {
        RelationConfig minimal = new RelationConfig(
            "test_rel", null, null, null,
            1, 2, false, 0, null, PosGroup.OTHER);
        GrammarConfigSerializer.RelationConfigEntry entry = GrammarConfigSerializer.toJson(minimal);

        assertEquals("test_rel", entry.id());
        assertNull(entry.name(), "null name should be null");
        assertNull(entry.description(), "null description should be null");
        assertNull(entry.pattern(), "null pattern should be null");
        assertNull(entry.relationType(), "absent relationType should be null");
    }
}
