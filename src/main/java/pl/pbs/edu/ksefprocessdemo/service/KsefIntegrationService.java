package pl.pbs.edu.ksefprocessdemo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.akmf.ksef.sdk.api.HttpStatus;
import pl.akmf.ksef.sdk.api.builders.invoices.InvoicesAsyncQueryFiltersBuilder;
import pl.akmf.ksef.sdk.api.services.DefaultCryptographyService;
import pl.akmf.ksef.sdk.client.interfaces.KSeFClient;
import pl.akmf.ksef.sdk.client.model.ApiException;
import pl.akmf.ksef.sdk.client.model.invoice.*;
import pl.akmf.ksef.sdk.client.model.session.EncryptionData;
import pl.akmf.ksef.sdk.client.model.session.EncryptionInfo;
import pl.pbs.edu.ksefprocessdemo.auth.KsefAuthorizationProvider;
import pl.pbs.edu.ksefprocessdemo.exception.KsefPackagePoolException;
import pl.pbs.edu.ksefprocessdemo.model.KsefInvoice;
import pl.pbs.edu.ksefprocessdemo.utils.KsefPayloadProcessor;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

@Slf4j
@Service
public class KsefIntegrationService {

  private final DefaultCryptographyService defaultCryptographyService;
  private final KsefAuthorizationProvider kap;
  private final KSeFClient ksefClient;
  private final KsefPayloadProcessor ksefPayloadProcessor;


  public KsefIntegrationService(
      DefaultCryptographyService defaultCryptographyService,
      KsefAuthorizationProvider kap,
      KSeFClient ksefClient,
      KsefPayloadProcessor ksefPayloadProcessor
  ) {
    this.defaultCryptographyService = defaultCryptographyService;
    this.kap = kap;
    this.ksefClient = ksefClient;
    this.ksefPayloadProcessor = ksefPayloadProcessor;
  }

  public Set<KsefInvoice> fetchInvoicePackageBetween(OffsetDateTime dateFrom, OffsetDateTime dateTo) {
    try {
      return fetchInvoicePackageRecursive(dateFrom, dateTo);
    } catch (ApiException e) {
      throw new RuntimeException(e);
    }
  }


  private Set<KsefInvoice> fetchInvoicePackageRecursive(
      OffsetDateTime dateFrom,
      OffsetDateTime dateTo
  ) throws ApiException {
    Set<KsefInvoice> invoices = new HashSet<>();


    EncryptionData encryptionData = defaultCryptographyService.getEncryptionData();
    InvoiceExportFilters filters = new InvoicesAsyncQueryFiltersBuilder()
        .withSubjectType(InvoiceQuerySubjectType.SUBJECT2)
        .withDateRange(new InvoiceQueryDateRange(InvoiceQueryDateType.INVOICING, dateFrom, dateTo))
        .build();

    InvoiceExportRequest request = new InvoiceExportRequest(
        new EncryptionInfo(
            encryptionData.encryptionInfo().getEncryptedSymmetricKey(),
            encryptionData.encryptionInfo().getInitializationVector()
        ), filters
    );
    InitAsyncInvoicesQueryResponse response = ksefClient.initAsyncQueryInvoice(
        request,
        kap.getTokens().getAccessToken().getToken()
    );
    InvoiceExportStatus exportStatus = poolUntilPackageReady(response.getReferenceNumber());


    // Because 10k invoices is limit before truncated, I will parse payload recursively too.
    if (exportStatus.getPackageParts().getIsTruncated()) // Set will make sure it's unique.
      invoices.addAll(fetchInvoicePackageRecursive(
          exportStatus.getPackageParts().getLastPermanentStorageDate(),
          dateTo
      ));
    invoices.addAll(ksefPayloadProcessor.parseKsefPayload(exportStatus, encryptionData));

    return invoices;
  }


  private InvoiceExportStatus poolUntilPackageReady(String referenceNumber) throws KsefPackagePoolException {
    log.debug("Package pooling starts...");
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    CompletableFuture<InvoiceExportStatus> future = new CompletableFuture<>();

    final long timeoutMillis = 60_000 * 30; //30 Minutes
    final long start = System.currentTimeMillis();

    scheduler.scheduleAtFixedRate(
        () -> {
          try {
            InvoiceExportStatus status = ksefClient.checkStatusAsyncQueryInvoice(
                referenceNumber,
                kap.getTokens().getAccessToken().getToken()
            );
            log.debug("[DEBUG] Actual status code of pooling: {}", status.getStatus().getCode());
            if (status.getStatus().getCode() == HttpStatus.OK.getCode()) {
              future.complete(status);
              scheduler.shutdown();
            }

            if (status.getStatus().getCode() != HttpStatus.CONTINUE.getCode()) {
              future.completeExceptionally(new KsefPackagePoolException(status));
            }

            if (System.currentTimeMillis() - start > timeoutMillis) {
              future.completeExceptionally(new TimeoutException("Invoice export timeout"));
              scheduler.shutdown();
            }

          } catch (ApiException e) {
            future.completeExceptionally(e);
            scheduler.shutdown();
          }
        }, 0, 10, TimeUnit.SECONDS
    );

    try {
      return future.get();
    } catch (ExecutionException | InterruptedException e) {
      throw new RuntimeException(e);
    } finally {
      scheduler.shutdownNow();
    }
  }
}
