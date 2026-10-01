CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(60) NOT NULL,
    normalized_name VARCHAR(60) NOT NULL UNIQUE,
    color CHAR(7) NOT NULL
);

CREATE TABLE expenses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    description VARCHAR(120) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    expense_date DATE NOT NULL,
    category_id BIGINT NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    notes VARCHAR(500) NOT NULL DEFAULT '',
    CONSTRAINT fk_expense_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT ck_expense_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_method CHECK (payment_method IN ('PIX', 'DEBITO', 'CREDITO', 'DINHEIRO', 'BOLETO', 'TRANSFERENCIA'))
);

CREATE INDEX idx_expenses_date ON expenses(expense_date);
CREATE INDEX idx_expenses_category_date ON expenses(category_id, expense_date);

CREATE TABLE monthly_budgets (
    month_key CHAR(7) PRIMARY KEY,
    amount DECIMAL(12, 2) NOT NULL,
    CONSTRAINT ck_budget_amount CHECK (amount > 0)
);

INSERT INTO categories (name, normalized_name, color) VALUES
    ('Alimentação', 'alimentacao', '#f59e0b'),
    ('Moradia', 'moradia', '#6366f1'),
    ('Transporte', 'transporte', '#06b6d4'),
    ('Saúde', 'saude', '#f43f5e'),
    ('Lazer', 'lazer', '#8b5cf6'),
    ('Educação', 'educacao', '#14b8a6'),
    ('Outros', 'outros', '#64748b');
