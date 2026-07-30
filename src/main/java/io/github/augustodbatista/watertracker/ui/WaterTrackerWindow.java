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
import javax.swing.JProgressBar;
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

  /** Largura do conteúdo, em pixels. Dita a proporção retrato da janela. */
  private static final int LARGURA_UTIL = 118;

  private static final Color FUNDO = new Color(0x1B, 0x26, 0x32);
  private static final Color TEXTO = new Color(0xE8, 0xF1, 0xF8);
  private static final Color TEXTO_FRACO = new Color(0x8C, 0xA3, 0xB8);
  /** Azul royal (#4169E1) clareado. Usada nos botões, na barra de progresso e na gota. */
  private static final Color AGUA = new Color(0x4A, 0x7B, 0xE8);
  private static final Color TRILHO = new Color(0x2A, 0x3A, 0x4A);

  private final transient JsonIntakeRepository registros;
  private final transient DailyGoalStore metas;

  /** De quanto em quanto tempo se verifica se o dia virou. */
  private static final int INTERVALO_DA_VIRADA_MS = 60_000;

  private LocalDate diaExibido = LocalDate.now();

  /** Tamanho da gota-medidor, em pixels. Alta o bastante para o nível ser legível. */
  private static final int GOTA_LARGURA = 30;

  private static final int GOTA_ALTURA = 48;

  private final JLabel total = new JLabel("", SwingConstants.CENTER);
  private final JLabel metaEscrita = new JLabel("", SwingConstants.CENTER);
  private final JLabel medidor = new JLabel();
  private final JProgressBar progresso = new JProgressBar();

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

    JPanel raiz = new JPanel(new BorderLayout(0, 10));
    raiz.setBackground(FUNDO);
    raiz.setBorder(BorderFactory.createEmptyBorder(12, 16, 14, 16));
    raiz.add(cabecalho(), BorderLayout.NORTH);
    raiz.add(centro(), BorderLayout.CENTER);
    raiz.add(botoes(), BorderLayout.SOUTH);

    permitirArrastar(raiz);
    setContentPane(raiz);
    atualizar();
    // Hurdle #18: pack() por ultimo e sem mexer no tamanho depois.
    pack();
    posicionarNoCantoInferiorDireito();
  }

  private JPanel cabecalho() {
    JPanel linha = new JPanel(new BorderLayout());
    linha.setOpaque(false);

    JPanel acoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    acoes.setOpaque(false);
    // Corrigir fica fora da tela principal de proposito: um botao de apagar ao lado dos de
    // registrar so trocaria um misclick por outro, pior.
    acoes.add(icone("↺", "Corrigir registros de hoje", this::abrirCorrecoes));
    acoes.add(icone("⚙", "Configurar meta diária", this::abrirConfiguracoes));
    acoes.add(icone("×", "Fechar", this::dispose));

    linha.add(acoes, BorderLayout.EAST);
    return linha;
  }

  private JLabel icone(String simbolo, String dica, Runnable acao) {
    JLabel rotulo = new JLabel(simbolo);
    rotulo.setForeground(TEXTO_FRACO);
    rotulo.setFont(rotulo.getFont().deriveFont(Font.PLAIN, 15f));
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

  private JPanel centro() {
    JPanel coluna = new JPanel();
    coluna.setLayout(new BoxLayout(coluna, BoxLayout.Y_AXIS));
    coluna.setOpaque(false);

    total.setForeground(TEXTO);
    total.setFont(total.getFont().deriveFont(Font.BOLD, 20f));
    total.setAlignmentX(CENTER_ALIGNMENT);

    // Em retrato a janela e estreita: "4050 / 3500 ml" numa linha so ficaria mais largo que o
    // widget inteiro. Separado, o numero que importa fica grande e a meta vira legenda.
    metaEscrita.setForeground(TEXTO_FRACO);
    metaEscrita.setFont(metaEscrita.getFont().deriveFont(Font.PLAIN, 11f));
    metaEscrita.setAlignmentX(CENTER_ALIGNMENT);

    progresso.setForeground(AGUA);
    progresso.setBackground(TRILHO);
    progresso.setBorderPainted(false);
    // Hurdle #18: largura real, nao zero, senao o pack() encolhe a coluna inteira.
    progresso.setPreferredSize(new Dimension(LARGURA_UTIL, 8));
    progresso.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
    progresso.setAlignmentX(CENTER_ALIGNMENT);

    JPanel textos = new JPanel();
    textos.setLayout(new BoxLayout(textos, BoxLayout.Y_AXIS));
    textos.setOpaque(false);
    textos.add(total);
    textos.add(Box.createVerticalStrut(2));
    textos.add(metaEscrita);

    // FlowLayout centraliza os filhos verticalmente na linha, o que alinha a gota com o bloco de
    // texto sem depender de altura fixa - fonte maior no Pop!_OS continua alinhada.
    JPanel linha = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
    linha.setOpaque(false);
    linha.add(medidor);
    linha.add(textos);
    linha.setAlignmentX(CENTER_ALIGNMENT);

    coluna.add(linha);
    coluna.add(Box.createVerticalStrut(12));
    coluna.add(progresso);
    return coluna;
  }

  /** Botões empilhados: é o que dá à janela a proporção retrato, e alvos maiores para o clique. */
  private JPanel botoes() {
    JPanel coluna = new JPanel(new GridLayout(0, 1, 0, 6));
    coluna.setOpaque(false);
    for (int porcao : PORCOES_ML) {
      coluna.add(
          botao("+" + porcao + " ml", "Registrar " + porcao + " ml", () -> registrar(porcao)));
    }
    coluna.add(botao("⋯", "Digitar outra quantidade", this::perguntarQuantidade));
    return coluna;
  }

  private JButton botao(String texto, String dica, Runnable acao) {
    JButton botao = new JButton(texto);
    botao.setFocusPainted(false);
    botao.setBackground(AGUA);
    botao.setForeground(Color.WHITE);
    botao.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
    botao.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    botao.setToolTipText(dica);
    botao.addActionListener(evento -> acao.run());
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
      total.setText("erro");
      total.setToolTipText(e.getMessage());
      metaEscrita.setText("ao ler o arquivo");
      return;
    }
    int meta = metas.load();
    total.setText(consumido + " ml");
    total.setToolTipText(null);
    metaEscrita.setText("de " + meta + " ml");
    progresso.setMaximum(meta);
    progresso.setValue((int) Math.min(consumido, meta));

    double fracao = Math.min(1.0, consumido / (double) meta);
    medidor.setIcon(new GotaDagua(GOTA_LARGURA, GOTA_ALTURA, AGUA, TRILHO, fracao));
    medidor.setToolTipText(Math.round(fracao * 100) + "% da meta");
  }

  private void avisar(String mensagem) {
    JOptionPane.showMessageDialog(this, mensagem, "Water Tracker", JOptionPane.WARNING_MESSAGE);
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
   * Gota d'água desenhada, no lugar do emoji {@code 💧}.
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
        // Recorta pela silhueta e pinta so a faixa de baixo: a agua sobe dentro da gota, em vez
        // de a gota inteira mudar de cor. Copia propria do Graphics para nao levar o clip adiante.
        Graphics2D recorte = (Graphics2D) g2.create();
        int alturaCheia = (int) Math.round(altura * preenchimento);
        recorte.clip(gota);
        recorte.setColor(cheia);
        recorte.fillRect(x, y + altura - alturaCheia, largura, alturaCheia);
        recorte.dispose();
      }

      // Sem contorno, com o dia zerado a gota inteira ficaria na cor vazia, quase indistinguivel
      // do fundo. O contorno mantem a silhueta legivel em qualquer nivel.
      g2.setColor(new Color(cheia.getRed(), cheia.getGreen(), cheia.getBlue(), 130));
      g2.setStroke(new BasicStroke(1.4f));
      g2.draw(gota);
      g2.dispose();
    }

    /** Círculo embaixo somado a um triângulo em cima — ver Hurdle #19. */
    private Area forma(int x, int y) {
      double centro = x + largura / 2.0;
      double baseDoTriangulo = y + altura - largura * 0.55;

      Path2D ponta = new Path2D.Double();
      ponta.moveTo(centro, y);
      ponta.lineTo(x + largura, baseDoTriangulo);
      ponta.lineTo(x, baseDoTriangulo);
      ponta.closePath();

      Area gota = new Area(new Ellipse2D.Double(x, y + altura - largura, largura, largura));
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
