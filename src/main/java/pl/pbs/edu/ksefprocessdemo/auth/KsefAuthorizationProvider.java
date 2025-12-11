package pl.pbs.edu.ksefprocessdemo.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.akmf.ksef.sdk.api.builders.auth.AuthKsefTokenRequestBuilder;
import pl.akmf.ksef.sdk.api.services.DefaultCryptographyService;
import pl.akmf.ksef.sdk.client.interfaces.KSeFClient;
import pl.akmf.ksef.sdk.client.model.ApiException;
import pl.akmf.ksef.sdk.client.model.auth.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.*;

/**
 * This spring component provides ksef tokens in AuthOperationStatusResponse object using _getTokens()_ method
 */
@Component
@Slf4j
public class KsefAuthorizationProvider {
  private final KSeFClient ksefClient;
  private final DefaultCryptographyService defaultCryptographyService;
  private final ScheduledExecutorService poolingScheduler = Executors.newSingleThreadScheduledExecutor();
  private final CompletableFuture<AuthStatus> poolingAwait = new CompletableFuture<>();
  @Value("${ksef.apiToken}")
  private String ksefToken;
  @Value("${ksef.nip}")
  private String NIP;
  private AuthOperationStatusResponse tokens;

  public KsefAuthorizationProvider(KSeFClient ksefClient, DefaultCryptographyService defaultCryptographyService) {
    this.ksefClient = ksefClient;
    this.defaultCryptographyService = defaultCryptographyService;
  }

  /**
   * Synchronized to ensure thread safety and avoid redundant requests in parallel workflows. (No need for ReentrantLock)
   * It could be changed to a strategy for future implementation of Auth from certificate.
   *
   * @return Returns the current tokens, renewing them if expired.
   */
  public synchronized AuthOperationStatusResponse getTokens() {
    if (isTokenExpired()) renewTokens();

    return tokens;
  }

  private void renewTokens() {
    try {
      log.debug("Renewing tokens...");
      this.tokens = getKsefAuthentication();
    } catch (ApiException e) {
      log.error("Error occurred while renewing tokens in KsefAuthorizationProvider", e);
    }

  }

  private boolean isTokenExpired() {
    if (tokens == null) return true;
    return OffsetDateTime.now(ZoneOffset.UTC).isAfter(tokens.getAccessToken().getValidUntil()) || OffsetDateTime
        .now(ZoneOffset.UTC)
        .isAfter(tokens.getRefreshToken().getValidUntil());
  }

  private AuthOperationStatusResponse getKsefAuthentication() throws ApiException {
    AuthenticationChallengeResponse challenge = ksefClient.getAuthChallenge();
    byte[] encryptedToken = encrypt(EncryptionMethod.Rsa, challenge);

    AuthKsefTokenRequest authKsefTokenRequest = new AuthKsefTokenRequestBuilder()
        .withChallenge(challenge.getChallenge())
        .withContextIdentifier(new ContextIdentifier(ContextIdentifier.IdentifierType.NIP, NIP))
        .withEncryptedToken(Base64.getEncoder().encodeToString(encryptedToken))
        .build();
    SignatureResponse signature = ksefClient.authenticateByKSeFToken(authKsefTokenRequest);
    poolingScheduler.scheduleAtFixedRate(getPoolingTask(signature), 0, 1, TimeUnit.SECONDS);

    try {
      poolingAwait.get();
    } catch (Exception e) {
      log.error(e.getMessage());
    }
    return ksefClient.redeemToken(signature.getAuthenticationToken().getToken());
  }

  private Runnable getPoolingTask(SignatureResponse signature) {
    return new Runnable() {
      private int retries = 0;

      @Override
      public void run() {
        try {
          AuthStatus authStatus = ksefClient.getAuthStatus(
              signature.getReferenceNumber(),
              signature.getAuthenticationToken().getToken()
          );

          if (authStatus.getStatus().getCode() == 200) {
            poolingAwait.complete(authStatus);
            poolingScheduler.shutdown();
            return;
          }
          if (++retries > 30) {
            poolingAwait.completeExceptionally(new TimeoutException("Token pooling failed after " + retries + " attempt(s)"));
            poolingScheduler.shutdown();
          }
        } catch (ApiException e) {
          log.error(e.getMessage());
        }
      }
    };
  }

  private byte[] encrypt(EncryptionMethod encryptionMethod, AuthenticationChallengeResponse ch) {
    return switch (encryptionMethod) {
      case Rsa -> defaultCryptographyService.encryptKsefTokenWithRSAUsingPublicKey(ksefToken, ch.getTimestamp());
      case ECDsa -> defaultCryptographyService.encryptKsefTokenWithECDsaUsingPublicKey(ksefToken, ch.getTimestamp());
    };
  }
}
