package pl.pbs.edu.ksefprocessdemo.demo;

import jakarta.annotation.PostConstruct;
import jakarta.xml.bind.JAXBException;
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
import pl.pbs.edu.ksefprocessdemo.generated.Faktura;
import pl.pbs.edu.ksefprocessdemo.model.KsefInvoice;
import pl.pbs.edu.ksefprocessdemo.service.KsefIntegrationService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static pl.pbs.edu.ksefprocessdemo.utils.KsefUtils.*;

@Service
@Slf4j
public class Examples {


  private final KSeFClient ksefClient;
  private final KsefAuthorizationProvider kap;
  private final DefaultCryptographyService defaultCryptographyService;
  private final KsefIntegrationService ksefIntegrationService;

  public Examples(
      KSeFClient ksefClient,
      KsefAuthorizationProvider ksefAuthorizationProvider,
      DefaultCryptographyService defaultCryptographyService,
      KsefIntegrationService ksefIntegrationService
  ) {
    this.ksefClient = ksefClient;
    this.kap = ksefAuthorizationProvider;
    this.defaultCryptographyService = defaultCryptographyService;
    this.ksefIntegrationService = ksefIntegrationService;
  }

  // Example No. 1 - File Extraction
  @PostConstruct
  public void processFileInvoice() throws JAXBException {
    log.info("[EXAMPLE] Processing file invoice...");
    Faktura faktura = unwrapInvoice(new File(
        "src\\main\\java\\pl\\pbs\\edu\\ksefprocessdemo\\demo\\mock.xml"));
    log.info("[FAKUTRA TEST]: " + faktura.getPodmiot1().getDaneIdentyfikacyjne().getNIP());
  }

  // Example No. 2 - API extraction
  @PostConstruct
  public void processApiInvoice() throws ApiException, JAXBException {
    log.info("[EXAMPLE] Processing API invoice...");
    byte[] rawFaktura = ksefClient.getInvoice(
        "5130206659-20251124-01008099E34C-20",
        kap.getTokens().getAccessToken().getToken()
    );
    Faktura invoice = unwrapInvoice(rawFaktura);
    log.info(invoice.getPodmiot2().getDaneIdentyfikacyjne().getNazwa());
  }

  // Example No. 2 - API package download call
  @PostConstruct
  public void handleInvoicePackage(){
    handleInvoicePackage(OffsetDateTime.now().minusYears(1),
        OffsetDateTime.now().plusDays(10));
  }


  public void handleInvoicePackage(OffsetDateTime dateFrom, OffsetDateTime dateTo) {

      Set<KsefInvoice> invoices = ksefIntegrationService.fetchInvoicePackageBetween(dateFrom, dateTo);
      invoices.forEach(invoice -> {
        log.info("=======FAKTURA START==============");
        log.info("KSeF ID Number: {}", invoice.getKsefId());
        log.info(invoice.getInvoiceData().getPodmiot1().getDaneIdentyfikacyjne().getNazwa());
        log.info("Sprzedał towar X do.:");
        log.info(invoice.getInvoiceData().getPodmiot2().getDaneIdentyfikacyjne().getNazwa());
        if (invoice.getInvoiceData().getPodmiot3() != null && !invoice.getInvoiceData().getPodmiot3().isEmpty()) {
          log.info("PODMIOT 3:");
          invoice
              .getInvoiceData()
              .getPodmiot3()
              .forEach(podmiot3 -> log.info(podmiot3.getDaneIdentyfikacyjne().getNazwa()));
          log.info("PODMIOt 3 - END");
        }
        log.info("========FAKTURA END===============");
        log.info("");
        log.info("");
        log.info("");
      });

    }


}
