-- Execute no MySQL Workbench com um usuário administrador.
-- Antes de executar, substitua SUBSTITUA_POR_UMA_SENHA_FORTE.
-- Este script prepara o banco e o usuário. O Flyway cria as tabelas ao iniciar a aplicação.
CREATE DATABASE IF NOT EXISTS controle_gastos
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'controle_app'@'localhost'
    IDENTIFIED BY 'SUBSTITUA_POR_UMA_SENHA_FORTE';

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
    ON controle_gastos.* TO 'controle_app'@'localhost';
