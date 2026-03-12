package pl.marcinmilkowski.word_sketch.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared Jackson {@link ObjectMapper} instance for the whole application.
 *
 * <p>A single shared instance avoids silent serialisation divergence if Jackson is
 * ever configured (e.g. date formats, custom modules, feature flags). All layers
 * that need JSON parsing or generation should use {@link #mapper()} rather than
 * constructing their own instance.</p>
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {}

    /** Returns the application-wide {@link ObjectMapper}. Do not mutate the returned instance. */
    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
