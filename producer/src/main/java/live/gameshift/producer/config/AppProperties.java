package live.gameshift.producer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// Binds all application.properties values into typed nested classes.
// Spring's relaxed binding maps kebab-case keys to camelCase fields automatically:
//   aws.kinesis.stream-name  →  aws.kinesis.streamName
//   api.sports.base-url      →  api.sports.baseUrl
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Aws aws = new Aws();
    private final Api api = new Api();

    public Aws getAws() { return aws; }
    public Api getApi() { return api; }

    public static class Aws {
        private String region;
        private final Kinesis kinesis = new Kinesis();
        private final Secrets secrets = new Secrets();

        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public Kinesis getKinesis() { return kinesis; }
        public Secrets getSecrets() { return secrets; }

        public static class Kinesis {
            private String streamName;
            public String getStreamName() { return streamName; }
            public void setStreamName(String streamName) { this.streamName = streamName; }
        }

        public static class Secrets {
            private String apiSportsKeyArn;
            public String getApiSportsKeyArn() { return apiSportsKeyArn; }
            public void setApiSportsKeyArn(String apiSportsKeyArn) { this.apiSportsKeyArn = apiSportsKeyArn; }
        }
    }

    public static class Api {
        private final Sports sports = new Sports();
        public Sports getSports() { return sports; }

        public static class Sports {
            private long pollIntervalMs;
            private boolean mockMode;
            private java.util.Map<live.gameshift.producer.model.SportType, SportConfig> configs = new java.util.HashMap<>();

            public long getPollIntervalMs() { return pollIntervalMs; }
            public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
            public boolean isMockMode() { return mockMode; }
            public void setMockMode(boolean mockMode) { this.mockMode = mockMode; }
            public java.util.Map<live.gameshift.producer.model.SportType, SportConfig> getConfigs() { return configs; }
            public void setConfigs(java.util.Map<live.gameshift.producer.model.SportType, SportConfig> configs) { this.configs = configs; }
        }
    }

    public static class SportConfig {
        private String baseUrl;
        private String fixtureId;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getFixtureId() { return fixtureId; }
        public void setFixtureId(String fixtureId) { this.fixtureId = fixtureId; }
    }
}
