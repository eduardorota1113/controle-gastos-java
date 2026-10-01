package br.com.controle.gastos.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class Models {
    private Models() {}

    public enum PaymentMethod { PIX, DEBITO, CREDITO, DINHEIRO, BOLETO, TRANSFERENCIA }

    public record Category(Long id, String name, String color, long expenseCount) {}

    public record CategoryInput(
            @NotBlank(message = "Informe o nome da categoria.")
            @Size(max = 60, message = "O nome deve ter até 60 caracteres.") String name,
            @NotNull(message = "Escolha uma cor.")
            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Informe uma cor hexadecimal válida.") String color) {}

    public record Expense(Long id, String description, BigDecimal amount, LocalDate date,
                          Long categoryId, String categoryName, String categoryColor,
                          PaymentMethod paymentMethod, String notes) {}

    public record ExpenseInput(
            @NotBlank(message = "Informe a descrição.")
            @Size(max = 120, message = "A descrição deve ter até 120 caracteres.") String description,
            @NotNull(message = "Informe o valor.")
            @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero.")
            @DecimalMax(value = "9999999999.99", message = "O valor excede o limite permitido.")
            @Digits(integer = 10, fraction = 2, message = "Use no máximo duas casas decimais.") BigDecimal amount,
            @NotNull(message = "Informe a data.") LocalDate date,
            @NotNull(message = "Escolha uma categoria.")
            @Positive(message = "Escolha uma categoria válida.") Long categoryId,
            @NotNull(message = "Escolha a forma de pagamento.") PaymentMethod paymentMethod,
            @Size(max = 500, message = "As observações devem ter até 500 caracteres.") String notes) {}

    public record BudgetInput(
            @NotNull(message = "Informe o orçamento.")
            @DecimalMin(value = "0.01", message = "O orçamento deve ser maior que zero.")
            @DecimalMax(value = "9999999999.99", message = "O orçamento excede o limite permitido.")
            @Digits(integer = 10, fraction = 2, message = "Use no máximo duas casas decimais.") BigDecimal amount) {}

    public record ExpensePage(List<Expense> items, long totalElements, int page, int size, BigDecimal totalAmount) {}
    public record CategoryTotal(Long categoryId, String name, String color, BigDecimal total) {}
    public record DailyTotal(LocalDate date, BigDecimal total) {}
    public record MonthTotal(String month, BigDecimal total) {}
    public record Dashboard(String month, BigDecimal total, long count, BigDecimal previousTotal,
                            BigDecimal budget, BigDecimal remaining, List<CategoryTotal> byCategory,
                            List<DailyTotal> byDay, List<MonthTotal> history) {}
}
