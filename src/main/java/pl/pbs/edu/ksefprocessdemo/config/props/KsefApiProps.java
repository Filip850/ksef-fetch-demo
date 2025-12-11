package pl.pbs.edu.ksefprocessdemo.config.props;

import org.springframework.beans.factory.annotation.Value;
import pl.akmf.ksef.sdk.api.KsefApiProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class KsefApiProps extends KsefApiProperties {

  @Value("${ksef.url}")
  private String ksefUrl;

  @Override
  public String getBaseUri() {
    return ksefUrl;
  }

  @Override
  public Duration getRequestTimeout() {
    return Duration.ofSeconds(100);
  }

  @Override
  public Map<String, String> getDefaultHeaders() {
    Map<String, String> headers = new HashMap<>();

    return headers;
  }
}
