# Water Tracker Desktop

Widget minimalista para registrar o consumo diário de água com um clique. Java 21 + Swing,
armazenamento local, sem rede e sem conta.

## Requisitos

- JDK 21 ou superior
- Maven 3.9+

## Build

```bash
mvn clean verify
```

Esse comando roda, nesta ordem: Checkstyle, compilação, testes JUnit 5, gate de cobertura JaCoCo
(≥ 85% no código não-UI) e análise estática de segurança SpotBugs + FindSecBugs. Se ele passa,
o commit é entregável.

## Desenvolvimento

Convenções, decisões de arquitetura e armadilhas conhecidas estão em [CLAUDE.md](CLAUDE.md).
Leia antes de contribuir.

## Licença

[MIT](LICENSE) — use, copie e modifique à vontade.
