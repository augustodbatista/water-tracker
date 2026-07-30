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

## Atalho

**Windows** — atalho no Desktop apontando para o `javaw.exe` do JDK 21:

```bash
$s = (New-Object -ComObject WScript.Shell).CreateShortcut("$([Environment]::GetFolderPath('Desktop'))\Water Tracker.lnk"); $s.TargetPath = "$env:JAVA_HOME\bin\javaw.exe"; $s.Arguments = '-jar "C:\water-count\target\water-tracker-1.0.0-SNAPSHOT.jar"'; $s.Save()
```

**Pop!_OS** — criar `~/.local/share/applications/water-tracker.desktop`:

```ini
[Desktop Entry]
Type=Application
Name=Water Tracker
Exec=java -jar /caminho/para/water-tracker-1.0.0-SNAPSHOT.jar
Terminal=false
Categories=Utility;
```

O ícone da janela e da barra de tarefas é desenhado pelo próprio app, então não depende de arquivo
externo em nenhum dos dois sistemas.

## Desenvolvimento

Convenções, decisões de arquitetura e armadilhas conhecidas estão em [CLAUDE.md](CLAUDE.md).
Leia antes de contribuir.

## Licença

[MIT](LICENSE) — use, copie e modifique à vontade.
