package com.studychatbot.backend.global.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;

@Configuration
public class RedisVectorStoreConfig {

    // @ConditionalOnMissingBean(RedisVectorStore) 때문에 이 Bean이 존재하면 auto-configuration이 물러남.
    // documentId / userId 필터를 쓰려면 인덱스 스키마에 MetadataField를 명시 등록해야 한다.
    @Bean
    public RedisVectorStore vectorStore(
            EmbeddingModel embeddingModel,
            JedisConnectionFactory jedisConnectionFactory,
            @Value("${spring.ai.vectorstore.redis.index-name}") String indexName,
            @Value("${spring.ai.vectorstore.redis.prefix}") String prefix,
            @Value("${spring.ai.vectorstore.redis.initialize-schema:true}") boolean initializeSchema,
            @Value("${app.embedding.dimensions}") int embeddingDimensions) {

        JedisPooled jedisPooled = createJedisPooled(jedisConnectionFactory);

        // RedisVectorStore는 Builder에 차원 설정 메서드가 없고, 인덱스 DIM을
        // embeddingModel.dimensions() 반환값으로만 결정한다.
        // Spring AI 버그(768 반환)를 래퍼로 고정하여 우회한다.
        EmbeddingModel dimensionFixed = new FixedDimensionEmbeddingModel(embeddingModel, embeddingDimensions);

        return RedisVectorStore.builder(jedisPooled, dimensionFixed)
                .indexName(indexName)
                .prefix(prefix)
                .initializeSchema(initializeSchema)
                // TAG 타입: ID 값은 토크나이징 없이 정확히 일치해야 하므로 TEXT가 아닌 TAG 사용
                .metadataFields(
                        RedisVectorStore.MetadataField.tag("documentId"),
                        RedisVectorStore.MetadataField.tag("userId")
                )
                .build();
    }

    // auto-configuration의 jedisPooled() 로직과 동일 — SSL, password, timeout 설정 포함
    private JedisPooled createJedisPooled(JedisConnectionFactory factory) {
        JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .ssl(factory.isUseSsl())
                .clientName(factory.getClientName())
                .timeoutMillis(factory.getTimeout())
                .password(factory.getPassword())
                .build();
        return new JedisPooled(new HostAndPort(factory.getHostName(), factory.getPort()), clientConfig);
    }

    // dimensions()만 고정값으로 덮어쓰고, 실제 임베딩 호출은 원본 모델에 위임한다.
    // EmbeddingModel의 나머지 default 메서드들은 내부적으로 call()을 호출하므로 별도 위임 불필요.
    private static final class FixedDimensionEmbeddingModel implements EmbeddingModel {

        private final EmbeddingModel delegate;
        private final int fixedDimension;

        FixedDimensionEmbeddingModel(EmbeddingModel delegate, int fixedDimension) {
            this.delegate = delegate;
            this.fixedDimension = fixedDimension;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return delegate.call(request);
        }

        @Override
        public float[] embed(Document document) {
            return delegate.embed(document);
        }

        @Override
        public int dimensions() {
            return fixedDimension;
        }
    }
}
