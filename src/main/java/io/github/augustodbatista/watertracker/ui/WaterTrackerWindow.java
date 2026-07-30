package io.github.augustodbatista.watertracker.ui;

import io.github.augustodbatista.watertracker.domain.DailyIntake;
import io.github.augustodbatista.watertracker.domain.WaterEntry;
import io.github.augustodbatista.watertracker.storage.DailyGoalStore;
import io.github.augustodbatista.watertracker.storage.JsonIntakeRepository;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/** Janela sem borda, sempre no topo, arrastável pelo corpo. */
final class WaterTrackerWindow extends JFrame {

  private static final long serialVersionUID = 1L;

  private static final int[] PORCOES_ML = {200, 250, 500};
  private static final int PASSO_DA_META_ML = 100;
  private static final int INTERVALO_DA_VIRADA_MS = 60_000;

  private static final int LARGURA_UTIL = 132;
  private static final int GOTA_LARGURA = 58;
  private static final int GOTA_ALTURA = 90;
  private static final int RAIO_DA_JANELA = 18;

  private static final Color FUNDO = new Color(0x0E, 0x14, 0x19);
  private static final Color RECIPIENTE = new Color(0x1A, 0x24, 0x2D);
  private static final Color AGUA = new Color(0x4A, 0x7B, 0xE8);
  private static final Color BRILHO = new Color(0x8F, 0xB4, 0xF5);
  private static final Color TEXTO = new Color(0xED, 0xF2, 0xF7);
  private static final Color TEXTO_FRACO = new Color(0x78, 0x89, 0x9B);

  private final transient JsonIntakeRepository registros;
  private final transient DailyGoalStore metas;

  private LocalDate diaExibido = LocalDate.now();

  private final JLabel medidor = new JLabel();
  private final JLabel total = new JLabel("", SwingConstants.CENTER);
  private final JLabel metaEscrita = new JLabel("", SwingConstants.CENTER);

  WaterTrackerWindow(JsonIntakeRepository registros, DailyGoalStore metas) {
    this.registros = registros;
    this.metas = metas;

    setUndecorated(true);
    setAlwaysOnTop(true);
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setTitle("Water Tracker");
    setIconImages(List.of(gotaComoImagem(16), gotaComoImagem(32), gotaComoImagem(48)));

    // O widget fica aberto o dia todo, entao ele precisa perceber a meia-noite sozinho: sem isso,
    // as 8h da manha ele ainda mostraria o total de ontem ate o usuario clicar em alguma coisa.
    new Timer(INTERVALO_DA_VIRADA_MS, evento -> verificarViradaDoDia()).start();

    JPanel raiz = new JPanel(new BorderLayout(0, 0));
    raiz.setBackground(FUNDO);
    raiz.setBorder(BorderFactory.createEmptyBorder(10, 14, 16, 14));
    raiz.add(cabecalho(), BorderLayout.NORTH);
    raiz.add(leitura(), BorderLayout.CENTER);
    raiz.add(controles(), BorderLayout.SOUTH);

    permitirArrastar(raiz);
    setContentPane(raiz);
    atualizar();
    // Hurdle #18: pack() por ultimo e sem mexer no tamanho depois.
    pack();
    arredondarCantos();
    posicionarNoCantoInferiorDireito();
  }

  private JPanel cabecalho() {
    JPanel acoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
    acoes.setOpaque(false);
    // Corrigir e configurar ficam fora da tela principal: um botao de apagar ao lado dos de
    // registrar so trocaria um misclick por outro, pior.
    acoes.add(icone("↺", "Corrigir registros de hoje", this::abrirCorrecoes));
    acoes.add(icone("⚙", "Configurar meta diária", this::abrirConfiguracoes));
    acoes.add(icone("×", "Fechar", this::dispose));

    JPanel linha = new JPanel(new BorderLayout());
    linha.setOpaque(false);
    linha.add(acoes, BorderLayout.EAST);
    return linha;
  }

  private JLabel icone(String simbolo, String dica, Runnable acao) {
    JLabel rotulo = new JLabel(simbolo);
    rotulo.setForeground(TEXTO_FRACO);
    rotulo.setFont(rotulo.getFont().deriveFont(Font.PLAIN, 14f));
    rotulo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    rotulo.setToolTipText(dica);
    rotulo.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent evento) {
            acao.run();
          }

          @Override
          public void mouseEntered(MouseEvent evento) {
            rotulo.setForeground(TEXTO);
          }

          @Override
          public void mouseExited(MouseEvent evento) {
            rotulo.setForeground(TEXTO_FRACO);
          }
        });
    return rotulo;
  }

  /** A gota é o instrumento: o nível dela é a informação principal, não um enfeite ao lado dela. */
  private JPanel leitura() {
    JPanel coluna = new JPanel();
    coluna.setLayout(new BoxLayout(coluna, BoxLayout.Y_AXIS));
    coluna.setOpaque(false);

    medidor.setAlignmentX(CENTER_ALIGNMENT);

    // Numero sem unidade: "ml" ja aparece na linha da meta, logo abaixo.
    total.setForeground(TEXTO);
    total.setFont(total.getFont().deriveFont(Font.BOLD, 27f));
    total.setAlignmentX(CENTER_ALIGNMENT);

    metaEscrita.setForeground(TEXTO_FRACO);
    metaEscrita.setFont(metaEscrita.getFont().deriveFont(Font.PLAIN, 11f));
    metaEscrita.setAlignmentX(CENTER_ALIGNMENT);

    coluna.add(Box.createVerticalStrut(4));
    coluna.add(medidor);
    coluna.add(Box.createVerticalStrut(12));
    coluna.add(total);
    coluna.add(Box.createVerticalStrut(1));
    coluna.add(metaEscrita);
    coluna.add(Box.createVerticalStrut(16));
    return coluna;
  }

  private JPanel controles() {
    JPanel coluna = new JPanel();
    coluna.setLayout(new BoxLayout(coluna, BoxLayout.Y_AXIS));
    coluna.setOpaque(false);
    coluna.add(porcoes());
    coluna.add(Box.createVerticalStrut(8));
    coluna.add(outroValor());
    return coluna;
  }

  /** Bolhas soltas, com vão entre elas — é o vão que faz cada uma ler como uma bolha. */
  private JPanel porcoes() {
    JPanel bloco = new JPanel(new GridLayout(0, 1, 0, 7));
    bloco.setOpaque(false);
    for (int porcao : PORCOES_ML) {
      bloco.add(
          botao("+" + porcao + " ml", "Registrar " + porcao + " ml", () -> registrar(porcao)));
    }
    return bloco;
  }

  private BotaoBolha botao(String texto, String dica, Runnable acao) {
    BotaoBolha botao = new BotaoBolha(texto, true);
    botao.setBackground(AGUA);
    botao.setForeground(Color.WHITE);
    botao.setFont(botao.getFont().deriveFont(Font.BOLD, 12f));
    botao.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));
    botao.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    botao.setToolTipText(dica);
    botao.setPreferredSize(new Dimension(LARGURA_UTIL, 34));
    botao.addActionListener(evento -> acao.run());
    botao.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseEntered(MouseEvent evento) {
            botao.setBackground(BRILHO);
          }

          @Override
          public void mouseExited(MouseEvent evento) {
            botao.setBackground(AGUA);
          }
        });
    return botao;
  }

  /**
   * Abre um diálogo em vez de registrar na hora, então não pode parecer um botão de porção.
   *
   * <p>Mesma forma de bolha, mas vazia: a diferença de tratamento carrega a diferença de ação.
   */
  private BotaoBolha outroValor() {
    BotaoBolha botao = new BotaoBolha("Outro valor", false);
    botao.setBackground(RECIPIENTE);
    botao.setForeground(TEXTO_FRACO);
    botao.setFont(botao.getFont().deriveFont(Font.PLAIN, 11f));
    botao.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
    botao.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    botao.setToolTipText("Digitar uma quantidade qualquer");
    botao.setAlignmentX(CENTER_ALIGNMENT);
    botao.setPreferredSize(new Dimension(LARGURA_UTIL, 30));
    botao.setMaximumSize(new Dimension(LARGURA_UTIL, 30));
    botao.addActionListener(evento -> perguntarQuantidade());
    botao.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseEntered(MouseEvent evento) {
            botao.setForeground(TEXTO);
            botao.setBackground(AGUA);
          }

          @Override
          public void mouseExited(MouseEvent evento) {
            botao.setForeground(TEXTO_FRACO);
            botao.setBackground(RECIPIENTE);
          }
        });
    return botao;
  }

  private void perguntarQuantidade() {
    String digitado =
        JOptionPane.showInputDialog(
            this, "Quantos mililitros?", "Registrar água", JOptionPane.PLAIN_MESSAGE);
    if (digitado == null || digitado.isBlank()) {
      return;
    }
    try {
      registrar(Integer.parseInt(digitado.trim()));
    } catch (NumberFormatException e) {
      avisar("Digite apenas o número em mililitros. Por exemplo: 350");
    }
  }

  private void abrirConfiguracoes() {
    JSpinner campo =
        new JSpinner(
            new SpinnerNumberModel(
                metas.load(),
                DailyGoalStore.META_MINIMA_ML,
                DailyGoalStore.META_MAXIMA_ML,
                PASSO_DA_META_ML));

    JPanel painel = new JPanel(new GridLayout(0, 1, 0, 8));
    painel.add(new JLabel("Meta diária, em mililitros:"));
    painel.add(campo);

    int escolha =
        JOptionPane.showConfirmDialog(
            this, painel, "Configurações", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
    if (escolha != JOptionPane.OK_OPTION) {
      return;
    }
    try {
      metas.save((Integer) campo.getValue());
      atualizar();
    } catch (RuntimeException e) {
      avisar(e.getMessage());
    }
  }

  private void abrirCorrecoes() {
    boolean primeiraVolta = true;
    while (true) {
      List<WaterEntry> doDia;
      try {
        doDia = new DailyIntake(LocalDate.now(), registros.loadAll()).entries();
      } catch (RuntimeException e) {
        avisar(e.getMessage());
        return;
      }
      if (doDia.isEmpty()) {
        // Só avisa se já abriu vazio. Reabrir para dizer "acabou" depois de remover o último
        // registro seria um clique a mais para informar o óbvio.
        if (primeiraVolta) {
          avisar("Não há registros hoje para corrigir.");
        }
        return;
      }
      primeiraVolta = false;
      if (!corrigirUmaVez(doDia)) {
        return;
      }
    }
  }

  /**
   * Mostra os registros do dia e aplica uma correção.
   *
   * @return {@code true} se o diálogo deve reabrir para outra correção
   */
  private boolean corrigirUmaVez(List<WaterEntry> doDia) {
    JList<String> lista =
        new JList<>(doDia.stream().map(r -> r.milliliters() + " ml").toArray(String[]::new));
    lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    // O ultimo e o candidato obvio: quem errou o clique acabou de errar.
    lista.setSelectedIndex(doDia.size() - 1);

    JScrollPane rolagem = new JScrollPane(lista);
    rolagem.setPreferredSize(new Dimension(210, 108));

    Object[] opcoes = {"Remover selecionado", "Zerar o dia", "Fechar"};
    int escolha =
        JOptionPane.showOptionDialog(
            this,
            rolagem,
            "Corrigir registros de hoje",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            opcoes,
            opcoes[2]);

    if (escolha == 0 && lista.getSelectedIndex() >= 0) {
      registros.remove(doDia.get(lista.getSelectedIndex()));
      atualizar();
      return true;
    }
    if (escolha == 1 && confirmarZerar(doDia.size())) {
      registros.clearDay(LocalDate.now());
      atualizar();
    }
    return false;
  }

  private boolean confirmarZerar(int quantidade) {
    int resposta =
        JOptionPane.showConfirmDialog(
            this,
            "Apagar os " + quantidade + " registros de hoje? Isso não tem desfazer.",
            "Zerar o dia",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    return resposta == JOptionPane.YES_OPTION;
  }

  private void registrar(int mililitros) {
    try {
      registros.save(new WaterEntry(LocalDate.now(), mililitros));
      atualizar();
    } catch (RuntimeException e) {
      avisar(e.getMessage());
    }
  }

  private void verificarViradaDoDia() {
    if (!LocalDate.now().equals(diaExibido)) {
      atualizar();
    }
  }

  private void atualizar() {
    diaExibido = LocalDate.now();
    long consumido;
    try {
      consumido = new DailyIntake(LocalDate.now(), registros.loadAll()).totalMilliliters();
    } catch (RuntimeException e) {
      total.setText("—");
      total.setToolTipText(e.getMessage());
      metaEscrita.setText("erro ao ler o arquivo");
      return;
    }
    int meta = metas.load();
    total.setText(String.valueOf(consumido));
    total.setToolTipText(null);
    metaEscrita.setText("de " + meta + " ml");

    double fracao = Math.min(1.0, consumido / (double) meta);
    medidor.setIcon(new GotaDagua(GOTA_LARGURA, GOTA_ALTURA, AGUA, RECIPIENTE, fracao));
    medidor.setToolTipText(Math.round(fracao * 100) + "% da meta");
  }

  private void avisar(String mensagem) {
    JOptionPane.showMessageDialog(this, mensagem, "Water Tracker", JOptionPane.WARNING_MESSAGE);
  }

  /** Cantos arredondados. Nem todo servidor gráfico suporta recorte de janela. */
  private void arredondarCantos() {
    try {
      setShape(
          new RoundRectangle2D.Double(
              0, 0, getWidth(), getHeight(), RAIO_DA_JANELA, RAIO_DA_JANELA));
    } catch (UnsupportedOperationException e) {
      setShape(null);
    }
  }

  /** Sem borda não há barra de título, então a janela inteira vira a alça de arraste. */
  private void permitirArrastar(JComponent raiz) {
    Point[] agarrou = new Point[1];
    MouseAdapter arraste =
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent evento) {
            agarrou[0] = evento.getPoint();
          }

          @Override
          public void mouseDragged(MouseEvent evento) {
            if (agarrou[0] != null) {
              setLocation(
                  getX() + evento.getX() - agarrou[0].x, getY() + evento.getY() - agarrou[0].y);
            }
          }
        };
    raiz.addMouseListener(arraste);
    raiz.addMouseMotionListener(arraste);
  }

  private void posicionarNoCantoInferiorDireito() {
    var area = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    setLocation(area.x + area.width - getWidth() - 24, area.y + area.height - getHeight() - 24);
  }

  /** Rende a gota num bitmap, para o ícone da janela e da barra de tarefas. */
  private static Image gotaComoImagem(int tamanho) {
    BufferedImage imagem = new BufferedImage(tamanho, tamanho, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = imagem.createGraphics();
    int largura = Math.round(tamanho * 0.62f);
    new GotaDagua(largura, tamanho, AGUA).paintIcon(null, g2, (tamanho - largura) / 2, 0);
    g2.dispose();
    return imagem;
  }

  /**
   * Botão em forma de bolha.
   *
   * <p>O Swing não tem botão arredondado: o look-and-feel pinta um retângulo. Aqui o preenchimento
   * padrão é desligado e a forma é pintada à mão, com raio igual à altura — o que dá extremidades
   * semicirculares em vez de cantos só suavizados.
   */
  private static final class BotaoBolha extends JButton {

    private static final long serialVersionUID = 1L;

    private final boolean preenchida;

    BotaoBolha(String texto, boolean preenchida) {
      super(texto);
      this.preenchida = preenchida;
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int arco = getHeight();
      g2.setColor(getBackground());
      if (preenchida) {
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), arco, arco);
      } else {
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arco, arco);
      }
      g2.dispose();
      super.paintComponent(g);
    }
  }

  /**
   * Gota d'água desenhada, no lugar do emoji {@code 💧}, funcionando como medidor de nível.
   *
   * <p>Emoji do plano suplementar do Unicode não tem fallback confiável no Java2D: costuma
   * renderizar no Windows e virar quadrado vazio no Pop!_OS, dependendo das fontes instaladas.
   * Desenhar garante o mesmo resultado nos dois sistemas e acompanha a cor do tema.
   */
  private static final class GotaDagua implements Icon {

    private final int largura;
    private final int altura;
    private final Color cheia;
    private final Color vazia;
    private final double preenchimento;

    /** Gota inteiramente preenchida, para o ícone da janela. */
    GotaDagua(int largura, int altura, Color cor) {
      this(largura, altura, cor, cor, 1.0);
    }

    /**
     * Gota que funciona como medidor.
     *
     * @param preenchimento fração cheia, de 0 a 1, medida de baixo para cima
     */
    GotaDagua(int largura, int altura, Color cheia, Color vazia, double preenchimento) {
      this.largura = largura;
      this.altura = altura;
      this.cheia = cheia;
      this.vazia = vazia;
      this.preenchimento = preenchimento;
    }

    @Override
    public void paintIcon(Component componente, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      Area gota = forma(x, y);
      g2.setColor(vazia);
      g2.fill(gota);

      if (preenchimento > 0) {
        // Recorta pela silhueta e pinta so a faixa de baixo, numa copia propria do Graphics para
        // o clip nao vazar para o resto da pintura.
        Graphics2D recorte = (Graphics2D) g2.create();
        int alturaCheia = (int) Math.round(altura * preenchimento);
        int nivel = y + altura - alturaCheia;
        recorte.clip(gota);
        recorte.setColor(cheia);
        recorte.fillRect(x, nivel, largura, alturaCheia);
        if (preenchimento < 1) {
          // Linha de superficie: e o que faz ler como liquido num recipiente, e nao como forma
          // preenchida pela metade.
          recorte.setColor(BRILHO);
          recorte.setStroke(new BasicStroke(2f));
          recorte.drawLine(x, nivel, x + largura, nivel);
        }
        recorte.dispose();
      }

      // Sem contorno, com o dia zerado a gota inteira ficaria na cor vazia, quase indistinguivel
      // do fundo. O contorno mantem a silhueta legivel em qualquer nivel.
      g2.setColor(new Color(cheia.getRed(), cheia.getGreen(), cheia.getBlue(), 130));
      g2.setStroke(new BasicStroke(1.4f));
      g2.draw(gota);
      g2.dispose();
    }

    /**
     * Círculo embaixo somado a um triângulo em cima — ver Hurdle #19.
     *
     * <p>Recuado 1px de cada lado: encostando na borda do ícone, o contorno sai cortado embaixo.
     */
    private Area forma(int x, int y) {
      double margem = 1;
      double larg = largura - 2 * margem;
      double alt = altura - 2 * margem;
      double esquerda = x + margem;
      double topo = y + margem;
      double centro = esquerda + larg / 2.0;
      double baseDoTriangulo = topo + alt - larg * 0.55;

      Path2D ponta = new Path2D.Double();
      ponta.moveTo(centro, topo);
      ponta.lineTo(esquerda + larg, baseDoTriangulo);
      ponta.lineTo(esquerda, baseDoTriangulo);
      ponta.closePath();

      Area gota = new Area(new Ellipse2D.Double(esquerda, topo + alt - larg, larg, larg));
      gota.add(new Area(ponta));
      return gota;
    }

    @Override
    public int getIconWidth() {
      return largura;
    }

    @Override
    public int getIconHeight() {
      return altura;
    }
  }
}
