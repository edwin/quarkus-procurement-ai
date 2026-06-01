package com.edw.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.configuration.StringConfiguration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <pre>
 *  com.edw.config.InfinispanChatMemoryStore
 * </pre>
 *
 * @author Muhammad Edwin < edwin at redhat dot com >
 * 29 May 2026 22:20
 */
@ApplicationScoped
public class InfinispanChatMemoryStore implements ChatMemoryStore {

    @Inject
    RemoteCacheManager cacheManager;

    private RemoteCache remoteCache;

    private RemoteCache<String, String> getCache() {
        String xmlConfig = """
        <infinispan>
            <cache-container>
                <distributed-cache name="chat-memory">
                    <encoding>
                        <key media-type="application/x-protostream"/>
                        <value media-type="application/x-protostream"/>
                    </encoding>
                    <memory max-count="100" when-full="REMOVE"/>
                </distributed-cache>
            </cache-container>
        </infinispan>
        """;

        if(remoteCache == null) {
            remoteCache = cacheManager.administration().getOrCreateCache("chat-memory", new StringConfiguration(xmlConfig));
        }

        return remoteCache;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = getCache().get(memoryId.toString());
        return json == null ? List.of()
                : ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        getCache().put(memoryId.toString(),
                ChatMessageSerializer.messagesToJson(messages),
                1, TimeUnit.HOURS);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        getCache().remove(memoryId.toString());
    }
}