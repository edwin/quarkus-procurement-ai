package com.edw.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.configuration.StringConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 *
 * The InfinispanChatMemoryStore is an implementation of the ChatMemoryStore interface, responsible
 * for managing chat messages in memory with the use of an Infinispan distributed cache. This
 * implementation ensures messages can be stored, retrieved, updated, or deleted while honoring
 * expiration and memory management configurations.
 *
 * It uses an Infinispan cache named "chat-memory", configured with parameters for data encoding,
 * expiration, and memory constraints. Initialization of the cache is handled automatically during
 * application startup.
 *
 * The class provides the following key functionalities:
 *
 * - Retrieving messages associated with a specified memory identifier.
 * - Updating stored messages for a specified memory identifier.
 * - Deleting messages for a specified memory identifier.
 *
 * Logging is included to track the initialization process and other important events.
 */
@ApplicationScoped
public class InfinispanChatMemoryStore implements ChatMemoryStore {

    @Inject
    RemoteCacheManager cacheManager;

    private RemoteCache<String, String> remoteCache;

    private final Logger logger = LoggerFactory.getLogger(InfinispanChatMemoryStore.class);

    @PostConstruct
    void init() {
        String xmlConfig = """
                <distributed-cache name="chat-memory">
                    <encoding>
                        <key media-type="application/x-protostream"/>
                        <value media-type="application/x-protostream"/>
                    </encoding>
                    <expiration lifespan="3600000" max-idle="1800000"/>
                    <memory max-count="100" when-full="REMOVE"/>
                </distributed-cache>
                """;

        remoteCache = cacheManager.administration()
                .getOrCreateCache("chat-memory", new StringConfiguration(xmlConfig));

        logger.info("Chat memory store initialized");
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = remoteCache.get(memoryId.toString());
        return json == null ? List.of()
                : ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        remoteCache.put(memoryId.toString(),
                ChatMessageSerializer.messagesToJson(messages),
                1, TimeUnit.HOURS);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        remoteCache.remove(memoryId.toString());
    }
}