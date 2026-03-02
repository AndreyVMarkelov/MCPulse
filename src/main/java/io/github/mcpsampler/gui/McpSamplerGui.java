package io.github.mcpsampler.gui;

import io.github.mcpsampler.McpSampler;
import org.apache.jmeter.samplers.gui.AbstractSamplerGui;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * JMeter GUI panel for {@link McpSampler}.
 *
 * <p>Shown in the JMeter "Add > Sampler" menu as "MCP Sampler (stdio)".</p>
 */
public class McpSamplerGui extends AbstractSamplerGui {

    private static final long serialVersionUID = 1L;

    // Process config
    private JTextField commandField;
    private JTextField argsField;
    private JTextField timeoutField;

    // Client config
    private JTextField clientNameField;
    private JTextField clientVersionField;

    // Method
    private JComboBox<String> methodCombo;

    // tools/call specific
    private JTextField toolNameField;
    private JTextArea toolArgsArea;
    private JPanel toolCallPanel;

    public McpSamplerGui() {
        super();
        init();
    }

    @Override
    public String getLabelResource() {
        return "mcp_sampler_title";
    }

    @Override
    public String getStaticLabel() {
        return "MCP Sampler (stdio)";
    }

    @Override
    public TestElement createTestElement() {
        McpSampler sampler = new McpSampler();
        modifyTestElement(sampler);
        return sampler;
    }

    @Override
    public void modifyTestElement(TestElement element) {
        super.configureTestElement(element);
        McpSampler sampler = (McpSampler) element;
        sampler.setCommand(commandField.getText().trim());
        sampler.setArgs(argsField.getText().trim());
        sampler.setClientName(clientNameField.getText().trim());
        sampler.setClientVersion(clientVersionField.getText().trim());
        sampler.setMethod((String) methodCombo.getSelectedItem());
        sampler.setToolName(toolNameField.getText().trim());
        sampler.setToolArgsJson(toolArgsArea.getText().trim());
        sampler.setTimeoutMs(parseTimeoutMs(timeoutField.getText()));
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);
        McpSampler sampler = (McpSampler) element;
        commandField.setText(sampler.getCommand());
        argsField.setText(sampler.getArgs());
        clientNameField.setText(sampler.getClientName());
        clientVersionField.setText(sampler.getClientVersion());
        methodCombo.setSelectedItem(sampler.getMethod());
        toolNameField.setText(sampler.getToolName());
        toolArgsArea.setText(sampler.getToolArgsJson());
        timeoutField.setText(Integer.toString(sampler.getTimeoutMs()));
        updateToolCallVisibility((String) methodCombo.getSelectedItem());
    }

    @Override
    public void clearGui() {
        super.clearGui();
        commandField.setText("uvx");
        argsField.setText("");
        clientNameField.setText("jmeter-mcp-sampler");
        clientVersionField.setText("1.0.0");
        methodCombo.setSelectedItem(McpSampler.METHOD_TOOLS_LIST);
        toolNameField.setText("");
        toolArgsArea.setText("{}");
        timeoutField.setText("30000");
        updateToolCallVisibility(McpSampler.METHOD_TOOLS_LIST);
    }

    // =========================================================================
    // UI construction
    // =========================================================================

    private void init() {
        setLayout(new BorderLayout());
        setBorder(makeBorder());
        add(makeTitlePanel(), BorderLayout.NORTH);

        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = defaultGbc();

        // --- Server process section ---
        mainPanel.add(buildProcessPanel(), gbc);
        gbc.gridy++;

        // --- Client info section ---
        mainPanel.add(buildClientPanel(), gbc);
        gbc.gridy++;

        // --- Method section ---
        mainPanel.add(buildMethodPanel(), gbc);
        gbc.gridy++;

        // --- tools/call args ---
        toolCallPanel = buildToolCallPanel();
        mainPanel.add(toolCallPanel, gbc);
        gbc.gridy++;

        // Filler
        gbc.weighty = 1.0;
        mainPanel.add(new JPanel(), gbc);

        add(mainPanel, BorderLayout.CENTER);
    }

    private JPanel buildProcessPanel() {
        JPanel panel = titledPanel("MCP Server Process");
        panel.setLayout(new GridLayout(3, 2, 4, 4));

        panel.add(new JLabel("Command:"));
        commandField = new JTextField("uvx");
        panel.add(commandField);

        panel.add(new JLabel("Arguments (space-separated):"));
        argsField = new JTextField();
        argsField.setToolTipText("e.g.  mcp-server-fetch   or   /path/to/server.py");
        panel.add(argsField);

        panel.add(new JLabel("Response timeout (ms):"));
        timeoutField = new JTextField("30000");
        timeoutField.setToolTipText("Maximum wait for one MCP response before failing");
        panel.add(timeoutField);

        return panel;
    }

    private JPanel buildClientPanel() {
        JPanel panel = titledPanel("MCP Client Identity");
        panel.setLayout(new GridLayout(2, 2, 4, 4));

        panel.add(new JLabel("Client name:"));
        clientNameField = new JTextField("jmeter-mcp-sampler");
        panel.add(clientNameField);

        panel.add(new JLabel("Client version:"));
        clientVersionField = new JTextField("1.0.0");
        panel.add(clientVersionField);

        return panel;
    }

    private JPanel buildMethodPanel() {
        JPanel panel = titledPanel("Request");
        panel.setLayout(new FlowLayout(FlowLayout.LEFT));

        panel.add(new JLabel("MCP Method:"));
        methodCombo = new JComboBox<>(new String[]{
                McpSampler.METHOD_INITIALIZE,
                McpSampler.METHOD_TOOLS_LIST,
                McpSampler.METHOD_TOOLS_CALL,
                McpSampler.METHOD_RESOURCES_LIST
        });
        methodCombo.setSelectedItem(McpSampler.METHOD_TOOLS_LIST);
        methodCombo.addActionListener(e ->
                updateToolCallVisibility((String) methodCombo.getSelectedItem()));
        panel.add(methodCombo);

        return panel;
    }

    private JPanel buildToolCallPanel() {
        JPanel panel = titledPanel("tools/call Options");
        panel.setLayout(new BorderLayout(4, 4));

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topRow.add(new JLabel("Tool name:"));
        toolNameField = new JTextField(20);
        topRow.add(toolNameField);
        panel.add(topRow, BorderLayout.NORTH);

        toolArgsArea = new JTextArea(5, 40);
        toolArgsArea.setText("{}");
        toolArgsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        panel.add(new JLabel("Arguments (JSON):"), BorderLayout.CENTER);
        panel.add(new JScrollPane(toolArgsArea), BorderLayout.SOUTH);

        panel.setVisible(false);
        return panel;
    }

    private void updateToolCallVisibility(String method) {
        boolean show = McpSampler.METHOD_TOOLS_CALL.equals(method);
        if (toolCallPanel != null) {
            toolCallPanel.setVisible(show);
            revalidate();
            repaint();
        }
    }

    // =========================================================================
    // Layout helpers
    // =========================================================================

    private GridBagConstraints defaultGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);
        return gbc;
    }

    private JPanel titledPanel(String title) {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), title,
                TitledBorder.LEFT, TitledBorder.TOP));
        return panel;
    }

    private int parseTimeoutMs(String raw) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : 30_000;
        } catch (Exception ignored) {
            return 30_000;
        }
    }
}
