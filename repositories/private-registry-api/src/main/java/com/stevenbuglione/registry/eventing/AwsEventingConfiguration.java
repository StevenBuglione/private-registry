package com.stevenbuglione.registry.eventing;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "registry.eventing", name = "enabled", havingValue = "true")
public class AwsEventingConfiguration {

    private static final ClientOverrideConfiguration BOUNDED_CALLS = ClientOverrideConfiguration.builder()
            .apiCallAttemptTimeout(Duration.ofSeconds(3))
            .apiCallTimeout(Duration.ofSeconds(5))
            .build();

    @Bean
    @ConditionalOnMissingBean
    EventBridgeClient eventBridgeClient(EventingProperties properties) {
        var builder = EventBridgeClient.builder()
                .region(Region.of(properties.region()))
                .overrideConfiguration(BOUNDED_CALLS);
        configureLocalEndpoint(builder, properties.endpoint());
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    SqsClient sqsClient(EventingProperties properties) {
        var builder = SqsClient.builder()
                .region(Region.of(properties.region()))
                .overrideConfiguration(BOUNDED_CALLS);
        configureLocalEndpoint(builder, properties.endpoint());
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    S3Client s3Client(EventingProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .overrideConfiguration(BOUNDED_CALLS)
                .forcePathStyle(true);
        if (properties.endpoint() != null) {
            builder.endpointOverride(properties.endpoint())
                    .credentialsProvider(localCredentials());
        }
        return builder.build();
    }

    private static void configureLocalEndpoint(
            software.amazon.awssdk.awscore.client.builder.AwsClientBuilder<?, ?> builder, URI endpoint) {
        if (endpoint != null) {
            builder.endpointOverride(endpoint).credentialsProvider(localCredentials());
        }
    }

    private static StaticCredentialsProvider localCredentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create("localstack", "localstack"));
    }
}
