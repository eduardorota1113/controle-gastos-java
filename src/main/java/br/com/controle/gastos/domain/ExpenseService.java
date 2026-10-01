package br.com.controle.gastos.domain;

import br.com.controle.gastos.api.ApiException;
import br.com.controle.gastos.api.Models.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Statement;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ExpenseService {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private static final String SELECT_EXPENSE = """
            SELECT e.id, e.description, e.amount, e.expense_date, e.category_id,
                   c.name AS category_name, c.color AS category_color, e.payment_method, e.notes
            FROM expenses e JOIN categories c ON c.id = e.category_id
            """;
    private static final RowMapper<Expense> EXPENSE_MAPPER = (rs, row) -> new Expense(
            rs.getLong("id"), rs.getString("description"), rs.getBigDecimal("amount"),
            rs.getDate("expense_date").toLocalDate(), rs.getLong("category_id"),
            rs.getString("category_name"), rs.getString("category_color"),
            PaymentMethod.valueOf(rs.getString("payment_method")), rs.getString("notes"));

    public ExpenseService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    public List<Category> categories() {
        return jdbc.query("""
                SELECT c.id, c.name, c.color, COUNT(e.id) AS expense_count
                FROM categories c LEFT JOIN expenses e ON e.category_id = c.id
                GROUP BY c.id, c.name, c.color ORDER BY c.name
                """, (rs, row) -> new Category(rs.getLong("id"), rs.getString("name"),
                rs.getString("color"), rs.getLong("expense_count")));
    }

    public Category category(long id) {
        return categories().stream().filter(c -> c.id() == id).findFirst()
                .orElseThrow(() -> ApiException.notFound("Categoria não encontrada."));
    }

    @Transactional
    public Category createCategory(CategoryInput input) {
        String name = cleanName(input.name());
        String key = normalize(name);
        ensureUniqueCategory(key, null);
        long id = insert("INSERT INTO categories (name, normalized_name, color) VALUES (?, ?, ?)",
                name, key, input.color().toLowerCase(Locale.ROOT));
        return category(id);
    }

    @Transactional
    public Category updateCategory(long id, CategoryInput input) {
        category(id);
        String name = cleanName(input.name());
        String key = normalize(name);
        ensureUniqueCategory(key, id);
        jdbc.update("UPDATE categories SET name = ?, normalized_name = ?, color = ? WHERE id = ?",
                name, key, input.color().toLowerCase(Locale.ROOT), id);
        return category(id);
    }

    @Transactional
    public void deleteCategory(long id) {
        Category category = category(id);
        if (category.expenseCount() > 0) {
            throw ApiException.conflict("Esta categoria possui gastos. Mova ou exclua os gastos antes de removê-la.");
        }
        jdbc.update("DELETE FROM categories WHERE id = ?", id);
    }

    private void ensureUniqueCategory(String key, Long exceptId) {
        Long count = exceptId == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE normalized_name = ?", Long.class, key)
                : jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE normalized_name = ? AND id <> ?", Long.class, key, exceptId);
        if (count != null && count > 0) throw ApiException.conflict("Já existe uma categoria com esse nome.");
    }

    private String cleanName(String value) {
        String clean = value.strip().replaceAll("\\s+", " ");
        if (clean.isBlank()) throw ApiException.badRequest("Informe o nome da categoria.");
        return clean;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    public Expense expense(long id) {
        return jdbc.query(SELECT_EXPENSE + " WHERE e.id = ?", EXPENSE_MAPPER, id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Gasto não encontrado."));
    }

    @Transactional
    public Expense createExpense(ExpenseInput input) {
        validateExpense(input);
        long id = insert("""
                INSERT INTO expenses (description, amount, expense_date, category_id, payment_method, notes)
                VALUES (?, ?, ?, ?, ?, ?)
                """, input.description().strip(), input.amount(), Date.valueOf(input.date()),
                input.categoryId(), input.paymentMethod().name(), notes(input));
        return expense(id);
    }

    @Transactional
    public Expense updateExpense(long id, ExpenseInput input) {
        expense(id);
        validateExpense(input);
        jdbc.update("""
                UPDATE expenses SET description = ?, amount = ?, expense_date = ?, category_id = ?,
                                    payment_method = ?, notes = ? WHERE id = ?
                """, input.description().strip(), input.amount(), Date.valueOf(input.date()),
                input.categoryId(), input.paymentMethod().name(), notes(input), id);
        return expense(id);
    }

    @Transactional
    public void deleteExpense(long id) {
        if (jdbc.update("DELETE FROM expenses WHERE id = ?", id) == 0)
            throw ApiException.notFound("Gasto não encontrado.");
    }

    private void validateExpense(ExpenseInput input) {
        validateDate(input.date());
        category(input.categoryId());
    }

    private String notes(ExpenseInput input) { return input.notes() == null ? "" : input.notes().strip(); }

    private long insert(String sql, Object... values) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private record Filter(String where, MapSqlParameterSource params) {}

    private Filter filter(LocalDate from, LocalDate to, Long categoryId, String search) {
        if (from == null && to == null) {
            YearMonth month = YearMonth.now();
            from = month.atDay(1);
            to = month.atEndOfMonth();
        } else if (from == null || to == null) {
            throw ApiException.badRequest("Informe a data inicial e a data final.");
        }
        validateDate(from);
        validateDate(to);
        if (from.isAfter(to)) throw ApiException.badRequest("A data inicial deve ser anterior ou igual à data final.");
        String where = " WHERE e.expense_date BETWEEN :from AND :to";
        MapSqlParameterSource params = new MapSqlParameterSource("from", Date.valueOf(from)).addValue("to", Date.valueOf(to));
        if (categoryId != null) {
            if (categoryId < 1) throw ApiException.badRequest("Categoria inválida.");
            where += " AND e.category_id = :category";
            params.addValue("category", categoryId);
        }
        if (search != null && !search.isBlank()) {
            if (search.length() > 120) throw ApiException.badRequest("A busca deve ter até 120 caracteres.");
            where += " AND LOWER(e.description) LIKE :search ESCAPE '!'";
            String escaped = search.strip().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
            params.addValue("search", "%" + escaped + "%");
        }
        return new Filter(where, params);
    }

    public ExpensePage expenses(LocalDate from, LocalDate to, Long categoryId, String search, int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100)
            throw ApiException.badRequest("Paginação inválida. Use página a partir de zero e tamanho entre 1 e 100.");
        Filter filter = filter(from, to, categoryId, search);
        Long count = named.queryForObject("SELECT COUNT(*) FROM expenses e" + filter.where(), filter.params(), Long.class);
        BigDecimal total = named.queryForObject("SELECT COALESCE(SUM(e.amount), 0) FROM expenses e" + filter.where(), filter.params(), BigDecimal.class);
        filter.params().addValue("size", size).addValue("offset", page * size);
        List<Expense> items = named.query(SELECT_EXPENSE + filter.where() + " ORDER BY e.expense_date DESC, e.id DESC LIMIT :size OFFSET :offset", filter.params(), EXPENSE_MAPPER);
        return new ExpensePage(items, count == null ? 0 : count, page, size, total);
    }

    public List<Expense> exportExpenses(LocalDate from, LocalDate to, Long categoryId, String search) {
        Filter filter = filter(from, to, categoryId, search);
        Long count = named.queryForObject("SELECT COUNT(*) FROM expenses e" + filter.where(), filter.params(), Long.class);
        if (count != null && count > 10000) throw ApiException.badRequest("Exporte um período menor, com até 10.000 gastos.");
        return named.query(SELECT_EXPENSE + filter.where() + " ORDER BY e.expense_date DESC, e.id DESC", filter.params(), EXPENSE_MAPPER);
    }

    public YearMonth month(String value) {
        try {
            if (value == null || !value.matches("\\d{4}-\\d{2}")) throw new DateTimeParseException("invalid", "", 0);
            YearMonth month = YearMonth.parse(value);
            if (month.getYear() < 1900 || month.getYear() > 9998) throw new DateTimeParseException("invalid", value, 0);
            return month;
        } catch (DateTimeParseException error) {
            throw ApiException.badRequest("Informe um mês válido no formato AAAA-MM, entre 1900 e 9998.");
        }
    }

    private void validateDate(LocalDate date) {
        if (date.getYear() < 1900 || date.getYear() > 9998)
            throw ApiException.badRequest("Informe uma data entre 1900 e 9998.");
    }

    public Dashboard dashboard(String value) {
        YearMonth month = month(value);
        Date from = Date.valueOf(month.atDay(1));
        Date to = Date.valueOf(month.atEndOfMonth());
        BigDecimal total = totalForMonth(month);
        BigDecimal previous = totalForMonth(month.minusMonths(1));
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM expenses WHERE expense_date BETWEEN ? AND ?", Long.class, from, to);
        BigDecimal budget = budget(month.toString());
        List<CategoryTotal> byCategory = jdbc.query("""
                SELECT c.id, c.name, c.color, SUM(e.amount) AS total
                FROM expenses e JOIN categories c ON c.id = e.category_id
                WHERE e.expense_date BETWEEN ? AND ? GROUP BY c.id, c.name, c.color
                ORDER BY total DESC, c.name
                """, (rs, row) -> new CategoryTotal(rs.getLong("id"), rs.getString("name"), rs.getString("color"), rs.getBigDecimal("total")), from, to);
        List<DailyTotal> daily = jdbc.query("""
                SELECT expense_date, SUM(amount) AS total FROM expenses
                WHERE expense_date BETWEEN ? AND ? GROUP BY expense_date ORDER BY expense_date
                """, (rs, row) -> new DailyTotal(rs.getDate("expense_date").toLocalDate(), rs.getBigDecimal("total")),
                Date.valueOf(month.minusMonths(5).atDay(1)), to);
        Map<LocalDate, BigDecimal> byDate = new HashMap<>();
        Map<YearMonth, BigDecimal> byMonth = new HashMap<>();
        daily.forEach(day -> {
            byDate.put(day.date(), day.total());
            byMonth.merge(YearMonth.from(day.date()), day.total(), BigDecimal::add);
        });
        List<DailyTotal> byDay = new ArrayList<>();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            byDay.add(new DailyTotal(date, byDate.getOrDefault(date, BigDecimal.ZERO)));
        }
        List<MonthTotal> history = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth item = month.minusMonths(i);
            history.add(new MonthTotal(item.toString(), byMonth.getOrDefault(item, BigDecimal.ZERO)));
        }
        return new Dashboard(month.toString(), total, count == null ? 0 : count, previous, budget,
                budget == null ? null : budget.subtract(total), byCategory, byDay, history);
    }

    private BigDecimal totalForMonth(YearMonth month) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE expense_date BETWEEN ? AND ?",
                BigDecimal.class, Date.valueOf(month.atDay(1)), Date.valueOf(month.atEndOfMonth()));
    }

    private BigDecimal budget(String month) {
        return jdbc.query("SELECT amount FROM monthly_budgets WHERE month_key = ?",
                (rs, row) -> rs.getBigDecimal("amount"), month).stream().findFirst().orElse(null);
    }

    @Transactional
    public Dashboard saveBudget(String value, BudgetInput input) {
        String key = month(value).toString();
        if (jdbc.update("UPDATE monthly_budgets SET amount = ? WHERE month_key = ?", input.amount(), key) == 0)
            jdbc.update("INSERT INTO monthly_budgets (month_key, amount) VALUES (?, ?)", key, input.amount());
        return dashboard(key);
    }

    @Transactional
    public void deleteBudget(String value) {
        jdbc.update("DELETE FROM monthly_budgets WHERE month_key = ?", month(value).toString());
    }
}
