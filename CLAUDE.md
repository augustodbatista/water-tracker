# CLAUDE.md — Water Tracker Desktop

> Documento vivo. Leia no início de cada sessão. Atualize **na hora** que descobrir algo
> não óbvio — especialmente em *Common Hurdles*, antes de seguir para o próximo passo.

---

## 1. Visão geral e objetivo

Widget desktop minimalista para registrar consumo diário de água com **um clique**, mantendo
metas de hidratação alinhadas a rotinas de treino e saúde.

Princípios que governam decisões de produto:

- **Não intrusivo.** Fica aberto o dia todo. Se atrapalhar, falhou.
- **Registro em 1 clique.** Qualquer fluxo com mais de um clique para o caso comum está errado.
- **Multiplataforma de verdade.** Windows e Pop!_OS são cidadãos de primeira classe. Qualquer
  coisa que só funcione em um dos dois é bug, não "limitação conhecida".
- **POO explícita e documentada.** É um objetivo do projeto, não um detalhe de implementação.

## 2. Stack tecnológico

| Camada | Escolha | Versão | Por quê |
|---|---|---|---|
| Linguagem | Java | **JDK 21** (`release=21`) | É o `openjdk-21-jdk` padrão do Pop!_OS. **Compilar e rodar o build com JDK 21, não com o Zulu 25** — ver Hurdle #12 |
| UI | **Swing** | JDK | Já vem no JDK: zero dependência por plataforma. JavaFX exigiria artefatos win/linux separados |
| Persistência | **JSON** (Gson) | 2.14.0 | ~10 registros/dia. SQLite traria binário nativo por plataforma para guardar `data + ml`. Única dependência de produção, confinada a `storage/` |
| Build | Maven | 3.9.16 | — |
| Testes | JUnit Jupiter | 6.1.2 | via `junit-bom` |
| Cobertura | JaCoCo | 0.8.15 | — |
| Lint | Checkstyle | plugin 3.6.0 / tool 13.9.0 | `google_checks.xml` embutido |
| SAST | SpotBugs + FindSecBugs | 4.10.3.0 / 1.14.0 | Roda offline, em segundos |
| Auditoria de deps | Dependabot | — | Fora do build (ver Hurdle #9) |
| CI | GitHub Actions | matriz `ubuntu-latest` + `windows-latest` | Prova o cross-platform a cada push |

## 3. Configuração e variáveis de ambiente

| Item | Valor | Nota |
|---|---|---|
| `WATER_TRACKER_HOME` | *(opcional)* | Override do diretório de dados. **Validar contra path traversal antes de usar** (ciclo 2) |
| Diretório de dados | `System.getProperty("user.home") + /.water-tracker/` | Funciona nos dois SOs |
| Arquivo de registros | `entries.json`, UTF-8 | Escrita atômica (write-temp + `ATOMIC_MOVE`) |
| Arquivo da meta | `daily-goal.txt` | Só o número, sem JSON: é um inteiro. Faixa aceita: 500 a 10000 ml |
| Meta diária padrão | 2000 ml | Usada quando o arquivo não existe ou tem valor inutilizável |
| Volume máximo por registro | 5000 ml | Barreira de sanidade contra erro de digitação |

**Não há segredos, credenciais, rede ou servidor neste projeto.** Se isso mudar, esta seção muda
**antes** do código.

### Rodando localmente

Nem Maven nem o JDK 21 estão no `PATH` (ver Hurdles #8 e #12). Ambos foram instalados como zip
portável, com checksum conferido, em `%USERPROFILE%\tools\` — sem admin, e some apagando a pasta.

**Windows** — para tornar permanente (uma vez só). **Não use `setx` para o `PATH`** — ver Hurdle #14:

```bash
$jdk = "$env:USERPROFILE\tools\jdk-21.0.11+10"; $p = [Environment]::GetEnvironmentVariable('Path','User'); foreach ($n in @("$jdk\bin", "$env:USERPROFILE\tools\apache-maven-3.9.16\bin")) { if ($p -notlike "*$n*") { $p = $p.TrimEnd(';') + ';' + $n } }; [Environment]::SetEnvironmentVariable('Path', $p, 'User'); [Environment]::SetEnvironmentVariable('JAVA_HOME', $jdk, 'User')
```

**Pop!_OS:**

```bash
sudo apt install maven openjdk-21-jdk
```

Comando único que a CI e você rodam — se ele passa, o commit é entregável:

```bash
mvn clean verify
```

Para abrir o widget (o `shade` gera um jar único, com o Gson embutido):

```bash
java -jar target/water-tracker-1.0.0-SNAPSHOT.jar
```

Antes de confiar no resultado, confira no log **as quatro linhas** que provam que os gates rodaram
de fato, e não por vacuidade (Hurdle #10):

```
You have 0 Checkstyle violations.
Tests run: N, Failures: 0, Errors: 0, Skipped: 0
All coverage checks have been met.
BugInstance size is 0
```

## 4. Estrutura de diretórios

```
water-count/
├── .github/workflows/ci.yml       matriz Windows + Linux
├── .github/dependabot.yml         auditoria de dependências semanal
├── src/main/java/io/github/augustodbatista/watertracker/
│   ├── domain/                    regras de negócio puras — SEM I/O, SEM UI
│   ├── storage/                   persistência (ciclo 2)
│   ├── ui/                        Swing (ciclo 3) — excluída do gate de cobertura
│   └── App.java                   ponto de entrada (ciclo 3)
├── src/test/java/...              espelha a estrutura de main
├── CLAUDE.md
└── pom.xml
```

**Regra de dependência: `ui → domain ← storage`.** O pacote `domain/` não importa nada de
`storage/`, de `ui/`, nem de biblioteca externa. Se um `import` novo aparecer em `domain/`, pare
e questione.

## 5. Principais models e serviços

| Tipo | Papel | Status |
|---|---|---|
| `WaterEntry` | *record* imutável (`LocalDate date`, `int milliliters`). Valida no construtor compacto | ciclo 1 |
| `InvalidVolumeException` | Exceção de domínio nomeada — comunica *qual* regra foi violada | ciclo 1 |
| `DailyIntake` | Agrega entradas de um dia: total e progresso vs. meta | ciclo 1 |
| `JsonIntakeRepository` | Classe **concreta**, sem interface. O `@TempDir` do JUnit testa contra arquivo de verdade, então `InMemoryIntakeRepository` não teria usuário — seria abstração especulativa, contra a convenção 7. A interface nasce se e quando houver segunda implementação real | ciclo 2 |
| `RegistroEmDisco` | *record* privado dentro de `JsonIntakeRepository`. Separa o contrato do arquivo do modelo de domínio — ver Hurdle #17 | ciclo 2 |
| `DailyGoalStore` | Meta diária em `daily-goal.txt`. Ao contrário dos registros, arquivo ilegível aqui **não** lança exceção: cai no padrão. Travar o app por causa da meta impediria o usuário de fazer a única coisa que ele abriu o widget para fazer | ciclo 4 |
| `CorruptedDataException` | Arquivo ilegível ou adulterado. Distingue *dado corrompido* de *falha de I/O*, que pedem respostas diferentes na UI | ciclo 2 |
| `WaterTrackerWindow` | Janela Swing sem borda, always-on-top. Ações destrutivas (`↺` corrigir) e de configuração (`⚙`) ficam **fora** da tela principal: botão de apagar ao lado dos de registrar trocaria um misclick por outro pior | ciclo 3 |

## 6. Design Patterns e convenções

1. **Imutabilidade por padrão.** `record` para value objects. Sem setters.
2. **Validação no construtor.** Um objeto inválido nunca chega a existir. É encapsulamento de
   verdade — não o par getter/setter que só finge encapsular.
3. **Repository.** O domínio não sabe se a persistência é JSON, SQLite ou memória.
4. **Exceções de domínio nomeadas.** `InvalidVolumeException`, não `IllegalArgumentException` crua.
5. **Dependências apontam para dentro.** Ver seção 4.
6. **Nomes de teste em português, formato `deve<Comportamento>Quando<Condição>`.** Um comportamento
   por teste — se precisa de "e" no nome, são dois testes.
7. **Sem abstração especulativa.** Interface só nasce quando existe a segunda implementação real.

## 7. Common Hurdles

> Cada pegadinha não óbvia entra aqui **antes** de seguirmos em frente.

| # | Problema | Solução |
|---|---|---|
| 1 | Surefire default do Maven é o **2.12.4**, que não enxerga JUnit 5. Build fica **verde com zero testes executados** — o pior falso positivo possível. | Pinar `maven-surefire-plugin` 3.x no pom. Sempre conferir `Tests run: N` no log, nunca só o `BUILD SUCCESS`. |
| 2 | JaCoCo antigo quebra com bytecode de JDK novo (`Unsupported class file major version`). Local usa Zulu **25**, CI usa Temurin **21**. | JaCoCo **0.8.13+**. Se a mensagem aparecer, é versão do JaCoCo — não é bug no teste. |
| 3 | O gate de cobertura quebra assim que a UI Swing entra, porque código de UI é caro de testar. | `**/ui/**` e `App.class` excluídos do JaCoCo **desde o início**, não depois que ficar vermelho. |
| 4 | `\` vs `/` quebra no runner do SO oposto. | Nunca concatenar String de path. Sempre `Path.of(...)`. |
| 5 | CRLF/LF gera diff falso e faz o Checkstyle falhar só no Linux. | `.gitattributes` com `* text=auto eol=lf`. |
| 6 | Acentos viram `?` no console do Windows. | `project.build.sourceEncoding=UTF-8` no pom. |
| 7 | Mutação de UI Swing fora da EDT trava ou pisca a janela. | Toda mutação dentro de `SwingUtilities.invokeLater`. *(ciclo 3)* |
| 8 | **`winget` não distribui o Apache Maven** — `winget install Apache.Maven` retorna "No package found". | Zip portável oficial extraído em `%USERPROFILE%\tools\apache-maven-3.9.16`, **com SHA512 conferido**. Não precisa de admin e some apagando a pasta. |
| 9 | OWASP Dependency-Check dentro do `mvn verify` exige NVD API key e baixa uma base grande: a CI fica vermelha por falha de rede, não por bug. Isso corrói a confiança na regra "CI verde em todo commit". | Auditoria de dependências fica no **Dependabot** (fora do build). Reavaliar se o projeto passar a ter dependências de superfície relevante. |
| 10 | **Projeto vazio deixa os gates verdes por vacuidade**: JaCoCo e SpotBugs logam `Skipping ... due to missing execution data` e o build passa. Um pipeline "verde" pode significar "não rodou nada". | Cada gate foi verificado mordendo de verdade: Checkstyle reprovou 2 violações reais; o compilador reprovou o RED; JaCoCo reprovou com `lines covered ratio is 0.42, but expected minimum is 0.85`. Repetir essa prova sempre que um gate for adicionado ou reconfigurado. |
| 11 | `AbbreviationAsWordInName` do google_checks (máx. 1 maiúscula consecutiva) reprova nossa convenção de nome de teste em português: a conjunção "E" seguida de palavra capitalizada (`...DataEVolume...`) dispara em todo teste que descreve duas coisas. | `config/checkstyle/suppressions.xml` suprime **só essa regra**, **só em `src/test/`**. Produção continua sob a regra completa, onde ela protege de verdade (`HTTPResponseXMLParser`). Ligado via `suppressionsFileExpression` = `org.checkstyle.google.suppressionfilter.config` — google_checks lê dessa propriedade, não da padrão do plugin. |
| 14 | **`setx PATH "$env:PATH;..."` pode destruir o PATH.** Dois defeitos somados: dentro do PowerShell, `$env:PATH` é a concatenação do PATH *de sistema* com o *de usuário*, então gravá-lo no PATH do usuário duplica todas as entradas do sistema; e `setx` **trunca silenciosamente em 1024 caracteres**. O PATH de usuário desta máquina já tinha 1016 caracteres — faltavam 8 para a perda de dados. | Usar `[Environment]::SetEnvironmentVariable(...,'User')`, que não tem limite de tamanho, lendo o PATH de usuário com `GetEnvironmentVariable('Path','User')` e anexando apenas o que falta. Comando pronto na seção 3. |
| 20 | **Widget que fica aberto o dia todo não percebe a meia-noite sozinho.** O total é calculado com `LocalDate.now()`, mas `atualizar()` só rodava por ação do usuário — então às 8h da manhã a janela ainda mostrava o total de ontem, até alguém clicar. O dado no disco estava certo; a tela é que mentia. | `javax.swing.Timer` de 60 s que compara `LocalDate.now()` com o dia exibido e só então recarrega. Vale para qualquer coisa derivada de "hoje" numa janela de vida longa. |
| 19 | **Emoji do plano suplementar do Unicode não é confiável no Swing.** `💧` (U+1F4A7) depende de fonte com fallback para o plano suplementar: costuma renderizar no Windows e virar quadrado vazio no Pop!_OS. Símbolos do BMP que já usamos (`↺` `⚙` `×` `⋯`) são seguros; emoji não. | Desenhar o ícone com Java2D. Sai idêntico nos dois SOs, acompanha a cor do tema e escala com o DPI. Uma gota é círculo somado a triângulo — curva simétrica dos dois lados dá uma *folha*, pontuda em cima e embaixo. |
| 18 | Mexer no tamanho da janela **depois** do `pack()` (`setMinimumSize`) deixa o layout desatualizado: o texto do total passou por cima dos botões e a barra de progresso sumiu. Somado a isso, `setPreferredSize` com largura **0** na barra encolhe a coluna inteira. | `pack()` por último e não redimensionar depois. Dar largura real à barra. Lição geral: **rodar o app e olhar** — os 26 testes passavam com a janela quebrada, porque `ui/` não é testada. |
| 17 | **O Gson chama o construtor canônico do *record*, mas embrulha a exceção dele numa `java.lang.RuntimeException` crua.** Um `entries.json` editado à mão com `"milliliters": 0` dispara a `InvalidVolumeException` corretamente — e ela chega ao chamador irreconhecível, impossível de distinguir de um bug nosso. Capturar `RuntimeException` e inspecionar a causa dependeria de detalhe interno do Gson. | Desserializar para um **DTO de disco** (`RegistroEmDisco`, com `String`/`Integer` frouxos aceitando nulo) e converter para o domínio em código nosso, onde a validação lança exceção reconhecível. Bônus: dispensa o `TypeAdapter` de `LocalDate` (o DTO já guarda texto ISO) e desacopla o formato do arquivo da forma do *record* — renomear um componente deixa de quebrar arquivos existentes em silêncio. |
| 16 | Dependabot abriu 7 PRs no primeiro push e **os do `pom.xml` conflitaram entre si**: os bumps mexem em linhas vizinhas do bloco `<properties>`, então mergear um invalida o merge do outro. Também revelou que a API `search.maven.org` devolve `latestVersion` desatualizado — ela reportou Checkstyle 10.26.1 quando o atual era 13.9.0, três majors à frente. | Mergear primeiro os que não colidem e resolver o resto localmente, num commit por dependência, cada um verificado com `mvn clean verify`. Para descobrir versão atual, **não** confiar na `search.maven.org`: usar o `maven-metadata.xml` do repositório ou deixar o Dependabot dizer. |
| 15 | Mesmo com `JAVA_HOME` apontando para o JDK 21, `java` digitado no terminal resolve para o **Zulu 25**, porque `C:\Program Files\Zulu\zulu-25\bin\` vem antes no PATH. | Inofensivo para o build: o `mvn` usa `JAVA_HOME` e foi verificado rodando em `Java version: 21.0.11, Eclipse Adoptium`. Mas ao executar o jar à mão, chamar o `java` do JDK 21 pelo caminho completo — senão o app roda sob um runtime diferente do que a CI validou. |
| 13 | SpotBugs acusa `EI_EXPOSE_REP` num *record* cujo componente `List` foi construído com `Stream.toList()` — ele não consegue provar que o resultado é imutável, mesmo sendo. | Construir com **`List.copyOf(...)`**, que o detector reconhece como imutável. Custo zero: `copyOf` devolve a própria instância quando ela já é imutável. **Não** suprimir o finding e **não** trocar por cópia defensiva no acessor (que seria pior e alocaria a cada chamada). |
| 12 | **SpotBugs quebra sob JDK 25**: `Unsupported class file major version 69` ao escanear `java.lang.*`. O ASM embutido no SpotBugs 4.9.3.0 ainda não lê bytecode do Java 25. Pior: o build passava em Checkstyle, testes e cobertura antes de estourar — dava a impressão de que só o SAST estava com problema, quando a causa era divergência de ambiente. | **Rodar o build com JDK 21 localmente**, igual à CI. Zip portável do Temurin 21 em `%USERPROFILE%\tools\jdk-21.0.11+10`. A lição maior: `release=21` garante o *bytecode gerado*, mas **não** a versão do JDK que as ferramentas de build enxergam. "Passou na minha máquina" com JDK diferente da CI não vale nada. |

## 8. Pipeline principal do sistema

```
Clique "+250 ml"
  → new WaterEntry(hoje, 250)         valida faixa e nulos; lança InvalidVolumeException
  → JsonIntakeRepository.save(entry)  escrita atômica: temp + ATOMIC_MOVE
  → DailyIntake.totalFor(hoje)        soma acumulada em long (evita overflow)
  → atualiza barra de progresso       dentro de SwingUtilities.invokeLater
```

Ordem dos gates no `mvn clean verify` (falha o mais cedo possível):

```
checkstyle → compile → test → jacoco:report → jacoco:check → spotbugs:check
```

## 9. Definition of Done

Um commit só entra em `main` quando **todos** os itens abaixo forem verdade:

- [ ] Teste escrito **antes** do código, com o RED **observado** — não presumido
- [ ] `mvn clean verify` verde localmente
- [ ] `Tests run: N` conferido no log (N > 0 e igual ao esperado)
- [ ] Zero violações de Checkstyle
- [ ] Zero findings de SpotBugs/FindSecBugs
- [ ] Cobertura ≥ 85% no código não-UI
- [ ] Casos de borda cobertos: nulo, zero, negativo, limite inferior, limite superior, overflow
- [ ] Risco de segurança da superfície tocada foi **avaliado e registrado** — mesmo que a conclusão
      seja "não se aplica"
- [ ] CLAUDE.md atualizado se apareceu algo não óbvio
- [ ] Commit pequeno, com mensagem explicando o **porquê**
- [ ] CI verde nos **dois** sistemas operacionais

## 10. Superfície de segurança deste projeto

Registrado explicitamente para que a regra "segurança é hábito" não vire cerimônia vazia:

**Se aplica:**
- Validação de entrada: volume nulo, zero, negativo, acima do teto sano — *feito, ciclo 1*
- Overflow aritmético no somatório diário (acumular em `long`, não `int`) — *feito, ciclo 1*
- Parse de JSON malformado ou adulterado à mão pelo usuário — *feito, ciclo 2*. Arquivo em disco é
  **entrada não confiável**: passa por DTO e revalida no domínio (Hurdle #17)
- Truncamento do arquivo por queda no meio da gravação — *feito, ciclo 2*: temp + `ATOMIC_MOVE`
- **Perda de registro** por escrita concorrente de duas instâncias — *deliberadamente não tratado*.
  A troca atômica garante que o arquivo nunca corrompe; o que sobra é `save()` fazer
  ler-alterar-gravar, então dois widgets abertos podem sobrescrever o registro um do outro. Custa
  um `FileLock` para resolver, mas o app é de uso pessoal e ninguém abre dois. Tratar quando (e se)
  acontecer

**Não se aplica** (app local, single-user, sem rede e sem servidor): SSRF, rate limiting,
autenticação, autorização, CSRF, injeção de SQL, gestão de segredos.

**Path traversal em `WATER_TRACKER_HOME` foi reavaliado e também não se aplica.** Traversal
pressupõe entrada controlada por terceiro escapando de um sandbox. Aqui quem define a variável é o
próprio dono da máquina, que já pode escrever onde quiser — não há fronteira de privilégio a
atravessar. Validar que o caminho é utilizável faz sentido; construir defesa anti-traversal seria
cerimônia. Se o app um dia rodar com privilégio diferente do usuário, ou ler essa configuração de
fonte que não seja o próprio usuário, esta conclusão muda **antes** do código.
