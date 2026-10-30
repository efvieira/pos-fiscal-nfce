package posfiscalnfce;

import posfiscalnfce.fiscal.ContingencyRetryJob;
import posfiscalnfce.fiscal.SimulatedSefazGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PosIntegrationTest {

    static final String STORE = "00000000-0000-0000-0000-000000000001";
    static final String BREAD = "10000000-0000-0000-0000-000000000001";
    static final String COFFEE = "10000000-0000-0000-0000-000000000002";

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired SimulatedSefazGateway sefaz;
    @Autowired ContingencyRetryJob retryJob;

    @AfterEach
    void sefazBackOnline() {
        sefaz.setOnline(true);
    }

    private MockHttpServletRequestBuilder as(String operator, MockHttpServletRequestBuilder request) {
        return request.header("X-Store-Id", STORE).header("X-User", operator).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode call(String operator, MockHttpServletRequestBuilder request, String body) throws Exception {
        var response = mvc.perform(as(operator, request).content(body == null ? "" : body)).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isLessThan(300);
        return json.readTree(response.getContentAsString());
    }

    private String sell(String operator, String body) throws Exception {
        return call(operator, post("/v1/sales"), body).get("id").asString();
    }

    @Test
    void fullCashierShiftWithNfceAuthorizedAndCashClosing() throws Exception {
        call("bia", post("/v1/cash-sessions"), "{\"openingFloat\": 100.00}");
        call("bia", post("/v1/cash-sessions/current/supplies"), "{\"amount\": 50, \"note\": \"troco\"}");

        var sale = call("bia", post("/v1/sales"), """
                {"items": [{"productId": "%s", "quantity": 0.5}, {"productId": "%s", "quantity": 2}],
                 "payments": {"CASH": 50.00}}""".formatted(BREAD, COFFEE));

        assertThat(sale.get("total").decimalValue()).isEqualByComparingTo("22.45");
        assertThat(sale.get("change").decimalValue()).isEqualByComparingTo("27.55");
        assertThat(sale.at("/nfce/status").asString()).isEqualTo("AUTHORIZED");
        assertThat(sale.at("/nfce/accessKey").asString()).hasSize(44);
        assertThat(sale.at("/nfce/qrCodeUrl").asString()).contains("?p=");

        var saleId = sale.get("id").asString();
        mvc.perform(as("bia", get("/v1/sales/{id}/nfce/xml", saleId)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<SignatureValue>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<infNFeSupl>")));
        var pdf = mvc.perform(as("bia", get("/v1/sales/{id}/nfce/danfe", saleId))).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

        call("bia", post("/v1/cash-sessions/current/withdrawals"), "{\"amount\": 100, \"note\": \"cofre\"}");
        var closed = call("bia", post("/v1/cash-sessions/current/close"), "{\"counted\": {\"CASH\": 70.00}}");
        // 100 fundo + 50 suprimento + 22.45 venda - 100 sangria = 72.45 esperado; contado 70 -> -2.45
        assertThat(closed.at("/closing/0/difference").decimalValue()).isEqualByComparingTo("-2.45");

        var today = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        mvc.perform(as("bia", get("/v1/reports/cash-closings").param("from", today.toString()).param("to", today.toString())))
                .andExpect(jsonPath("$[?(@.operator == 'bia')]").exists());
        mvc.perform(as("bia", get("/v1/reports/sales").param("from", today.toString()).param("to", today.toString())))
                .andExpect(jsonPath("$.count").isNumber());
        mvc.perform(as("bia", get("/v1/audit"))).andExpect(jsonPath("$[?(@.action == 'CASH_CLOSED')]").exists());
    }

    @Test
    void saleContinuesInOfflineContingencyAndIsTransmittedLater() throws Exception {
        call("caio", post("/v1/cash-sessions"), "{\"openingFloat\": 0}");
        sefaz.setOnline(false);

        var sale = call("caio", post("/v1/sales"), """
                {"items": [{"productId": "%s", "quantity": 1}], "payments": {"PIX": 6.50}}""".formatted(COFFEE));
        assertThat(sale.at("/nfce/status").asString()).isEqualTo("CONTINGENCY_PENDING");
        assertThat(sale.at("/nfce/emissionType").asString()).isEqualTo("OFFLINE_CONTINGENCY");
        assertThat(sale.at("/nfce/accessKey").asString().charAt(34)).isEqualTo('9');
        mvc.perform(as("caio", get("/v1/reports/fiscal-pending"))).andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        assertThat(retryJob.retryPending()).isZero();
        sefaz.setOnline(true);
        assertThat(retryJob.retryPending()).isPositive();

        var saleId = sale.get("id").asString();
        var after = call("caio", get("/v1/sales/{id}", saleId), null);
        assertThat(after.at("/nfce/status").asString()).isEqualTo("AUTHORIZED");
    }

    @Test
    void cancellationReturnsStockAndRequiresJustification() throws Exception {
        call("duda", post("/v1/cash-sessions"), "{\"openingFloat\": 20}");
        var saleId = sell("duda", """
                {"items": [{"productId": "%s", "quantity": 3}], "payments": {"CREDIT_CARD": 19.50}}""".formatted(COFFEE));

        mvc.perform(as("duda", post("/v1/sales/{id}/cancel", saleId)).content("{\"justification\": \"curta\"}"))
                .andExpect(status().isUnprocessableContent());
        var cancelled = call("duda", post("/v1/sales/{id}/cancel", saleId),
                "{\"justification\": \"Cliente desistiu da compra no balcão\"}");
        assertThat(cancelled.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(cancelled.at("/nfce/status").asString()).isEqualTo("CANCELLED");

        mvc.perform(as("duda", post("/v1/sales/{id}/cancel", saleId)).content("{\"justification\": \"Cliente desistiu da compra no balcão\"}"))
                .andExpect(status().isUnprocessableContent());
        var current = call("duda", get("/v1/cash-sessions/current"), null);
        assertThat(current.at("/expected/CREDIT_CARD").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void enforcesBusinessRules() throws Exception {
        mvc.perform(as("edu", post("/v1/sales")).content("""
                        {"items": [{"productId": "%s", "quantity": 1}], "payments": {"CASH": 10}}""".formatted(COFFEE)))
                .andExpect(status().isUnprocessableContent());

        call("edu", post("/v1/cash-sessions"), "{\"openingFloat\": 10}");
        mvc.perform(as("edu", post("/v1/cash-sessions")).content("{\"openingFloat\": 10}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(as("edu", post("/v1/sales")).content("""
                        {"items": [{"productId": "%s", "quantity": 1}], "payments": {"PIX": 10}}""".formatted(COFFEE)))
                .andExpect(status().isUnprocessableContent()); // troco sobre Pix
        mvc.perform(as("edu", post("/v1/sales")).content("""
                        {"items": [{"productId": "%s", "quantity": 1}], "payments": {"CASH": 1}}""".formatted(COFFEE)))
                .andExpect(status().isUnprocessableContent()); // pagamento insuficiente
        mvc.perform(as("edu", post("/v1/sales")).content("""
                        {"items": [{"productId": "%s", "quantity": 99999}], "payments": {"CASH": 1}}""".formatted(BREAD)))
                .andExpect(status().isUnprocessableContent()); // estoque
    }

    @Test
    void managesProductsWithFiscalData() throws Exception {
        var product = call("gil", post("/v1/products"), """
                {"sku": "AGUA-500", "name": "Água mineral 500 ml", "ncm": "22011000", "cfop": "5102",
                 "csosn": "102", "unit": "UN", "price": 3.50, "stock": 10}""");
        var id = product.get("id").asString();

        mvc.perform(as("gil", post("/v1/products")).content("""
                        {"sku": "AGUA-500", "name": "x", "ncm": "22011000", "cfop": "5102", "csosn": "102",
                         "unit": "UN", "price": 1, "stock": 1}"""))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(as("gil", post("/v1/products")).content("""
                        {"sku": "X", "name": "x", "ncm": "123", "cfop": "6102", "csosn": "102",
                         "unit": "UN", "price": 1, "stock": 1}"""))
                .andExpect(status().isBadRequest());

        call("gil", put("/v1/products/{id}", id), """
                {"sku": "AGUA-500", "name": "Água mineral 500 ml", "ncm": "22011000", "cfop": "5102",
                 "csosn": "102", "unit": "UN", "price": 4.00, "stock": 10}""");
        var adjusted = call("gil", post("/v1/products/{id}/stock-adjustments", id), "{\"quantity\": -4, \"reason\": \"avaria\"}");
        assertThat(adjusted.get("stock").decimalValue()).isEqualByComparingTo("6");
        assertThat(adjusted.get("price").decimalValue()).isEqualByComparingTo("4.00");
        mvc.perform(as("gil", get("/v1/products"))).andExpect(jsonPath("$[?(@.sku == 'AGUA-500')]").exists());
    }

    @Test
    void requiresValidTenant() throws Exception {
        mvc.perform(get("/v1/products")).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/products").header("X-Store-Id", "00000000-0000-0000-0000-00000000ffff"))
                .andExpect(status().isForbidden());
        mvc.perform(as("x", get("/v1/sales/{id}", "00000000-0000-0000-0000-00000000ffff")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v1/admin/stores")).andExpect(jsonPath("$[0].uf").value("SP"));
        mvc.perform(post("/v1/admin/stores").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Loja 2", "cnpj": "11222333000262", "stateRegistration": "123456789",
                         "uf": "SP", "ibgeCityCode": "3509502", "city": "Campinas", "cscId": "1", "csc": "abc"}"""))
                .andExpect(status().isCreated());
        mvc.perform(get("/v1/admin/sandbox/sefaz")).andExpect(jsonPath("$.online").value(true));
        mvc.perform(post("/v1/admin/sandbox/sefaz").param("online", "true")).andExpect(status().isOk());
    }
}
