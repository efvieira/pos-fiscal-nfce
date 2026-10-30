package posfiscalnfce.fiscal;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import posfiscalnfce.sale.Sale;
import posfiscalnfce.shared.ClockConfig;
import posfiscalnfce.tenant.Store;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;

/** DANFE NFC-e simplificado em bobina de 80 mm, com QR Code de consulta. */
@Component
public class DanfeRenderer {

    private static final float WIDTH = 226f; // 80 mm
    private static final Font NORMAL = new Font(Font.HELVETICA, 7);
    private static final Font BOLD = new Font(Font.HELVETICA, 7, Font.BOLD);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ClockConfig.BRT);

    public byte[] render(FiscalDocument document, Store store, Sale sale) {
        var out = new ByteArrayOutputStream();
        var pdf = new Document(new Rectangle(WIDTH, 260 + sale.getItems().size() * 12f + 180), 8, 8, 8, 8);
        PdfWriter.getInstance(pdf, out);
        pdf.open();

        center(pdf, store.getName(), BOLD);
        center(pdf, "CNPJ " + store.getCnpj() + "  IE " + store.getStateRegistration(), NORMAL);
        center(pdf, "Documento Auxiliar da Nota Fiscal de Consumidor Eletrônica", NORMAL);

        var items = new PdfPTable(new float[]{4, 1.2f, 1.6f, 1.8f});
        items.setWidthPercentage(100);
        header(items, "Descrição", "Qtd", "Unit", "Total");
        for (var item : sale.getItems()) {
            cell(items, item.getDescription(), Element.ALIGN_LEFT);
            cell(items, item.getQuantity().stripTrailingZeros().toPlainString(), Element.ALIGN_RIGHT);
            cell(items, item.getUnitPrice().toPlainString(), Element.ALIGN_RIGHT);
            cell(items, item.getTotal().toPlainString(), Element.ALIGN_RIGHT);
        }
        pdf.add(items);

        line(pdf, "Qtd. total de itens", String.valueOf(sale.getItems().size()));
        line(pdf, "Valor a pagar R$", sale.getTotal().toPlainString());
        for (var payment : sale.getPayments()) {
            line(pdf, payment.getMethod().label(), payment.getAmount().toPlainString());
        }
        if (sale.getChange().signum() > 0) {
            line(pdf, "Troco R$", sale.getChange().toPlainString());
        }

        if (document.getEmissionType() == EmissionType.OFFLINE_CONTINGENCY) {
            center(pdf, "EMITIDA EM CONTINGÊNCIA", BOLD);
            center(pdf, "Pendente de autorização", NORMAL);
        }
        center(pdf, "Consulte pela Chave de Acesso em", NORMAL);
        center(pdf, document.getAccessKey().formatted(), BOLD);
        center(pdf, "NFC-e nº %d Série %d %s".formatted(document.getNumber(), document.getSeries(),
                DATE.format(document.getIssuedAt())), NORMAL);
        if (document.getProtocol() != null) {
            center(pdf, "Protocolo de autorização: " + document.getProtocol(), NORMAL);
        }

        Image qr;
        try {
            qr = Image.getInstance(qrCode(document.getQrCodeUrl()), null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        qr.scaleAbsolute(110, 110);
        qr.setAlignment(Element.ALIGN_CENTER);
        pdf.add(qr);
        pdf.close();
        return out.toByteArray();
    }

    private static BufferedImage qrCode(String content) {
        try {
            var matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 300, 300);
            var image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < matrix.getWidth(); x++) {
                for (int y = 0; y < matrix.getHeight(); y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            return image;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar QR Code", e);
        }
    }

    private static void center(Document pdf, String text, Font font) {
        var paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        pdf.add(paragraph);
    }

    private static void line(Document pdf, String label, String value) {
        var table = new PdfPTable(2);
        table.setWidthPercentage(100);
        cell(table, label, Element.ALIGN_LEFT);
        cell(table, value, Element.ALIGN_RIGHT);
        pdf.add(table);
    }

    private static void header(PdfPTable table, String... titles) {
        for (var title : titles) {
            var cell = new PdfPCell(new Phrase(title, BOLD));
            cell.setBorder(Rectangle.BOTTOM);
            table.addCell(cell);
        }
    }

    private static void cell(PdfPTable table, String text, int align) {
        var cell = new PdfPCell(new Phrase(text, NORMAL));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(align);
        table.addCell(cell);
    }
}
