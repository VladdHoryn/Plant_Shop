package com.plantshop.catalog.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {

    public static final String PLANT_CREATED = "plant-created";

    @Bean
    public NewTopic plantCreatedTopic() {
        return TopicBuilder.name(PLANT_CREATED).partitions(3).replicas(1).build();
    }
}
