package br.com.controle.gastos.config;

import br.com.controle.gastos.api.Models.*;
import br.com.controle.gastos.domain.ExpenseService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Profile("demo")
public class DemoData implements ApplicationRunner {
    private final ExpenseService service;
    private final JdbcTemplate jdbc;

    public DemoData(ExpenseService service, JdbcTemplate jdbc) { this.service = service; this.jdbc = jdbc; }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM expenses", Long.class) > 0) return;
        Map<String, Long> ids = service.categories().stream().collect(Collectors.toMap(Category::name, Category::id));
        YearMonth now = YearMonth.now();
        service.saveBudget(now.toString(), new BudgetInput(new BigDecimal("4500.00")));
        add(ids, now, 1, "Aluguel do apartamento", "1450.00", "Moradia", PaymentMethod.TRANSFERENCIA);
        add(ids, now, 2, "Compras no supermercado", "286.40", "Alimentação", PaymentMethod.DEBITO);
        add(ids, now, 3, "Café e pão de queijo", "24.90", "Alimentação", PaymentMethod.PIX);
        add(ids, now, 4, "Plano de internet", "99.90", "Moradia", PaymentMethod.BOLETO);
        add(ids, now, 5, "Combustível", "180.00", "Transporte", PaymentMethod.CREDITO);
        add(ids, now, 7, "Farmácia", "67.80", "Saúde", PaymentMethod.PIX);
        add(ids, now, 9, "Cinema no fim de semana", "54.00", "Lazer", PaymentMethod.CREDITO);
        add(ids, now, 10, "Curso de Java", "89.90", "Educação", PaymentMethod.PIX);
        add(ids, now, 12, "Almoço com a família", "142.50", "Alimentação", PaymentMethod.DEBITO);
        add(ids, now, 14, "Transporte por aplicativo", "32.70", "Transporte", PaymentMethod.PIX);
        add(ids, now, 16, "Conta de energia", "138.60", "Moradia", PaymentMethod.BOLETO);
        add(ids, now, 18, "Assinatura de música", "21.90", "Lazer", PaymentMethod.CREDITO);
        for (int i = 1; i <= 5; i++) {
            YearMonth previous = now.minusMonths(i);
            add(ids, previous, 1, "Aluguel", "1450.00", "Moradia", PaymentMethod.TRANSFERENCIA);
            add(ids, previous, 6, "Mercado", String.valueOf(360 + i * 73) + ".40", "Alimentação", PaymentMethod.DEBITO);
            add(ids, previous, 15, "Despesas do mês", String.valueOf(550 + (i % 3) * 170) + ".00", "Outros", PaymentMethod.PIX);
        }
    }

    private void add(Map<String, Long> ids, YearMonth month, int day, String description, String amount, String category, PaymentMethod method) {
        service.createExpense(new ExpenseInput(description, new BigDecimal(amount), month.atDay(day), ids.get(category), method, "Dado fictício de demonstração"));
    }
}
