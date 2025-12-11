package pl.pbs.edu.ksefprocessdemo.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.akmf.ksef.sdk.api.DefaultKsefClient;
import pl.akmf.ksef.sdk.api.KsefApiProperties;
import pl.akmf.ksef.sdk.api.services.*;
import pl.akmf.ksef.sdk.client.interfaces.*;
import pl.pbs.edu.ksefprocessdemo.config.props.KsefApiProps;
import pl.pbs.edu.ksefprocessdemo.utils.HttpClientBuilder;
import pl.pbs.edu.ksefprocessdemo.utils.HttpClientConfig;

import java.net.http.HttpClient;

@Configuration
@RequiredArgsConstructor
public class KsefClientConfig {

  @Bean
  public KsefApiProperties apiProperties() {
    return new KsefApiProps();
  }

  @Bean
  public CertificateService initDefaultCertificateService() {
    return new DefaultCertificateService();
  }

  @Bean
  public SignatureService initDefaultSignatureService() {
    return new DefaultSignatureService();
  }

  @Bean
  public VerificationLinkService initDefaultVerificationLinkService(KsefApiProperties ksefApiProperties) {
    return new DefaultVerificationLinkService(ksefApiProperties);
  }

  @Bean
  public QrCodeService initDefaultQrCodeService() {
    return new DefaultQrCodeService();
  }

  @Bean
  public KSeFClient initDefaultKsefClient() {
    ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    HttpClient apiClient = HttpClientBuilder.createHttpBuilder(new HttpClientConfig()).build();
    return new DefaultKsefClient(apiClient, apiProperties(), objectMapper);
  }

  @Bean
  public DefaultCryptographyService initDefaultCryptographyService(KSeFClient kSeFClient) {
    return new DefaultCryptographyService(kSeFClient);
  }
}
