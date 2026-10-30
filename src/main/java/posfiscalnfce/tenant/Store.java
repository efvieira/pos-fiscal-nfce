package posfiscalnfce.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

/** Loja emitente (tenant). Guarda os dados fiscais e a numeração da NFC-e. */
@Entity
@Table(name = "stores")
public class Store {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 14)
    private String cnpj;

    @Column(name = "state_registration", nullable = false, length = 14)
    private String stateRegistration;

    @Column(nullable = false, length = 2)
    private String uf;

    @Column(name = "ibge_city_code", nullable = false, length = 7)
    private String ibgeCityCode;

    @Column(nullable = false, length = 60)
    private String city;

    @Column(name = "csc_id", nullable = false, length = 6)
    private String cscId;

    @Column(nullable = false, length = 36)
    private String csc;

    @Column(name = "nfce_series", nullable = false)
    private int nfceSeries;

    @Column(name = "next_nfce_number", nullable = false)
    private long nextNfceNumber;

    @Version
    private long version;

    protected Store() {
    }

    public Store(String name, String cnpj, String stateRegistration, String uf, String ibgeCityCode, String city,
                 String cscId, String csc) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.cnpj = cnpj;
        this.stateRegistration = stateRegistration;
        this.uf = uf;
        this.ibgeCityCode = ibgeCityCode;
        this.city = city;
        this.cscId = cscId;
        this.csc = csc;
        this.nfceSeries = 1;
        this.nextNfceNumber = 1;
    }

    /** Reserva o próximo número. O @Version impede que duas vendas recebam o mesmo número. */
    public long takeNextNfceNumber() {
        return nextNfceNumber++;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCnpj() { return cnpj; }
    public String getStateRegistration() { return stateRegistration; }
    public String getUf() { return uf; }
    public String getIbgeCityCode() { return ibgeCityCode; }
    public String getCity() { return city; }
    public String getCscId() { return cscId; }
    public String getCsc() { return csc; }
    public int getNfceSeries() { return nfceSeries; }
}
