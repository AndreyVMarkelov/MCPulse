package io.github.mcpsampler.gui;

import io.github.mcpsampler.McpSampler;
import io.github.mcpsampler.transport.HttpSseTransport;
import org.apache.jmeter.samplers.gui.AbstractSamplerGui;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

/**
 * JMeter GUI panel for {@link McpSampler}.
 */
public class McpSamplerGui extends AbstractSamplerGui {

    private static final long serialVersionUID = 1L;

    private JComboBox<String> transportCombo;

    private JTextField commandField;
    private JTextField argsField;

    private JTextField timeoutField;
    private JTextField maxResponseBytesField;
    private JComboBox<String> warmupModeCombo;

    private JTextField clientNameField;
    private JTextField clientVersionField;

    private JComboBox<String> methodCombo;
    private JTextArea rawRequestArea;
    private JPanel rawRequestPanel;

    private JTextField toolNameField;
    private JTextArea toolArgsArea;
    private JPanel toolCallPanel;

    private JComboBox<String> validationModeCombo;
    private JTextField validationExprField;
    private JTextField validationExpectedField;

    private JTextField baseUrlField;
    private JTextField sendPathField;
    private JTextArea headersArea;
    private JComboBox<String> authTypeCombo;
    private JTextField bearerTokenField;
    private JTextField basicUserField;
    private JTextField basicPassField;
    private JComboBox<String> tlsModeCombo;
    private JTextField truststorePathField;
    private JTextField truststorePasswordField;

    private JTextField ssePathField;
    private JTextField sseCorrelationField;
    private JComboBox<String> sseConnectModeCombo;
    private JTextField sseEventFilterField;

    private JCheckBox debugTransportCheck;

    private JPanel processPanel;
    private JPanel httpPanel;
    private JPanel ssePanel;

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
        return "MCP Sampler";
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

        sampler.setTransport((String) transportCombo.getSelectedItem());

        sampler.setCommand(commandField.getText().trim());
        sampler.setArgs(argsField.getText().trim());

        sampler.setClientName(clientNameField.getText().trim());
        sampler.setClientVersion(clientVersionField.getText().trim());

        sampler.setMethod((String) methodCombo.getSelectedItem());
        sampler.setRawRequestJson(rawRequestArea.getText().trim());
        sampler.setToolName(toolNameField.getText().trim());
        sampler.setToolArgsJson(toolArgsArea.getText().trim());

        sampler.setTimeoutMs(parsePositiveInt(timeoutField.getText(), 30_000));
        sampler.setMaxResponseBytes(parsePositiveInt(maxResponseBytesField.getText(), 65_536));
        sampler.setWarmupMode((String) warmupModeCombo.getSelectedItem());

        sampler.setValidationMode((String) validationModeCombo.getSelectedItem());
        sampler.setValidationExpr(validationExprField.getText().trim());
        sampler.setValidationExpected(validationExpectedField.getText().trim());

        sampler.setHttpBaseUrl(baseUrlField.getText().trim());
        sampler.setHttpSendPath(sendPathField.getText().trim());
        sampler.setHttpHeaders(headersArea.getText().trim());
        sampler.setHttpAuthType((String) authTypeCombo.getSelectedItem());
        sampler.setHttpBearerToken(bearerTokenField.getText().trim());
        sampler.setHttpBasicUser(basicUserField.getText().trim());
        sampler.setHttpBasicPass(basicPassField.getText().trim());
        sampler.setHttpTlsMode((String) tlsModeCombo.getSelectedItem());
        sampler.setHttpTruststorePath(truststorePathField.getText().trim());
        sampler.setHttpTruststorePassword(truststorePasswordField.getText().trim());

        sampler.setSsePath(ssePathField.getText().trim());
        sampler.setSseCorrelationKey(sseCorrelationField.getText().trim());
        sampler.setSseConnectMode((String) sseConnectModeCombo.getSelectedItem());
        sampler.setSseEventFilter(sseEventFilterField.getText().trim());

        sampler.setDebugTransport(debugTransportCheck.isSelected());
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);
        McpSampler sampler = (McpSampler) element;

        transportCombo.setSelectedItem(sampler.getTransport());

        commandField.setText(sampler.getCommand());
        argsField.setText(sampler.getArgs());

        clientNameField.setText(sampler.getClientName());
        clientVersionField.setText(sampler.getClientVersion());

        methodCombo.setSelectedItem(sampler.getMethod());
        rawRequestArea.setText(sampler.getRawRequestJson());
        toolNameField.setText(sampler.getToolName());
        toolArgsArea.setText(sampler.getToolArgsJson());

        timeoutField.setText(Integer.toString(sampler.getTimeoutMs()));
        maxResponseBytesField.setText(Integer.toString(sampler.getMaxResponseBytes()));
        warmupModeCombo.setSelectedItem(sampler.getWarmupMode());

        validationModeCombo.setSelectedItem(sampler.getValidationMode());
        validationExprField.setText(sampler.getValidationExpr());
        validationExpectedField.setText(sampler.getValidationExpected());

        baseUrlField.setText(sampler.getHttpBaseUrl());
        sendPathField.setText(sampler.getHttpSendPath());
        headersArea.setText(sampler.getHttpHeaders());
        authTypeCombo.setSelectedItem(sampler.getHttpAuthType());
        bearerTokenField.setText(sampler.getHttpBearerToken());
        basicUserField.setText(sampler.getHttpBasicUser());
        basicPassField.setText(sampler.getHttpBasicPass());
        tlsModeCombo.setSelectedItem(sampler.getHttpTlsMode());
        truststorePathField.setText(sampler.getHttpTruststorePath());
        truststorePasswordField.setText(sampler.getHttpTruststorePassword());

        ssePathField.setText(sampler.getSsePath());
        sseCorrelationField.setText(sampler.getSseCorrelationKey());
        sseConnectModeCombo.setSelectedItem(sampler.getSseConnectMode());
        sseEventFilterField.setText(sampler.getSseEventFilter());

        debugTransportCheck.setSelected(sampler.isDebugTransport());

        updateTransportVisibility();
        updateMethodVisibility();
        updateAuthVisibility();
        updateTlsVisibility();
    }

    @Override
    public void clearGui() {
        super.clearGui();

        transportCombo.setSelectedItem(McpSampler.TRANSPORT_STDIO);

        commandField.setText("uvx");
        argsField.setText("");

        clientNameField.setText("jmeter-mcp-sampler");
        clientVersionField.setText("1.0.0");

        methodCombo.setSelectedItem(McpSampler.METHOD_TOOLS_LIST);
        rawRequestArea.setText("{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\"}");
        toolNameField.setText("");
        toolArgsArea.setText("{}");

        timeoutField.setText("30000");
        maxResponseBytesField.setText("65536");
        warmupModeCombo.setSelectedItem(McpSampler.WARMUP_NONE);

        validationModeCombo.setSelectedItem(McpSampler.VALIDATION_NONE);
        validationExprField.setText("");
        validationExpectedField.setText("");

        baseUrlField.setText("http://localhost:8080");
        sendPathField.setText("/rpc");
        headersArea.setText("");
        authTypeCombo.setSelectedItem(McpSampler.AUTH_NONE);
        bearerTokenField.setText("");
        basicUserField.setText("");
        basicPassField.setText("");
        tlsModeCombo.setSelectedItem(McpSampler.TLS_SYSTEM);
        truststorePathField.setText("");
        truststorePasswordField.setText("");

        ssePathField.setText("/events");
        sseCorrelationField.setText("id");
        sseConnectModeCombo.setSelectedItem(HttpSseTransport.CONNECT_PER_THREAD);
        sseEventFilterField.setText("");

        debugTransportCheck.setSelected(false);

        updateTransportVisibility();
        updateMethodVisibility();
        updateAuthVisibility();
        updateTlsVisibility();
    }

    private void init() {
        setLayout(new BorderLayout());
        setBorder(makeBorder());
        add(makeTitlePanel(), BorderLayout.NORTH);

        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = defaultGbc();

        mainPanel.add(buildTransportPanel(), gbc);
        gbc.gridy++;

        processPanel = buildProcessPanel();
        mainPanel.add(processPanel, gbc);
        gbc.gridy++;

        httpPanel = buildHttpPanel();
        mainPanel.add(httpPanel, gbc);
        gbc.gridy++;

        ssePanel = buildSsePanel();
        mainPanel.add(ssePanel, gbc);
        gbc.gridy++;

        mainPanel.add(buildClientPanel(), gbc);
        gbc.gridy++;

        mainPanel.add(buildRequestPanel(), gbc);
        gbc.gridy++;

        rawRequestPanel = buildRawRequestPanel();
        mainPanel.add(rawRequestPanel, gbc);
        gbc.gridy++;

        toolCallPanel = buildToolCallPanel();
        mainPanel.add(toolCallPanel, gbc);
        gbc.gridy++;

        mainPanel.add(buildAdvancedPanel(), gbc);
        gbc.gridy++;

        gbc.weighty = 1.0;
        mainPanel.add(new JPanel(), gbc);

        add(mainPanel, BorderLayout.CENTER);

        updateTransportVisibility();
        updateMethodVisibility();
        updateAuthVisibility();
        updateTlsVisibility();
    }

    private JPanel buildTransportPanel() {
        JPanel panel = titledPanel("Transport");
        panel.setLayout(new FlowLayout(FlowLayout.LEFT));

        panel.add(new JLabel("Transport:"));
        transportCombo = new JComboBox<>(new String[]{
                McpSampler.TRANSPORT_STDIO,
                McpSampler.TRANSPORT_HTTP,
                McpSampler.TRANSPORT_HTTP_SSE
        });
        transportCombo.setSelectedItem(McpSampler.TRANSPORT_STDIO);
        transportCombo.addActionListener(e -> updateTransportVisibility());
        panel.add(transportCombo);

        return panel;
    }

    private JPanel buildProcessPanel() {
        JPanel panel = titledPanel("STDIO Process");
        panel.setLayout(new GridLayout(2, 2, 4, 4));

        panel.add(new JLabel("Command:"));
        commandField = new JTextField("uvx");
        panel.add(commandField);

        panel.add(new JLabel("Arguments (space-separated):"));
        argsField = new JTextField();
        panel.add(argsField);

        return panel;
    }

    private JPanel buildHttpPanel() {
        JPanel panel = titledPanel("HTTP Settings");
        panel.setLayout(new BorderLayout(4, 4));

        JPanel grid = new JPanel(new GridLayout(8, 2, 4, 4));
        grid.add(new JLabel("Base URL:"));
        baseUrlField = new JTextField("http://localhost:8080");
        grid.add(baseUrlField);

        grid.add(new JLabel("Send path:"));
        sendPathField = new JTextField("/rpc");
        grid.add(sendPathField);

        grid.add(new JLabel("Auth type:"));
        authTypeCombo = new JComboBox<>(new String[]{
                McpSampler.AUTH_NONE,
                McpSampler.AUTH_BEARER,
                McpSampler.AUTH_BASIC
        });
        authTypeCombo.setSelectedItem(McpSampler.AUTH_NONE);
        authTypeCombo.addActionListener(e -> updateAuthVisibility());
        grid.add(authTypeCombo);

        grid.add(new JLabel("TLS mode:"));
        tlsModeCombo = new JComboBox<>(new String[]{
                McpSampler.TLS_SYSTEM,
                McpSampler.TLS_TRUST_ALL,
                McpSampler.TLS_TRUSTSTORE
        });
        tlsModeCombo.setSelectedItem(McpSampler.TLS_SYSTEM);
        tlsModeCombo.addActionListener(e -> updateTlsVisibility());
        grid.add(tlsModeCombo);

        grid.add(new JLabel("Truststore path:"));
        truststorePathField = new JTextField();
        grid.add(truststorePathField);

        grid.add(new JLabel("Truststore password:"));
        truststorePasswordField = new JTextField();
        grid.add(truststorePasswordField);

        grid.add(new JLabel("Bearer token:"));
        bearerTokenField = new JTextField();
        grid.add(bearerTokenField);

        grid.add(new JLabel("Basic username:"));
        basicUserField = new JTextField();
        grid.add(basicUserField);

        grid.add(new JLabel("Basic password:"));
        basicPassField = new JTextField();
        grid.add(basicPassField);

        panel.add(grid, BorderLayout.NORTH);

        headersArea = new JTextArea(4, 40);
        headersArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JPanel headersPanel = new JPanel(new BorderLayout(4, 4));
        headersPanel.add(new JLabel("Headers (one per line: Key: Value):"), BorderLayout.NORTH);
        headersPanel.add(new JScrollPane(headersArea), BorderLayout.CENTER);
        panel.add(headersPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildSsePanel() {
        JPanel panel = titledPanel("SSE Settings");
        panel.setLayout(new GridLayout(4, 2, 4, 4));

        panel.add(new JLabel("SSE endpoint path:"));
        ssePathField = new JTextField("/events");
        panel.add(ssePathField);

        panel.add(new JLabel("Correlation key:"));
        sseCorrelationField = new JTextField("id");
        panel.add(sseCorrelationField);

        panel.add(new JLabel("Connect mode:"));
        sseConnectModeCombo = new JComboBox<>(new String[]{
                HttpSseTransport.CONNECT_PER_SAMPLE,
                HttpSseTransport.CONNECT_PER_THREAD
        });
        sseConnectModeCombo.setSelectedItem(HttpSseTransport.CONNECT_PER_THREAD);
        panel.add(sseConnectModeCombo);

        panel.add(new JLabel("Event filter (exact or prefix*):"));
        sseEventFilterField = new JTextField();
        panel.add(sseEventFilterField);

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

    private JPanel buildRequestPanel() {
        JPanel panel = titledPanel("Request");
        panel.setLayout(new GridLayout(5, 2, 4, 4));

        panel.add(new JLabel("MCP Method:"));
        methodCombo = new JComboBox<>(new String[]{
                McpSampler.METHOD_INITIALIZE,
                McpSampler.METHOD_TOOLS_LIST,
                McpSampler.METHOD_TOOLS_CALL,
                McpSampler.METHOD_RESOURCES_LIST,
                McpSampler.METHOD_RAW_JSON
        });
        methodCombo.setSelectedItem(McpSampler.METHOD_TOOLS_LIST);
        methodCombo.addActionListener(e -> updateMethodVisibility());
        panel.add(methodCombo);

        panel.add(new JLabel("Response timeout (ms):"));
        timeoutField = new JTextField("30000");
        panel.add(timeoutField);

        panel.add(new JLabel("Max response bytes:"));
        maxResponseBytesField = new JTextField("65536");
        panel.add(maxResponseBytesField);

        panel.add(new JLabel("Warm-up mode:"));
        warmupModeCombo = new JComboBox<>(new String[]{
                McpSampler.WARMUP_NONE,
                McpSampler.WARMUP_PROCESS,
                McpSampler.WARMUP_INITIALIZE
        });
        warmupModeCombo.setSelectedItem(McpSampler.WARMUP_NONE);
        panel.add(warmupModeCombo);

        panel.add(new JLabel("Transport debug logging:"));
        debugTransportCheck = new JCheckBox();
        panel.add(debugTransportCheck);

        return panel;
    }

    private JPanel buildRawRequestPanel() {
        JPanel panel = titledPanel("Raw JSON Request");
        panel.setLayout(new BorderLayout(4, 4));

        rawRequestArea = new JTextArea(6, 40);
        rawRequestArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        rawRequestArea.setText("{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\"}");

        panel.add(new JScrollPane(rawRequestArea), BorderLayout.CENTER);
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

        return panel;
    }

    private JPanel buildAdvancedPanel() {
        JPanel panel = titledPanel("Response Validation");
        panel.setLayout(new GridLayout(3, 2, 4, 4));

        panel.add(new JLabel("Validation mode:"));
        validationModeCombo = new JComboBox<>(new String[]{
                McpSampler.VALIDATION_NONE,
                McpSampler.VALIDATION_REGEX,
                McpSampler.VALIDATION_JSONPATH,
                McpSampler.VALIDATION_EQUALS
        });
        validationModeCombo.setSelectedItem(McpSampler.VALIDATION_NONE);
        panel.add(validationModeCombo);

        panel.add(new JLabel("Expression (regex / jsonpath):"));
        validationExprField = new JTextField();
        panel.add(validationExprField);

        panel.add(new JLabel("Expected (equals/jsonpath optional):"));
        validationExpectedField = new JTextField();
        panel.add(validationExpectedField);

        return panel;
    }

    private void updateTransportVisibility() {
        String transport = (String) transportCombo.getSelectedItem();
        boolean isStdio = McpSampler.TRANSPORT_STDIO.equals(transport);
        boolean isHttp = McpSampler.TRANSPORT_HTTP.equals(transport);
        boolean isHttpSse = McpSampler.TRANSPORT_HTTP_SSE.equals(transport);

        processPanel.setVisible(isStdio);
        httpPanel.setVisible(isHttp || isHttpSse);
        ssePanel.setVisible(isHttpSse);

        revalidate();
        repaint();
    }

    private void updateMethodVisibility() {
        String method = (String) methodCombo.getSelectedItem();
        boolean showToolCall = McpSampler.METHOD_TOOLS_CALL.equals(method);
        boolean showRaw = McpSampler.METHOD_RAW_JSON.equals(method);

        toolCallPanel.setVisible(showToolCall);
        rawRequestPanel.setVisible(showRaw);

        revalidate();
        repaint();
    }

    private void updateAuthVisibility() {
        String authType = (String) authTypeCombo.getSelectedItem();
        boolean showBearer = McpSampler.AUTH_BEARER.equals(authType);
        boolean showBasic = McpSampler.AUTH_BASIC.equals(authType);

        bearerTokenField.setEnabled(showBearer);
        basicUserField.setEnabled(showBasic);
        basicPassField.setEnabled(showBasic);
    }

    private void updateTlsVisibility() {
        String tlsMode = (String) tlsModeCombo.getSelectedItem();
        boolean showTruststore = McpSampler.TLS_TRUSTSTORE.equals(tlsMode);

        truststorePathField.setEnabled(showTruststore);
        truststorePasswordField.setEnabled(showTruststore);
    }

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

    private int parsePositiveInt(String raw, int fallback) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
