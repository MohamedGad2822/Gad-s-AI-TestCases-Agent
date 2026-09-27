package com.ignteq.tcagent;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

/** Drag-and-drop desktop front end for the CLI pipeline in {@link Main}. */
public class Gui {

    private final JFrame frame = new JFrame("TC Agent");
    private final DefaultListModel<Path> fileModel = new DefaultListModel<>();
    private final JList<Path> fileList = new JList<>(fileModel);

    private static final String[] MODULE_PRESETS = {
            "Categories", "Forms", "Field Library", "Agenda", "Agenda Item Types",
            "Zones", "Tickets", "Basic Details", "Media", "Permissions"
    };

    private final JComboBox<String> moduleCombo = new JComboBox<>(MODULE_PRESETS);
    private final JComboBox<String> codeCombo = new JComboBox<>(new String[]{
            "(auto - derived from module)", "CAT", "FF", "FL", "AGN", "AIT", "ZN", "TKT", "BD", "MED", "PRM"
    });
    private final JComboBox<String> startCombo = new JComboBox<>(new String[]{"1", "10", "20", "30", "50", "100"});
    private final JComboBox<String> countCombo = new JComboBox<>(new String[]{
            "Auto (model decides)", "10", "20", "30", "40", "50", "80"
    });
    private final JComboBox<String> projectCombo = new JComboBox<>(new String[]{"24"});
    private final JComboBox<String> maxTokensCombo =
            new JComboBox<>(new String[]{"8000", "16000", "24000", "32000", "48000", "64000"});
    private final JComboBox<String> outCombo = new JComboBox<>(new String[]{"out"});
    private final JComboBox<String> modelCombo = new JComboBox<>(new String[]{
            "claude-sonnet-5", "claude-opus-5-5", "claude-haiku-4-5-20251001",
            "gpt-5.5", "gpt-5.5-pro", "gpt-5-chat-latest",
            "gemini-3.8-flash", "gemini-3.5-flash", "gemini-3.1-pro-preview",
            "deepseek-v4-pro", "deepseek-flash",
            "grok-4.7",
            "anthropic/claude-sonnet-5", "openai/gpt-5.5", "deepseek/deepseek-v4-pro", "google/gemini-3.8-flash"
    });
    private final JComboBox<String> providerCombo = new JComboBox<>(new String[]{
            "Auto (guess from key)", "anthropic", "openai", "gemini", "deepseek", "xai", "openrouter"
    });
    private final JTextArea notesArea = new JTextArea(5, 20);
    private final JPasswordField keyField = new JPasswordField();
    private final JLabel providerLabel = new JLabel(" ");
    private final JCheckBox dryRunBox = new JCheckBox("Dry run (build the request, no API call)");
    private final JCheckBox debugBox = new JCheckBox("Save raw model response (debug)");

    private final JButton generateBtn = new JButton("Generate test cases");
    private final JButton openOutBtn = new JButton("Open output folder");
    private final JLabel statusLabel = new JLabel(" ");
    private final JProgressBar progressBar = new JProgressBar();
    private final JTextArea logArea = new JTextArea();

    private final JTabbedPane outputTabs = new JTabbedPane();
    private final DefaultTableModel resultsModel = new DefaultTableModel(
            new Object[]{"ID", "Title", "Type", "Priority", "TBD"}, 0) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable resultsTable = new JTable(resultsModel);
    private final JTextArea detailArea = new JTextArea();
    private List<TestCase> lastCases = List.of();

    private Path lastOutDir = null;

    private static final Path OLD_SAVED_CONFIG =
            Paths.get(System.getProperty("user.home"), ".tc-agent", "config.properties");
    private static final Path KEYS_DIR =
            Paths.get(System.getProperty("user.home"), ".tc-agent", "keys");

    /** One dedicated plaintext file per provider - e.g. ~/.tc-agent/keys/deepseek.key -
     *  so switching providers doesn't overwrite the others, and each key only needs
     *  to be pasted once per machine. */
    private static Path keyFile(Provider p) {
        return KEYS_DIR.resolve(p.name().toLowerCase() + ".key");
    }

    private static String loadSavedApiKey(Provider p) {
        try {
            return Files.readString(keyFile(p)).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static void saveApiKey(Provider p, String key) {
        try {
            Files.createDirectories(KEYS_DIR);
            Files.writeString(keyFile(p), key);
        } catch (IOException ignored) {
            // best-effort only - worst case you re-paste it next time
        }
    }

    private static Provider firstProviderWithSavedKey() {
        for (Provider p : List.of(Provider.ANTHROPIC, Provider.OPENAI, Provider.GEMINI,
                Provider.DEEPSEEK, Provider.XAI, Provider.OPENROUTER)) {
            if (!loadSavedApiKey(p).isEmpty()) return p;
        }
        return Provider.ANTHROPIC;
    }

    /** One-time carryover from the old single-key file (pre per-provider storage), if present. */
    private static void migrateOldKeyIfNeeded() {
        Properties p = new Properties();
        try (var in = Files.newInputStream(OLD_SAVED_CONFIG)) {
            p.load(in);
        } catch (IOException e) {
            return;
        }
        String old = p.getProperty("apiKey", "").trim();
        if (old.isEmpty()) return;
        Provider detected = Provider.detect(old);
        if (loadSavedApiKey(detected).isEmpty()) saveApiKey(detected, old);
    }

    private static final Path SESSION_FILE =
            Paths.get(System.getProperty("user.home"), ".tc-agent", "session.json");

    /** Everything except the API key (that has its own file) - so reopening the app resumes where you left off. */
    private void saveSession() {
        Map<String, Object> s = Json.obj();
        List<Object> files = new ArrayList<>();
        for (Path p : Collections.list(fileModel.elements())) files.add(p.toString());
        s.put("files", files);
        s.put("module", comboText(moduleCombo));
        s.put("code", comboText(codeCombo));
        s.put("start", comboText(startCombo));
        s.put("count", comboText(countCombo));
        s.put("project", comboText(projectCombo));
        s.put("model", comboText(modelCombo));
        s.put("maxTokens", comboText(maxTokensCombo));
        s.put("out", comboText(outCombo));
        s.put("provider", comboText(providerCombo));
        s.put("notes", notesArea.getText());
        s.put("dryRun", dryRunBox.isSelected());
        s.put("debug", debugBox.isSelected());
        try {
            Files.createDirectories(SESSION_FILE.getParent());
            Files.writeString(SESSION_FILE, Json.pretty(s));
        } catch (IOException ignored) {
            // best-effort only - worst case the next launch starts blank
        }
    }

    /** Restores whatever saveSession() last wrote, if anything. Silently does nothing on first run. */
    private void loadSession() {
        Object parsed;
        try {
            parsed = Json.parse(Files.readString(SESSION_FILE));
        } catch (IOException e) {
            return;
        }
        Map<String, Object> s = Json.asMap(parsed);

        for (Object f : Json.asList(s.get("files"))) {
            Path p = Paths.get(String.valueOf(f));
            if (Files.exists(p)) addFileToList(p);
        }
        setIfPresent(s, "module", moduleCombo);
        setIfPresent(s, "code", codeCombo);
        setIfPresent(s, "start", startCombo);
        setIfPresent(s, "count", countCombo);
        setIfPresent(s, "project", projectCombo);
        setIfPresent(s, "model", modelCombo);
        setIfPresent(s, "maxTokens", maxTokensCombo);
        setIfPresent(s, "out", outCombo);
        setIfPresent(s, "provider", providerCombo);
        if (s.containsKey("notes")) notesArea.setText(Json.str(s, "notes"));
        dryRunBox.setSelected(Json.bool(s, "dryRun"));
        debugBox.setSelected(Json.bool(s, "debug"));
    }

    private static void setIfPresent(Map<String, Object> s, String key, JComboBox<String> box) {
        String v = Json.str(s, key);
        if (!v.isEmpty()) box.setSelectedItem(v);
    }

    public static void launch() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // fall back to default look and feel
        }
        new Gui().show();
    }

    private void show() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));
        frame.add(header(), BorderLayout.NORTH);

        JSplitPane top = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, filesPanel(), optionsPanel());
        top.setResizeWeight(0.4);
        top.setDividerLocation(420);

        outputTabs.addTab("Log", logPanel());
        outputTabs.addTab("Results", resultsPanel());

        JSplitPane main = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, outputTabs);
        main.setResizeWeight(0.62);

        frame.add(main, BorderLayout.CENTER);
        frame.add(footer(), BorderLayout.SOUTH);

        for (JComboBox<String> box : List.of(moduleCombo, codeCombo, startCombo, countCombo,
                projectCombo, maxTokensCombo, outCombo, modelCombo)) {
            box.setEditable(true);
        }
        moduleCombo.setSelectedIndex(0);
        codeCombo.setSelectedIndex(0);
        startCombo.setSelectedIndex(0);
        countCombo.setSelectedIndex(0);
        projectCombo.setSelectedIndex(0);
        maxTokensCombo.setSelectedIndex(1); // 16000, matches the CLI default
        outCombo.setSelectedIndex(0);
        providerCombo.setSelectedIndex(0);

        migrateOldKeyIfNeeded();
        loadSession(); // overrides the defaults above (including Provider) with whatever was last saved

        // Explicit env var wins outright; otherwise use whichever provider is now selected
        // (session-restored, or Auto) and show that provider's own saved key, if any.
        String envKey = new Config().apiKey;
        String dropdownChoice = comboText(providerCombo);
        Provider initialProvider;
        if (!dropdownChoice.isEmpty() && !dropdownChoice.startsWith("Auto")) {
            initialProvider = Provider.parse(dropdownChoice);
        } else if (!envKey.isEmpty()) {
            initialProvider = Provider.detect(envKey);
        } else {
            initialProvider = firstProviderWithSavedKey();
        }
        keyField.setText(!envKey.isEmpty() ? envKey : loadSavedApiKey(initialProvider));
        if (comboText(modelCombo).isEmpty()) modelCombo.setSelectedItem(initialProvider.defaultModel);

        keyField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateProviderLabel(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateProviderLabel(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateProviderLabel(); }
        });
        providerCombo.addActionListener(e -> {
            updateProviderLabel();
            String choice = comboText(providerCombo);
            if (!choice.isEmpty() && !choice.startsWith("Auto")) {
                String saved = loadSavedApiKey(Provider.parse(choice));
                if (!saved.isEmpty()) keyField.setText(saved);
            }
        });
        updateProviderLabel();
        progressBar.setVisible(false);
        openOutBtn.setEnabled(false);

        frame.setSize(1040, 760);
        frame.setMinimumSize(new Dimension(820, 560));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JComponent header() {
        JLabel title = new JLabel("Drop screenshots, PDFs and notes below, pick a module, "
                + "and click Generate. Everything else already has a sensible default.");
        title.setBorder(new EmptyBorder(10, 12, 0, 12));
        title.setFont(title.getFont().deriveFont(Font.PLAIN, 13f));
        return title;
    }

    private JComponent filesPanel() {
        fileList.setCellRenderer(this::renderFile);
        fileList.setVisibleRowCount(10);
        fileList.setDragEnabled(false);
        fileList.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) removeSelected();
            }
        });

        JScrollPane scroll = new JScrollPane(fileList);
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createDashedBorder(Color.GRAY), "Drag & drop files or folders here",
                TitledBorder.LEADING, TitledBorder.TOP));

        new DropTarget(fileList, new DropTargetAdapter() {
            @Override public void drop(DropTargetDropEvent evt) {
                acceptDrop(evt);
            }
        });
        new DropTarget(scroll, new DropTargetAdapter() {
            @Override public void drop(DropTargetDropEvent evt) {
                acceptDrop(evt);
            }
        });

        JButton browseBtn = new JButton("Browse…");
        browseBtn.addActionListener(e -> browseFiles());
        JButton removeBtn = new JButton("Remove selected");
        removeBtn.addActionListener(e -> removeSelected());
        JButton clearBtn = new JButton("Clear all");
        clearBtn.addActionListener(e -> { fileModel.clear(); saveSession(); });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        buttons.add(browseBtn);
        buttons.add(removeBtn);
        buttons.add(clearBtn);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(0, 12, 0, 6));
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    @SuppressWarnings("unchecked")
    private void acceptDrop(DropTargetDropEvent evt) {
        try {
            evt.acceptDrop(DnDConstants.ACTION_COPY);
            List<File> dropped = (List<File>) evt.getTransferable()
                    .getTransferData(DataFlavor.javaFileListFlavor);
            for (File f : dropped) addFile(f.toPath());
            evt.dropComplete(true);
        } catch (Exception ex) {
            evt.dropComplete(false);
        }
    }

    private void browseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle("Select screenshots, PDFs, notes, or a folder of them");
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            for (File f : chooser.getSelectedFiles()) addFile(f.toPath());
        }
    }

    private void addFile(Path p) {
        addFileToList(p);
        saveSession();
    }

    /** Same as {@link #addFile}, minus the save - so restoring a whole saved session doesn't
     *  write it back out mid-restore, before the rest of the saved fields are in place. */
    private void addFileToList(Path p) {
        if (!fileModel.contains(p)) fileModel.addElement(p);
    }

    private void removeSelected() {
        int[] indices = fileList.getSelectedIndices();
        for (int i = indices.length - 1; i >= 0; i--) fileModel.remove(indices[i]);
        saveSession();
    }

    private Component renderFile(JList<? extends Path> list, Path path, int index,
                                  boolean isSelected, boolean hasFocus) {
        JLabel label = new JLabel();
        boolean dir = Files.isDirectory(path);
        String detail = dir ? "folder" : humanSize(sizeOf(path));
        label.setText((dir ? "📁 " : "📄 ") + path.getFileName() + "   [" + detail + "]");
        label.setOpaque(true);
        label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
        label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
        label.setBorder(new EmptyBorder(2, 6, 2, 6));
        return label;
    }

    private static long sizeOf(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0;
        }
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.0f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / 1048576.0);
    }

    private JComponent optionsPanel() {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

        // --- essentials: everything already has a default except the module pick ---
        JPanel essentials = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        row = addRow(essentials, g, row, "Module", moduleCombo, "pick one, or type your own");

        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        JScrollPane notesScroll = new JScrollPane(notesArea);
        g.gridx = 0; g.gridy = row; g.gridwidth = 1; g.anchor = GridBagConstraints.NORTHWEST;
        essentials.add(new JLabel("Notes"), g);
        g.gridx = 1; g.gridy = row; g.gridwidth = 2; g.weightx = 1; g.weighty = 1;
        g.fill = GridBagConstraints.BOTH;
        essentials.add(notesScroll, g);
        essentials.setAlignmentX(Component.LEFT_ALIGNMENT);

        // --- advanced: already-sensible defaults, collapsed unless you need to change one ---
        JPanel advanced = new JPanel(new GridBagLayout());
        GridBagConstraints ag = new GridBagConstraints();
        ag.insets = new Insets(4, 4, 4, 4);
        ag.fill = GridBagConstraints.HORIZONTAL;
        int arow = 0;
        arow = addRow(advanced, ag, arow, "Code prefix", codeCombo, "or type your own");
        arow = addRow(advanced, ag, arow, "Start #", startCombo, "first case number");
        arow = addRow(advanced, ag, arow, "Target count", countCombo, "no padding regardless");
        arow = addRow(advanced, ag, arow, "Project #", projectCombo, "GitHub Projects number");
        arow = addRow(advanced, ag, arow, "Model", modelCombo, "opus / gpt-5.5 / gemini pro for the hard modules");
        arow = addRow(advanced, ag, arow, "Max tokens", maxTokensCombo, "raise for 80+ case suites");
        arow = addRow(advanced, ag, arow, "Output folder", outCombo, "or type your own path");
        arow = addRow(advanced, ag, arow, "API key", keyField,
                "Anthropic, OpenAI, Gemini, DeepSeek, or OpenRouter");
        arow = addRow(advanced, ag, arow, "Provider", providerCombo,
                "only needed if auto-guess picks the wrong one (e.g. DeepSeek looks like OpenAI)");

        providerLabel.setForeground(Color.GRAY);
        providerLabel.setFont(providerLabel.getFont().deriveFont(11f));
        ag.gridx = 1; ag.gridy = arow; ag.gridwidth = 2; ag.anchor = GridBagConstraints.WEST;
        advanced.add(providerLabel, ag);
        arow++;

        ag.gridx = 1; ag.gridy = arow; ag.gridwidth = 2; ag.anchor = GridBagConstraints.WEST;
        JPanel boxes = new JPanel(new GridLayout(0, 1));
        boxes.add(dryRunBox);
        boxes.add(debugBox);
        advanced.add(boxes, ag);
        advanced.setAlignmentX(Component.LEFT_ALIGNMENT);
        advanced.setVisible(false);

        JButton advancedToggle = new JButton("▸ Advanced options (defaults already set)");
        advancedToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        advancedToggle.addActionListener(e -> {
            boolean show = !advanced.isVisible();
            advanced.setVisible(show);
            advancedToggle.setText((show ? "▾" : "▸") + " Advanced options (defaults already set)");
            container.revalidate();
        });

        container.add(essentials);
        container.add(advancedToggle);
        container.add(advanced);
        container.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(container);
        scroll.setBorder(new EmptyBorder(0, 6, 0, 12));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private int addRow(JPanel form, GridBagConstraints g, int row, String label,
                        JComponent field, String hint) {
        g.gridx = 0; g.gridy = row; g.gridwidth = 1; g.weightx = 0;
        g.anchor = GridBagConstraints.WEST;
        form.add(new JLabel(label), g);

        g.gridx = 1; g.gridy = row; g.weightx = 1;
        form.add(field, g);

        if (!hint.isEmpty()) {
            g.gridx = 2; g.gridy = row; g.weightx = 0;
            JLabel h = new JLabel(hint);
            h.setForeground(Color.GRAY);
            h.setFont(h.getFont().deriveFont(11f));
            form.add(h, g);
        }
        return row + 1;
    }

    private JComponent logPanel() {
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Log"));
        return scroll;
    }

    private JComponent resultsPanel() {
        resultsTable.setAutoCreateRowSorter(true);
        resultsTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(320);
        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = resultsTable.getSelectedRow();
            detailArea.setText(row < 0 ? "" : detailText(rowToCase(row)));
            detailArea.setCaretPosition(0);
        });
        JScrollPane tableScroll = new JScrollPane(resultsTable);

        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setBorder(BorderFactory.createTitledBorder("Details (select a row above)"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailScroll);
        split.setResizeWeight(0.55);
        return split;
    }

    /** The table is sortable, so the visible row index isn't the model/list index. */
    private TestCase rowToCase(int viewRow) {
        return lastCases.get(resultsTable.convertRowIndexToModel(viewRow));
    }

    private static String detailText(TestCase t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.fullTitle).append("\n\n");
        sb.append("Section: ").append(t.section).append("\n");
        sb.append("Preconditions: ").append(t.preconditions).append("\n\n");
        sb.append("Steps:\n").append(t.stepsBlock()).append("\n");
        sb.append("Expected: ").append(t.expected).append("\n\n");
        sb.append("Evidence: ").append(t.evidence);
        return sb.toString();
    }

    private void showResults(List<TestCase> cases) {
        lastCases = cases;
        resultsModel.setRowCount(0);
        for (TestCase t : cases) {
            resultsModel.addRow(new Object[]{t.id, t.titleText, t.type, t.priority, t.tbd ? "TBD" : ""});
        }
        detailArea.setText("");
    }

    private JComponent footer() {
        JPanel panel = new JPanel(new BorderLayout(8, 4));
        panel.setBorder(new EmptyBorder(4, 12, 10, 12));

        generateBtn.addActionListener(e -> onGenerate());
        openOutBtn.addActionListener(e -> openOutputFolder());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(generateBtn);
        buttons.add(openOutBtn);

        progressBar.setIndeterminate(true);
        JPanel status = new JPanel(new BorderLayout(6, 0));
        status.add(progressBar, BorderLayout.WEST);
        status.add(statusLabel, BorderLayout.CENTER);

        panel.add(buttons, BorderLayout.WEST);
        panel.add(status, BorderLayout.CENTER);
        return panel;
    }

    private void updateProviderLabel() {
        String choice = comboText(providerCombo);
        if (!choice.isEmpty() && !choice.startsWith("Auto")) {
            providerLabel.setText("using: " + Provider.parse(choice).label() + " (manually selected)");
            return;
        }
        String key = new String(keyField.getPassword()).trim();
        if (key.isEmpty()) {
            providerLabel.setText(" ");
            return;
        }
        providerLabel.setText("detected: " + Provider.detect(key).label());
    }

    /** Resolves the effective provider: the dropdown wins if set, else guessed from the key. */
    private Provider resolveProvider(String apiKey) {
        String choice = comboText(providerCombo);
        return (!choice.isEmpty() && !choice.startsWith("Auto")) ? Provider.parse(choice) : Provider.detect(apiKey);
    }

    private void openOutputFolder() {
        if (lastOutDir == null) return;
        try {
            Desktop.getDesktop().open(lastOutDir.toFile());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Could not open " + lastOutDir + ":\n" + ex.getMessage(),
                    "Open folder", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onGenerate() {
        if (fileModel.isEmpty()) {
            JOptionPane.showMessageDialog(frame,
                    "Drop or browse at least one screenshot, PDF, or notes file first.",
                    "Nothing to read", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String module = comboText(moduleCombo);
        if (module.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Module name is required, e.g. Categories.",
                    "Module required", JOptionPane.WARNING_MESSAGE);
            moduleCombo.requestFocus();
            return;
        }

        String codeChoice = comboText(codeCombo);
        String outChoice = comboText(outCombo);

        Config c = new Config();
        c.module = module;
        c.code = (codeChoice.isEmpty() || codeChoice.startsWith("(auto"))
                ? Config.deriveCode(module) : codeChoice;
        c.start = parseIntOrDefault(comboText(startCombo), 1);
        String countChoice = comboText(countCombo);
        c.targetCount = countChoice.startsWith("Auto") ? 0 : parseIntOrDefault(countChoice, 0);
        c.projectNumber = parseIntOrDefault(comboText(projectCombo), 24);
        c.model = comboText(modelCombo);
        c.maxTokens = parseIntOrDefault(comboText(maxTokensCombo), 16000);
        c.notes = notesArea.getText();
        c.out = Paths.get(outChoice.isEmpty() ? "out" : outChoice);
        c.dryRun = dryRunBox.isSelected();
        c.debug = debugBox.isSelected();
        String typedKey = new String(keyField.getPassword()).trim();
        if (!typedKey.isEmpty()) c.apiKey = typedKey;
        c.provider = resolveProvider(c.apiKey);
        if (c.model.isEmpty()) c.model = c.provider.defaultModel;
        if (!typedKey.isEmpty()) saveApiKey(c.provider, typedKey); // dedicated file per provider

        if (!c.dryRun && c.apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(frame,
                    "No API key. Paste one into the API key field, or set an *_API_KEY env var and restart.",
                    "API key required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String folderName = JOptionPane.showInputDialog(frame,
                "Name this run's output folder (created under \"" + c.out + "\"):",
                "Output folder name", JOptionPane.PLAIN_MESSAGE);
        if (folderName == null) return; // cancelled
        folderName = sanitizeFolderName(folderName);
        if (folderName.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Folder name can't be empty.",
                    "Output folder name", JOptionPane.WARNING_MESSAGE);
            return;
        }
        c.out = uniqueOutputDir(c.out.resolve(folderName));

        List<Path> roots = Collections.list(fileModel.elements());
        saveSession();

        logArea.setText("");
        showResults(List.of());
        outputTabs.setSelectedIndex(0);
        generateBtn.setEnabled(false);
        openOutBtn.setEnabled(false);
        progressBar.setVisible(true);
        statusLabel.setText("Working…");

        new Worker(c, roots).execute();
    }

    private static String comboText(JComboBox<String> box) {
        Object v = box.getEditor().getItem();
        return v == null ? "" : v.toString().trim();
    }

    private static int parseIntOrDefault(String s, int dflt) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return dflt;
        }
    }

    /** Strips characters Windows won't allow in a folder name. */
    private static String sanitizeFolderName(String s) {
        return s.trim().replaceAll("[\\\\/:*?\"<>|]", "-");
    }

    /** If the requested folder already exists, appends -1, -2, ... until one doesn't. */
    private static Path uniqueOutputDir(Path p) {
        if (!Files.exists(p)) return p;
        Path parent = p.getParent();
        String name = p.getFileName().toString();
        for (int n = 1; ; n++) {
            Path candidate = parent != null ? parent.resolve(name + "-" + n) : Paths.get(name + "-" + n);
            if (!Files.exists(candidate)) return candidate;
        }
    }

    private void appendLog(String line) {
        logArea.append(line + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    /** Runs the existing CLI pipeline off the EDT, streaming its console output into the log. */
    private class Worker extends SwingWorker<List<TestCase>, String> {
        private final Config config;
        private final List<Path> roots;

        Worker(Config config, List<Path> roots) {
            this.config = config;
            this.roots = roots;
        }

        @Override
        protected List<TestCase> doInBackground() throws Exception {
            PrintStream oldOut = System.out;
            PrintStream oldErr = System.err;
            Consumer<String> sink = this::publish;
            PrintStream captured = new PrintStream(new LineOutputStream(sink), true, StandardCharsets.UTF_8);
            System.setOut(captured);
            System.setErr(captured);
            try {
                publish("> reading " + roots.size() + " dropped item(s)");
                Inputs in = Inputs.scan(roots);
                publish("  " + in.summary());
                return Main.execute(config, in);
            } finally {
                System.setOut(oldOut);
                System.setErr(oldErr);
            }
        }

        @Override
        protected void process(List<String> chunks) {
            for (String c : chunks) appendLog(c);
        }

        @Override
        protected void done() {
            generateBtn.setEnabled(true);
            progressBar.setVisible(false);
            try {
                List<TestCase> cases = get();
                lastOutDir = config.out;
                openOutBtn.setEnabled(true);
                statusLabel.setText(config.dryRun ? "Dry run complete." : "Done - see " + config.out);
                if (!cases.isEmpty()) {
                    showResults(cases);
                    outputTabs.setSelectedIndex(1);
                }
            } catch (Exception ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                appendLog("! " + cause.getMessage());
                statusLabel.setText("Failed - see log.");
            }
        }
    }

    /** Buffers bytes into lines and hands each finished line to a sink (used to capture System.out/err). */
    private static class LineOutputStream extends OutputStream {
        private final StringBuilder buf = new StringBuilder();
        private final Consumer<String> sink;

        LineOutputStream(Consumer<String> sink) {
            this.sink = sink;
        }

        @Override public void write(int b) {
            char c = (char) b;
            if (c == '\n') {
                sink.accept(buf.toString());
                buf.setLength(0);
            } else if (c != '\r') {
                buf.append(c);
            }
        }
    }
}
