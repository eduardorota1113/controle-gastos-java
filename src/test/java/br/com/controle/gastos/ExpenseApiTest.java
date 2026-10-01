package br.com.controle.gastos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExpenseApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private long categoryId(String name) {
        return jdbc.queryForObject("SELECT id FROM categories WHERE name = ?", Long.class, name);
    }

    private String expenseBody(String description, String amount, String date, long category) throws Exception {
        return json.writeValueAsString(Map.of("description", description, "amount", amount, "date", date,
                "categoryId", category, "paymentMethod", "PIX", "notes", ""));
    }

    private JsonNode create(String description, String amount, String date, long category) throws Exception {
        MvcResult result = mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(expenseBody(description, amount, date, category)))
                .andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn();
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void createUpdateReadAndDeleteExpense() throws Exception {
        long food = categoryId("Alimentação");
        long id = create("  Mercado  ", "12.35", "2030-02-10", food).get("id").asLong();
        mvc.perform(get("/api/expenses/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Mercado"))
                .andExpect(jsonPath("$.amount").value(12.35))
                .andExpect(jsonPath("$.categoryName").value("Alimentação"));
        mvc.perform(put("/api/expenses/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(expenseBody("Mercado atualizado", "25.99", "2030-02-11", food)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(25.99));
        assertThat(jdbc.queryForObject("SELECT amount FROM expenses WHERE id = ?", BigDecimal.class, id)).isEqualByComparingTo("25.99");
        mvc.perform(delete("/api/expenses/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/expenses/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void rejectInvalidValuesWithoutWriting() throws Exception {
        long food = categoryId("Alimentação");
        for (String amount : new String[]{"0", "-10", "1.001", "10000000000.00"}) {
            mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON)
                    .content(expenseBody("Mercado", amount, "2030-02-10", food)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.amount").exists());
        }
        mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(expenseBody(" ", "10", "2030-02-10", food))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(expenseBody("Mercado", "10", "1800-02-10", food))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expenses", Long.class)).isZero();
    }

    @Test
    void rejectUnknownCategoriesAndMalformedJson() throws Exception {
        mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON)
                .content(expenseBody("Mercado", "10", "2030-02-10", 999999)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());
        String body = expenseBody("Mercado", "10", "2030-02-10", categoryId("Alimentação"));
        mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(body.replace("PIX", "INVALIDO")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content(body.substring(0, body.length() - 1) + ",\"extra\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filterWithInclusiveDatesCategoryAndPaginate() throws Exception {
        long food = categoryId("Alimentação");
        create("Mercado A", "10.10", "2030-01-01", food);
        create("Mercado B", "20.20", "2030-01-31", food);
        create("Cinema", "40", "2030-01-15", categoryId("Lazer"));
        create("Fora do mês", "90", "2030-02-01", food);
        mvc.perform(get("/api/expenses").param("from", "2030-01-01").param("to", "2030-01-31")
                .param("categoryId", String.valueOf(food)).param("size", "1").param("search", "mercado"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalAmount").value(30.30)).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].description").value("Mercado B"));
        mvc.perform(get("/api/expenses").param("from", "2030-01-01").param("to", "2030-01-31")
                .param("categoryId", String.valueOf(food)).param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.items[0].description").value("Mercado A"));
    }

    @Test
    void searchTreatsSqlWildcardsAsLiteralCharacters() throws Exception {
        long food = categoryId("Alimentação");
        create("Desconto 10%", "5", "2030-01-10", food);
        create("Produto comum", "6", "2030-01-10", food);
        mvc.perform(get("/api/expenses").param("from", "2030-01-01").param("to", "2030-01-31").param("search", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].description").value("Desconto 10%"));
    }

    @Test
    void dashboardKeepsExactCentsAndSeparatesMonths() throws Exception {
        long food = categoryId("Alimentação");
        create("A", "0.10", "2030-02-01", food);
        create("B", "0.20", "2030-02-28", food);
        create("Anterior", "0.50", "2030-01-31", food);
        mvc.perform(get("/api/dashboard").param("month", "2030-02"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0.30))
                .andExpect(jsonPath("$.previousTotal").value(0.50)).andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.byCategory[0].total").value(0.30))
                .andExpect(jsonPath("$.byDay", hasSize(28))).andExpect(jsonPath("$.history", hasSize(6)))
                .andExpect(jsonPath("$.history[4].total").value(0.50))
                .andExpect(jsonPath("$.history[5].total").value(0.30));
    }

    @Test
    void budgetCanBeUpdatedRemovedAndExceeded() throws Exception {
        create("Conta", "30.10", "2030-03-10", categoryId("Moradia"));
        mvc.perform(put("/api/budgets/2030-03").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"100.00\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.remaining").value(69.90));
        mvc.perform(put("/api/budgets/2030-03").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":\"20.00\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.remaining").value(-10.10));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monthly_budgets", Long.class)).isEqualTo(1);
        mvc.perform(delete("/api/budgets/2030-03")).andExpect(status().isNoContent());
        mvc.perform(get("/api/dashboard").param("month", "2030-03")).andExpect(jsonPath("$.budget").doesNotExist());
    }

    @Test
    void categoriesPreventDuplicatesAndPreserveLinkedExpenses() throws Exception {
        MvcResult created = mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  Viagens  \",\"color\":\"#14B8A6\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Viagens")).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"víagens\",\"color\":\"#14b8a6\"}")).andExpect(status().isConflict());
        long expenseId = create("Passagem", "80", "2030-02-10", id).get("id").asLong();
        mvc.perform(put("/api/categories/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Férias\",\"color\":\"#112233\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/expenses/{id}", expenseId)).andExpect(jsonPath("$.categoryName").value("Férias"));
        mvc.perform(delete("/api/categories/{id}", id)).andExpect(status().isConflict());
        mvc.perform(put("/api/expenses/{id}", expenseId).contentType(MediaType.APPLICATION_JSON)
                .content(expenseBody("Passagem", "80", "2030-02-10", categoryId("Transporte"))))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/categories/{id}", id)).andExpect(status().isNoContent());
    }

    @Test
    void rejectInvalidCategoryColorsAndNames() throws Exception {
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Teste\",\"color\":\"red;script\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.color").exists());
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" \",\"color\":\"#112233\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void csvUsesUtf8EscapesQuotesAndNeutralizesSpreadsheetFormulas() throws Exception {
        create("=SUM(1;2) \"café\"", "12.35", "2030-02-10", categoryId("Alimentação"));
        MvcResult result = mvc.perform(get("/api/expenses/export").param("from", "2030-02-01").param("to", "2030-02-28"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();
        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFData;Descrição;").contains("\"'=SUM(1;2) \"\"café\"\"\"").contains("\"12,35\"");
    }

    @Test
    void crossOriginWritesAreRejectedAndSameOriginWorks() throws Exception {
        String body = expenseBody("Mercado", "10", "2030-02-10", categoryId("Alimentação"));
        mvc.perform(post("/api/expenses").header("Origin", "https://example.com").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/expenses").header("Sec-Fetch-Site", "cross-site").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/expenses").header("Origin", "http://localhost").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void invalidDatesAndPaginationReturnHelpfulErrors() throws Exception {
        mvc.perform(get("/api/expenses").param("from", "2030-03-01").param("to", "2030-02-01")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/expenses").param("from", "2030-03-01")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/expenses").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/expenses").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/expenses").param("from", "not-a-date")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard").param("month", "2030-13")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard").param("month", "1800-01")).andExpect(status().isBadRequest());
    }

    @Test
    void missingExpensesNeverCreateRecordsOnUpdateOrDelete() throws Exception {
        mvc.perform(put("/api/expenses/999999").contentType(MediaType.APPLICATION_JSON)
                .content(expenseBody("Teste", "10", "2030-02-10", categoryId("Alimentação")))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/expenses/999999")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expenses", Long.class)).isZero();
    }

    @Test
    void emptyDashboardIncludesLeapYearDaysAndZeroHistory() throws Exception {
        mvc.perform(get("/api/dashboard").param("month", "2032-02"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.count").value(0)).andExpect(jsonPath("$.byDay", hasSize(29)))
                .andExpect(jsonPath("$.byCategory", hasSize(0))).andExpect(jsonPath("$.history", hasSize(6)));
        mvc.perform(get("/api/info")).andExpect(jsonPath("$.demo").value(false));
    }

    @Test
    void frameworkErrorsKeepCorrectHttpStatusAndJsonMessages() throws Exception {
        mvc.perform(get("/api/missing-resource")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Recurso não encontrado."));
        mvc.perform(patch("/api/expenses/1")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/expenses").contentType(MediaType.TEXT_PLAIN).content("test"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
