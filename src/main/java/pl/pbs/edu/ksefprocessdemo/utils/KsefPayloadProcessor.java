package pl.pbs.edu.ksefprocessdemo.utils;

import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.akmf.ksef.sdk.api.services.DefaultCryptographyService;
import pl.akmf.ksef.sdk.client.interfaces.KSeFClient;
import pl.akmf.ksef.sdk.client.model.invoice.InvoiceExportPackage;
import pl.akmf.ksef.sdk.client.model.invoice.InvoiceExportStatus;
import pl.akmf.ksef.sdk.client.model.invoice.InvoicePackagePart;
import pl.akmf.ksef.sdk.client.model.session.EncryptionData;
import pl.pbs.edu.ksefprocessdemo.generated.Faktura;
import pl.pbs.edu.ksefprocessdemo.model.KsefInvoice;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static pl.pbs.edu.ksefprocessdemo.utils.KsefUtils.*;

@Component
@Slf4j
public class KsefPayloadProcessor {

  private final KSeFClient ksefClient;
  private final DefaultCryptographyService defaultCryptographyService;

  public KsefPayloadProcessor(KSeFClient ksefClient, DefaultCryptographyService defaultCryptographyService) {
    this.ksefClient = ksefClient;
    this.defaultCryptographyService = defaultCryptographyService;
  }

  public Set<KsefInvoice> parseKsefPayload(InvoiceExportStatus exportStatus, EncryptionData encryptionData) {
    log.info("[EXAMPLE] Faktur wewnątrz paczki: {}", exportStatus.getPackageParts().getInvoiceCount());
    byte[] fullZip = getDecryptedZipFile(exportStatus, encryptionData);

    Set<KsefInvoice> invoices = new HashSet<>();
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(fullZip))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if(entry.getName().toLowerCase().endsWith(".xml")) {
          System.out.println("File: " + entry.getName());
          byte[] xmlFile = readZipEntry(zis);
          Faktura invoice = unwrapInvoice(xmlFile);

          invoices.add(new KsefInvoice(readKsefIdFromFileName(entry), invoice));
        }
      }
    } catch (JAXBException | IOException e) {
      throw new RuntimeException(e);
    }
    return invoices;
  }

  private byte[] getDecryptedZipFile(InvoiceExportStatus exportStatus, EncryptionData encryptionData){
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

    try {
      for (byte[] part : decrypted) {
        outputStream.write(part);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return outputStream.toByteArray();
  }
}
