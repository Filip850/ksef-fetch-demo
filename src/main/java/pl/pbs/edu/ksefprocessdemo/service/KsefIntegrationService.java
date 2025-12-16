package pl.pbs.edu.ksefprocessdemo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
public class KsefIntegrationService {

  private final DefaultCryptographyService defaultCryptographyService;
  private final KsefAuthorizationProvider kap;
  private final KSeFClient ksefClient;

  public KsefIntegrationService(DefaultCryptographyService defaultCryptographyService,
                                KsefAuthorizationProvider kap, KSeFClient ksefClient) {
    this.defaultCryptographyService = defaultCryptographyService;
    this.kap = kap;
    this.ksefClient = ksefClient;
  }


  private Set<KsefInvoice> fetchInvoicePackageRecursive(OffsetDateTime dateFrom, OffsetDateTime dateTo) throws ApiException{
    Set<KsefInvoice> invoices = new HashSet<>();


    log.info("[EXAMPLE] Getting invoice package...");
    EncryptionData encryptionData = defaultCryptographyService.getEncryptionData();
    InvoiceExportFilters filters = new InvoicesAsyncQueryFiltersBuilder()
        .withSubjectType(InvoiceQuerySubjectType.SUBJECT2)
        .withDateRange(new InvoiceQueryDateRange(
            InvoiceQueryDateType.INVOICING,
            dateFrom,
            dateTo
        ))
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




    log.info("[EXAMPLE] Invoice pooling ended with status code: {}", exportStatus.getStatus().getCode());

    if (exportStatus.getPackageParts().getIsTruncated())
      invoices.addAll(fetchInvoicePackageRecursive(exportStatus.getPackageParts().getLastPermanentStorageDate(), dateTo));

    //TODO: add  processed invoices from actual iter to Set.

    return invoices;
  }


  private InvoiceExportStatus poolUntilPackageReady(String referenceNumber) throws KsefPackagePoolException {
    log.info("[EXAMPLE] Pooling starts...");
    try {
      InvoiceExportStatus exportStatus;

      do { //TODO: DO Better Pooling 💀
        exportStatus = ksefClient.checkStatusAsyncQueryInvoice(
            referenceNumber,
            kap.getTokens().getAccessToken().getToken()
        );
        log.info("[EXAMPLE] Actual status code: {}", exportStatus.getStatus().getCode());
        Thread.sleep(1000);
      } while (exportStatus.getStatus().getCode() == 100);
      if (exportStatus.getStatus().getCode() != 200) throw new KsefPackagePoolException(exportStatus);
      return exportStatus;

    } catch (ApiException | InterruptedException exception) {
      throw new RuntimeException("Pooling failed", exception);
    }
  }
}
