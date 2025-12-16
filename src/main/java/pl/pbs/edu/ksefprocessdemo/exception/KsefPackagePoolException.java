package pl.pbs.edu.ksefprocessdemo.exception;

import lombok.Getter;
import pl.akmf.ksef.sdk.client.model.invoice.InvoiceExportStatus;

@Getter
public class KsefPackagePoolException extends RuntimeException {

  private final InvoiceExportStatus exportStatus;

  public KsefPackagePoolException(InvoiceExportStatus exportStatus) {
    super(String.format(
        "Getting package failed with code: %d - %s (DETAILS: %s)",
        exportStatus.getStatus().getCode(),
        exportStatus.getStatus().getDescription(),
        exportStatus.getStatus().getDetails().toString()
    ));
    this.exportStatus = exportStatus;
  }
}
