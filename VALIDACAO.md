# Verificações

## Interface da versão 1.1.0

Revisada em Chrome, em 1º de outubro de 2026, usando o backend de demonstração existente. Os registros anteriores foram preservados; os testes criaram e removeram apenas seus próprios dados temporários.

- Layout sem transbordamento horizontal da página em 1440, 1024, 768, 390 e 320 pixels. A tabela tem sua própria rolagem quando necessário.
- Capturas de desktop e celular inspecionadas visualmente: `docs/editorial-desktop.png` e `docs/editorial-mobile.png`.
- Navegação, cadastro de categoria com cor personalizada, cadastro e edição de gasto, busca, download CSV, cancelamento e confirmação de exclusão verificados.
- Texto contendo marcação HTML exibido como texto, sem criar elementos na página.
- Abertura e cancelamento dos formulários de orçamento e gastos, incluindo o formulário de gastos em 320 pixels.
- Transição com curva de mola confirmada; movimento reduzido respeitado por `prefers-reduced-motion`.
- Nenhum erro de JavaScript ou erro no console durante os fluxos.
- Compilação da versão 1.1.0 e geração do executável concluídas com `mvn verify`: 15 testes de integração em H2, zero falhas e zero erros.

## Base da versão 1.0.0

Executadas em 1º de outubro de 2026, no Windows 11 com Java 21.0.12 e Maven 3.9.11.

| Verificação | Resultado |
|---|---|
| Compilação e geração do JAR | Sucesso |
| Maven Wrapper | Executado e confirmado em 3.9.11 |
| Testes de integração em H2 2.3 | 15 testes, zero falhas, zero erros |
| Testes de integração em MySQL 8.0.46 isolado | 15 testes, zero falhas, zero erros |
| Migração inicial Flyway em H2 e MySQL | Aplicada com sucesso |
| Interface em Chrome com backend de demonstração | Fluxos verificados |
| Layouts de desktop e celular | Verificados em 1440, 390 e 320 pixels |

Os testes de API verificam cadastro, edição, leitura e exclusão; precisão de centavos; entradas inválidas; categorias duplicadas e em uso; filtros com datas inclusivas; paginação; busca literal com caracteres de SQL; resumo por mês e categoria; ano bissexto; orçamento excedido; CSV com acentos e fórmulas neutralizadas; gravações entre origens; e respostas HTTP 400, 404, 405, 409 e 415.

No navegador, foram verificados navegação, paginação, criação e remoção de categoria, cadastro e edição de gasto, escape de HTML em texto, download CSV, cancelamento e confirmação de exclusão, atualização de orçamento, seleção de mês vazio e formulário no celular. A revisão concluiu sem erros de JavaScript.

O MySQL de testes usou uma pasta e uma porta próprias, sem alterar os bancos existentes no computador. As imagens em `docs/` mostram exclusivamente dados fictícios do modo demonstração.

Docker Compose foi fornecido como alternativa de instalação, mas não foi executado neste computador, que não dispõe de Docker. O workflow de GitHub Actions testa H2 e MySQL 8.4 quando o código é enviado ao GitHub; seu resultado é acompanhado na aba **Actions** do repositório.
