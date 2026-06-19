package live.gameshift.producer.client;

import live.gameshift.producer.config.AppProperties;
import live.gameshift.producer.config.SecretsService;
import live.gameshift.producer.model.SportType;
import live.gameshift.producer.normalizer.SportNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

class ApiSportsClientTest {

    @Test
    void constructor_wiresUpWithoutThrowing() {
        SecretsService mockSecrets = mock(SecretsService.class);
        when(mockSecrets.getApiSportsKey()).thenReturn("test-key");
        
        AppProperties mockProps = mock(AppProperties.class);
        AppProperties.Api mockApi = mock(AppProperties.Api.class);
        AppProperties.Api.Sports mockSports = mock(AppProperties.Api.Sports.class);
        when(mockProps.getApi()).thenReturn(mockApi);
        when(mockApi.getSports()).thenReturn(mockSports);

        AppProperties.SportConfig mockConfig = new AppProperties.SportConfig();
        mockConfig.setBaseUrl("https://example.com");
        mockConfig.setFixtureId("123");

        when(mockSports.getConfigs()).thenReturn(Map.of(SportType.SOCCER, mockConfig));

        SportNormalizer mockNormalizer = mock(SportNormalizer.class);
        when(mockNormalizer.getSportType()).thenReturn(SportType.SOCCER);

        new ApiSportsClient(mockSecrets, mockProps, List.of(mockNormalizer));
    }
}
