package posfiscalnfce.fiscal;

import posfiscalnfce.sale.Sale;
import posfiscalnfce.tenant.Store;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Gera o XML da NFC-e (modelo 65, leiaute 4.00) com os grupos essenciais para emitente
 * do Simples Nacional em SP. Campos não usados pelo varejo de balcão ficam de fora.
 */
public final class NfceXmlBuilder {

    public static final String NS = "http://www.portalfiscal.inf.br/nfe";
    private static final Map<String, String> UF_CODES = Map.of("SP", "35");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private NfceXmlBuilder() {
    }

    public static String ufCode(String uf) {
        var code = UF_CODES.get(uf);
        if (code == null) {
            throw new IllegalArgumentException("UF não suportada: " + uf);
        }
        return code;
    }

    public static Document build(Store store, Sale sale, AccessKey key, long number, OffsetDateTime issuedAt,
                                 int environment, String contingencyReason) {
        var doc = newDocument();
        var nfe = doc.createElementNS(NS, "NFe");
        doc.appendChild(nfe);
        var inf = child(doc, nfe, "infNFe");
        inf.setAttribute("versao", "4.00");
        inf.setAttribute("Id", "NFe" + key.value());

        var ide = child(doc, inf, "ide");
        text(doc, ide, "cUF", ufCode(store.getUf()));
        text(doc, ide, "cNF", key.value().substring(35, 43));
        text(doc, ide, "natOp", "VENDA");
        text(doc, ide, "mod", "65");
        text(doc, ide, "serie", String.valueOf(store.getNfceSeries()));
        text(doc, ide, "nNF", String.valueOf(number));
        text(doc, ide, "dhEmi", DATE_TIME.format(issuedAt));
        text(doc, ide, "tpNF", "1");
        text(doc, ide, "idDest", "1");
        text(doc, ide, "cMunFG", store.getIbgeCityCode());
        text(doc, ide, "tpImp", "4");
        text(doc, ide, "tpEmis", String.valueOf(key.emissionType().code()));
        text(doc, ide, "cDV", key.value().substring(43));
        text(doc, ide, "tpAmb", String.valueOf(environment));
        text(doc, ide, "finNFe", "1");
        text(doc, ide, "indFinal", "1");
        text(doc, ide, "indPres", "1");
        text(doc, ide, "procEmi", "0");
        text(doc, ide, "verProc", "pos-fiscal-nfce-1.0");
        if (contingencyReason != null) {
            text(doc, ide, "dhCont", DATE_TIME.format(issuedAt));
            text(doc, ide, "xJust", contingencyReason);
        }

        var emit = child(doc, inf, "emit");
        text(doc, emit, "CNPJ", store.getCnpj());
        text(doc, emit, "xNome", store.getName());
        var address = child(doc, emit, "enderEmit");
        text(doc, address, "cMun", store.getIbgeCityCode());
        text(doc, address, "xMun", store.getCity());
        text(doc, address, "UF", store.getUf());
        text(doc, emit, "IE", store.getStateRegistration());
        text(doc, emit, "CRT", "1");

        int itemNumber = 1;
        for (var item : sale.getItems()) {
            var det = child(doc, inf, "det");
            det.setAttribute("nItem", String.valueOf(itemNumber++));
            var prod = child(doc, det, "prod");
            text(doc, prod, "cProd", item.getSku());
            text(doc, prod, "cEAN", "SEM GTIN");
            text(doc, prod, "xProd", environment == 2 ? "NOTA FISCAL EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL" : item.getDescription());
            text(doc, prod, "NCM", item.getNcm());
            text(doc, prod, "CFOP", item.getCfop());
            text(doc, prod, "uCom", item.getUnit());
            text(doc, prod, "qCom", item.getQuantity().setScale(4).toPlainString());
            text(doc, prod, "vUnCom", item.getUnitPrice().setScale(2).toPlainString());
            text(doc, prod, "vProd", item.getTotal().toPlainString());
            text(doc, prod, "cEANTrib", "SEM GTIN");
            text(doc, prod, "uTrib", item.getUnit());
            text(doc, prod, "qTrib", item.getQuantity().setScale(4).toPlainString());
            text(doc, prod, "vUnTrib", item.getUnitPrice().setScale(2).toPlainString());
            text(doc, prod, "indTot", "1");
            var icms = child(doc, child(doc, child(doc, det, "imposto"), "ICMS"), "ICMSSN" + item.getCsosn());
            text(doc, icms, "orig", "0");
            text(doc, icms, "CSOSN", item.getCsosn());
        }

        var tot = child(doc, child(doc, inf, "total"), "ICMSTot");
        text(doc, tot, "vBC", "0.00");
        text(doc, tot, "vICMS", "0.00");
        text(doc, tot, "vProd", sale.getTotal().toPlainString());
        text(doc, tot, "vDesc", "0.00");
        text(doc, tot, "vNF", sale.getTotal().toPlainString());

        text(doc, child(doc, inf, "transp"), "modFrete", "9");

        var pag = child(doc, inf, "pag");
        for (var payment : sale.getPayments()) {
            var det = child(doc, pag, "detPag");
            text(doc, det, "tPag", payment.getMethod().tPag());
            text(doc, det, "vPag", payment.getAmount().toPlainString());
        }
        if (sale.getChange().signum() > 0) {
            text(doc, pag, "vTroco", sale.getChange().toPlainString());
        }
        return doc;
    }

    /** Insere infNFeSupl (QR Code e URL de consulta) logo após infNFe, antes da assinatura. */
    public static void addSupplement(Document doc, String qrCodeUrl, String consultUrl) {
        var inf = doc.getElementsByTagNameNS(NS, "infNFe").item(0);
        var supl = doc.createElementNS(NS, "infNFeSupl");
        text(doc, supl, "qrCode", qrCodeUrl);
        text(doc, supl, "urlChave", consultUrl);
        inf.getParentNode().insertBefore(supl, inf.getNextSibling());
    }

    public static String toString(Document doc) {
        try {
            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            var writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Document newDocument() {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Element child(Document doc, Element parent, String name) {
        var element = doc.createElementNS(NS, name);
        parent.appendChild(element);
        return element;
    }

    private static void text(Document doc, Element parent, String name, String value) {
        child(doc, parent, name).setTextContent(value);
    }
}
