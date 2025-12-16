package pl.pbs.edu.ksefprocessdemo.model;

import lombok.Getter;
import pl.pbs.edu.ksefprocessdemo.generated.Faktura;

import java.util.Objects;

@Getter
public class KsefInvoice {
  private final String ksefId;
  private final Faktura invoiceData;

  public KsefInvoice(String ksefId, Faktura invoiceData) {
    this.ksefId = ksefId;
    this.invoiceData = invoiceData;
  }


  // KsefId should be unique

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof KsefInvoice that)) return false;
    return Objects.equals(this.ksefId, that.ksefId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ksefId);
  }
}
