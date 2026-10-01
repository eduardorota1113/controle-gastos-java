package br.com.controle.gastos.api;

import br.com.controle.gastos.api.Models.*;
import br.com.controle.gastos.domain.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ExpenseController {
    private final ExpenseService service;
    private final boolean demo;

    public ExpenseController(ExpenseService service, @Value("${app.demo:false}") boolean demo) {
        this.service = service;
        this.demo = demo;
    }

    @GetMapping("/info")
    public Map<String, Object> info() { return Map.of("demo", demo, "name", "Controle de Gastos"); }

    @GetMapping("/categories")
    public List<Category> categories() { return service.categories(); }

    @PostMapping("/categories")
    public ResponseEntity<Category> createCategory(@Valid @RequestBody CategoryInput input) {
        Category category = service.createCategory(input);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(category.id()).toUri()).body(category);
    }

    @PutMapping("/categories/{id}")
    public Category updateCategory(@PathVariable long id, @Valid @RequestBody CategoryInput input) { return service.updateCategory(id, input); }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable long id) {
        service.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/expenses")
    public ExpensePage expenses(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                @RequestParam(required = false) Long categoryId, @RequestParam(required = false) String search,
                                @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return service.expenses(from, to, categoryId, search, page, size);
    }

    @GetMapping("/expenses/{id}")
    public Expense expense(@PathVariable long id) { return service.expense(id); }

    @PostMapping("/expenses")
    public ResponseEntity<Expense> createExpense(@Valid @RequestBody ExpenseInput input) {
        Expense expense = service.createExpense(input);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(expense.id()).toUri()).body(expense);
    }

    @PutMapping("/expenses/{id}")
    public Expense updateExpense(@PathVariable long id, @Valid @RequestBody ExpenseInput input) { return service.updateExpense(id, input); }

    @DeleteMapping("/expenses/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable long id) {
        service.deleteExpense(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@RequestParam(required = false) String month) {
        return service.dashboard(month == null ? YearMonth.now().toString() : month);
    }

    @PutMapping("/budgets/{month}")
    public Dashboard saveBudget(@PathVariable String month, @Valid @RequestBody BudgetInput input) { return service.saveBudget(month, input); }

    @DeleteMapping("/budgets/{month}")
    public ResponseEntity<Void> deleteBudget(@PathVariable String month) {
        service.deleteBudget(month);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/expenses/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                        @RequestParam(required = false) Long categoryId, @RequestParam(required = false) String search) {
        StringBuilder csv = new StringBuilder("\uFEFFData;Descrição;Categoria;Valor (R$);Pagamento;Observações\r\n");
        for (Expense expense : service.exportExpenses(from, to, categoryId, search)) {
            csv.append(csvCell(expense.date().toString())).append(';')
                    .append(csvCell(expense.description())).append(';')
                    .append(csvCell(expense.categoryName())).append(';')
                    .append(csvCell(expense.amount().toPlainString().replace('.', ','))).append(';')
                    .append(csvCell(expense.paymentMethod().name())).append(';')
                    .append(csvCell(expense.notes())).append("\r\n");
        }
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"gastos-" + LocalDate.now() + ".csv\"")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String csvCell(String value) {
        String stripped = value.stripLeading();
        if (!stripped.isEmpty() && ("=+-@".indexOf(stripped.charAt(0)) >= 0 || value.startsWith("\t") || value.startsWith("\r"))) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
