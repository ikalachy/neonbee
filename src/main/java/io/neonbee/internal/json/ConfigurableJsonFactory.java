package io.neonbee.internal.json;

import static io.vertx.core.json.jackson.JacksonCodec.fromParser;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.buffer.ByteBufInputStream;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.jackson.JacksonFactory;
import io.vertx.core.json.jackson.VertxModule;
import io.vertx.core.spi.json.JsonCodec;

/**
 * This class is similar to the Vert.x {@link JacksonFactory} but allows for configuration in JSON parsing, e.g. to
 * change the maximum allowed length of JSON strings.
 */
public class ConfigurableJsonFactory implements io.vertx.core.spi.JsonFactory {
    /**
     * A singleton instance to the {@link ConfigurableJsonCodec}.
     */
    public static final ConfigurableJsonCodec CODEC;

    static {
        ConfigurableJsonCodec codec;

        try {
            codec = new ConfigurableDatabindCodec();
        } catch (Exception ignore) {
            codec = new ConfigurableJacksonCodec();
        }

        CODEC = codec;
    }

    @Override
    public JsonCodec codec() {
        return CODEC;
    }

    /**
     * A JSON codec, that can be used to on-the-fly, adapt the Jackson factory used.
     */
    public abstract static class ConfigurableJsonCodec implements JsonCodec {
        @Override
        public <T> T fromString(String json, Class<T> clazz) {
            return fromParser(createParser(json), clazz);
        }

        @Override
        public <T> T fromBuffer(Buffer json, Class<T> clazz) {
            return fromParser(createParser(json), clazz);
        }

        @Override
        public <T> T fromValue(Object json, Class<T> toValueType) {
            return JacksonFactory.CODEC.fromValue(json, toValueType);
        }

        @Override
        public String toString(Object object, boolean pretty) {
            return JacksonFactory.CODEC.toString(object, pretty);
        }

        @Override
        public Buffer toBuffer(Object object, boolean pretty) {
            return JacksonFactory.CODEC.toBuffer(object, pretty);
        }

        /**
         * Changes the maximum allowed string size for JSON parsing.
         *
         * @see StreamReadConstraints.Builder#maxStringLength(int)
         * @param maxLength the maximum string length allowed
         */
        public abstract void setMaxStringLength(int maxLength);

        /**
         * Create a {@link JsonParser} for a given string.
         *
         * @param str the string to create the parser for
         * @return a new {@link JsonParser}
         */
        protected abstract JsonParser createParser(String str);

        /**
         * Create a {@link JsonParser} for a given buffer.
         *
         * @param buf the buffer to create the parser for
         * @return a new {@link JsonParser}
         */
        protected abstract JsonParser createParser(Buffer buf);
    }

    private static class ConfigurableJacksonCodec extends ConfigurableJsonCodec {
        protected final JsonFactory factory;

        protected ConfigurableJacksonCodec() {
            this(new JsonFactory());
        }

        protected ConfigurableJacksonCodec(JsonFactory factory) {
            super();
            // Non-standard JSON but we allow C style comments in our JSON
            (this.factory = factory).configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        }

        @Override
        public void setMaxStringLength(int maxLength) {
            factory.setStreamReadConstraints(StreamReadConstraints.builder().maxStringLength(maxLength).build());
        }

        @Override
        protected JsonParser createParser(String str) {
            try {
                return factory.createParser(str);
            } catch (IOException e) {
                throw new DecodeException("Failed to decode:" + e.getMessage(), e);
            }
        }

        @Override
        protected JsonParser createParser(Buffer buf) {
            try {
                return factory.createParser((InputStream) new ByteBufInputStream(buf.getByteBuf()));
            } catch (IOException e) {
                throw new DecodeException("Failed to decode:" + e.getMessage(), e);
            }
        }
    }

    private static class ConfigurableDatabindCodec extends ConfigurableJacksonCodec {
        @SuppressWarnings("unused")
        private final ObjectMapper mapper;

        private ConfigurableDatabindCodec() {
            this(new ObjectMapper());
        }

        private ConfigurableDatabindCodec(ObjectMapper mapper) {
            super(mapper.getFactory());

            // Non-standard JSON but we allow C style comments in our JSON
            (this.mapper = mapper).configure(JsonParser.Feature.ALLOW_COMMENTS, true);
            mapper.registerModule(new VertxModule());
        }
    }
}
