package pl.marcinmilkowski.word_sketch.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared Jackson {@link ObjectMapper} instance for the whole application.
 *
 * <p>A single shared instance avoids silent serialisation divergence if Jackson is
 * ever configured (e.g. date formats, custom modules, feature flags). All layers
 * that need JSON parsing or generation should reference {@link #MAPPER} rather than
 * constructing their own instance.</p>
 */
public final class JsonUtils {

    /** Application-wide ObjectMapper. Never mutate after startup. */
    public static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {}
}
