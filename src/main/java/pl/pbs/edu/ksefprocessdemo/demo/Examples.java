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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static pl.pbs.edu.ksefprocessdemo.utils.KsefUtils.readZipEntry;
import static pl.pbs.edu.ksefprocessdemo.utils.KsefUtils.unwrapInvoice;

@Service
@Slf4j
public class Examples {


  private final KSeFClient ksefClient;
  private final KsefAuthorizationProvider kap;
  private final DefaultCryptographyService defaultCryptographyService;

  public Examples(
      KSeFClient ksefClient,
      KsefAuthorizationProvider ksefAuthorizationProvider,
      DefaultCryptographyService defaultCryptographyService
  ) {
    this.ksefClient = ksefClient;
    this.kap = ksefAuthorizationProvider;
    this.defaultCryptographyService = defaultCryptographyService;
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
  public void handleInvoicePackage() throws IOException, InterruptedException, ApiException {
    handleInvoicePackage(OffsetDateTime.now().minusYears(1),
        OffsetDateTime.now().plusDays(10));
  }


  public void handleInvoicePackage(OffsetDateTime dateFrom, OffsetDateTime dateTo) throws ApiException, InterruptedException, IOException {
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
    InvoiceExportStatus exportStatus;
    log.info("[EXAMPLE] Pooling starts...");
    do {
      exportStatus = ksefClient.checkStatusAsyncQueryInvoice(
          response.getReferenceNumber(),
          kap.getTokens().getAccessToken().getToken()
      );
      log.info("[EXAMPLE] Actual status code: {}", exportStatus.getStatus().getCode());
      Thread.sleep(1000);
    } while (exportStatus.getStatus().getCode() == 100);
    log.info("[EXAMPLE] Invoice pooling ended with status code: {}", exportStatus.getStatus().getCode());

//    ============================================================================================================
    //TODO: Paczka może mieć flagę isTruncated i wtedy trzeba pobrać kolejne faktury na podstawie dat
    //      exportStatus.getPackageParts().getLastPermanentStorageDate() to data ostatniej faktury w paczki,
    //      Kontynuujemy więc dalej od danej daty :> SEE (Obsługa obciętych paczek (IsTruncated)) https://github.com/CIRFMF/ksef-docs/blob/main/pobieranie-faktur/przyrostowe-pobieranie-faktur.md
    if (exportStatus.getPackageParts().getIsTruncated())
      handleInvoicePackage(exportStatus.getPackageParts().getLastPermanentStorageDate(), dateTo);

//    ============================================================================================================
    if (exportStatus.getStatus().getCode() == 200) {
      log.info("[EXAMPLE] Faktur wewnątrz paczki: {}", exportStatus.getPackageParts().getInvoiceCount());
      InvoiceExportPackage packages = exportStatus.getPackageParts();
      List<InvoicePackagePart> partUrls = packages.getParts();
      List<byte[]> parts = partUrls.stream().map(ksefClient::downloadPackagePart).toList();

      List<byte[]> decrypted = parts
          .stream()
          .map(part -> defaultCryptographyService.decryptBytesWithAes256(
              part,
              encryptionData.cipherKey(),
              encryptionData.cipherIv()
          ))
          .toList();

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

      for (byte[] part : decrypted) {
        outputStream.write(part);
      }

      byte[] fullZip = outputStream.toByteArray();

      try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(fullZip))) {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
          if(entry.getName().toLowerCase().endsWith(".xml")) {
            System.out.println("File: " + entry.getName());
            byte[] xmlFile = readZipEntry(zis);
            Faktura invoice = unwrapInvoice(xmlFile);

            log.info("========================");
            log.info(invoice.getPodmiot1().getDaneIdentyfikacyjne().getNazwa());
            log.info("sprzedał pewny towar do:");
            log.info(invoice.getPodmiot2().getDaneIdentyfikacyjne().getNazwa());
            log.info("========================");

          }
        }
      } catch (JAXBException e) {
        throw new RuntimeException(e);
      }
    }
  }

}
