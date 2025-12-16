package pl.pbs.edu.ksefprocessdemo.utils;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import pl.pbs.edu.ksefprocessdemo.generated.Faktura;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class KsefUtils {
  public static Faktura unwrapInvoice(byte[] rawInvoiceXmlOrResponse) throws JAXBException {
    JAXBContext ctx = JAXBContext.newInstance(Faktura.class);
    Unmarshaller um = ctx.createUnmarshaller();
    ByteArrayInputStream bais = new ByteArrayInputStream(rawInvoiceXmlOrResponse);
    return (Faktura) um.unmarshal(bais);
  }

  public static Faktura unwrapInvoice(File rawInvoiceXmlOrResponse) throws JAXBException {
    JAXBContext ctx = JAXBContext.newInstance(Faktura.class);
    Unmarshaller um = ctx.createUnmarshaller();
    return (Faktura) um.unmarshal(rawInvoiceXmlOrResponse);
  }

  public static byte[] readZipEntry(ZipInputStream zipInputStream) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int len;

    while ((len = zipInputStream.read(buffer)) > 0) {
      baos.write(buffer, 0, len);
    }

    return baos.toByteArray();
  }

  public static String readKsefIdFromFileName(ZipEntry entry) {
    return entry.getName().substring(0, entry.getName().lastIndexOf("."));
  }
}
