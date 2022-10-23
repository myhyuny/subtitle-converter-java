package myhyuny.subtitle;

import static java.awt.EventQueue.invokeLater;
import static java.lang.Math.round;
import static java.nio.charset.Charset.availableCharsets;
import static java.util.Collections.singletonList;
import static java.util.concurrent.ForkJoinPool.commonPool;
import static java.util.stream.Collectors.toList;
import static myhyuny.subtitle.Converter.LINE_DELIMITER_UNIX;
import static myhyuny.subtitle.Converter.LINE_DELIMITER_WINDOWS;
import static myhyuny.subtitle.Converter.PATTERN_FILE_EXTENSION;
import static myhyuny.subtitle.Subtitle.TYPE_SAMI;
import static myhyuny.subtitle.Subtitle.TYPE_SUB_RIP;

import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Choice;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.Panel;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MainFrame
 *
 * @author Hyunmin Kang
 */
public class MainFrame extends Frame {

    private static final String CHARSET_AUTO = "Auto (Unicode Or System Default)";
    private static final String OUTPUT_TYPE_SAMI = "SAMI (smi)";
    private static final String OUTPUT_TYPE_SUB_RIP = "SubRip (srt)";

    private static final String LINE_DELIMITER_TYPE_UNIX = "Unix";
    private static final String LINE_DELIMITER_TYPE_WINDOWS = "Windows";

    private static final int MENU_LINES = 5;

    private static final Pattern PATTERN_SYNC = Pattern.compile("-?\\d+\\.?\\d*");

    private final Preferences preferences = Preferences.userNodeForPackage(getClass());

    private final MenuItem menuItemOpenFile = new MenuItem("Open File...", new MenuShortcut(KeyEvent.VK_O));

    private final JLabel outputTypeLabel = new JLabel("Output Type", JLabel.RIGHT);
    private final JLabel inputCharsetLabel = new JLabel("Input Charset", JLabel.RIGHT);
    private final JLabel outputCharsetLabel = new JLabel("Output Charset", JLabel.RIGHT);
    private final JLabel lineDelimiterLabel = new JLabel("Line Delimiter", JLabel.RIGHT);
    private final JLabel syncLabel = new JLabel("Sync", JLabel.RIGHT);

    private final Choice outTypeChoice = new Choice();
    private final Choice inputCharsetChoice = new Choice();
    private final Choice outputCharsetChoice = new Choice();
    private final Choice lineDelimiterChoice = new Choice();
    private final JTextField syncTextField = new JTextField("0.0 sec");

    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel statusLabel = new JLabel("https://github.com/myhyuny/subtitle-converter-java");

    private volatile boolean run = false;

    @SuppressWarnings("unused")
    private final DropTarget dropTarget = new DropTarget(this, DnDConstants.ACTION_REFERENCE, new DropTargetAdapter() {
        @Override
        public void drop(DropTargetDropEvent e) {
            if (run || (e.getDropAction() & DnDConstants.ACTION_MOVE) == 0) {
                e.dropComplete(false);
                return;
            }

            e.acceptDrop(e.getDropAction());

            try {
                @SuppressWarnings("unchecked")
                List<File> list = (List<File>) e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                convert(list);
                e.dropComplete(true);
            } catch (UnsupportedFlavorException | IOException exception) {
                exception.printStackTrace();
            }
        }
    });

    public MainFrame() {
        setTitle("Subtitle Converter");
        setSize(320, 200);
        setResizable(false);
        setLayout(new BorderLayout());

        outTypeChoice.add(OUTPUT_TYPE_SAMI);
        outTypeChoice.add(OUTPUT_TYPE_SUB_RIP);
        inputCharsetChoice.add(CHARSET_AUTO);
        for (Map.Entry<String, Charset> entry : availableCharsets().entrySet()) {
            inputCharsetChoice.add(entry.getKey());
            outputCharsetChoice.add(entry.getKey());
        }
        outputCharsetChoice.select("UTF-8");
        lineDelimiterChoice.add("Default");
        lineDelimiterChoice.add(LINE_DELIMITER_TYPE_UNIX);
        lineDelimiterChoice.add(LINE_DELIMITER_TYPE_WINDOWS);

        outTypeChoice.select(preferences.get("OutputType", OUTPUT_TYPE_SUB_RIP));
        inputCharsetChoice.select(preferences.get("InputCharset", CHARSET_AUTO));
        outputCharsetChoice.select(preferences.get("OutputCharset", OUTPUT_TYPE_SUB_RIP));
        lineDelimiterChoice.select(preferences.get("LineDelimiter", "Default"));

        MenuItem menuItemClose = new MenuItem("Close", new MenuShortcut(KeyEvent.VK_W));
        MenuItem menuItemExit = new MenuItem("Exit");

        Menu fileMenu = new Menu("File");
        fileMenu.add(menuItemOpenFile);
        fileMenu.addSeparator();
        fileMenu.add(menuItemClose);
        fileMenu.addSeparator();
        fileMenu.add(menuItemExit);

        MenuBar menuBar = new MenuBar();
        menuBar.add(fileMenu);
        setMenuBar(menuBar);

        Panel labelPanel = new Panel(new GridLayout(MENU_LINES, 1));
        labelPanel.add(outputTypeLabel);
        labelPanel.add(inputCharsetLabel);
        labelPanel.add(outputCharsetLabel);
        labelPanel.add(lineDelimiterLabel);
        labelPanel.add(syncLabel);

        Panel centerPanel = new Panel(new BorderLayout(10, 0));
        centerPanel.add(labelPanel, BorderLayout.WEST);

        Panel inputPanel = new Panel(new GridLayout(MENU_LINES, 1));
        inputPanel.add(outTypeChoice);
        inputPanel.add(inputCharsetChoice);
        inputPanel.add(outputCharsetChoice);
        inputPanel.add(lineDelimiterChoice);
        inputPanel.add(syncTextField);
        centerPanel.add(inputPanel, BorderLayout.CENTER);

        Panel southSanel = new Panel(new BorderLayout(10, 0));
        southSanel.add(progressBar, BorderLayout.WEST);
        southSanel.add(statusLabel, BorderLayout.CENTER);

        add(new Panel(), BorderLayout.NORTH);
        add(new Panel(), BorderLayout.EAST);
        add(new Panel(), BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);
        add(southSanel, BorderLayout.SOUTH);

        Dimension dimension = getToolkit().getScreenSize();
        int x = preferences.getInt("WindowBoundsX", round(dimension.width / 2.f - getWidth() / 2.f));
        int y = preferences.getInt("WindowBoundsY", round(dimension.height / 2.f - getHeight() / 2.f));
        setBounds(x, y, getWidth(), getHeight());

        menuItemOpenFile.addActionListener((e) -> {
            FileDialog dialog = new FileDialog(this);
            dialog.setMode(FileDialog.LOAD);
            dialog.setFilenameFilter((dir, name) -> PATTERN_FILE_EXTENSION.matcher(name).find());
            dialog.setVisible(true);
            if (dialog.getFile() == null) {
                return;
            }
            dialog.setVisible(false);
            convert(singletonList(new File(dialog.getDirectory(), dialog.getFile())));
            dialog.removeAll();
            dialog.dispose();
        });
        menuItemClose.addActionListener((e) -> exit());
        menuItemExit.addActionListener((e) -> exit());

        syncTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (e.getSource() == syncTextField) {
                    syncTextField.setText(parseFloatString(syncTextField.getText()));
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (e.getSource() == syncTextField) {
                    syncTextField.setText(parseFloatString(syncTextField.getText()) + " sec");
                }
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });
    }

    @Override
    public void setEnabled(boolean b) {
        menuItemOpenFile.setEnabled(b);

        outputTypeLabel.setEnabled(b);
        inputCharsetLabel.setEnabled(b);
        outputCharsetLabel.setEnabled(b);
        lineDelimiterLabel.setEnabled(b);
        syncLabel.setEnabled(b);

        outTypeChoice.setEnabled(b);
        inputCharsetChoice.setEnabled(b);
        outputCharsetChoice.setEnabled(b);
        lineDelimiterChoice.setEnabled(b);
        syncTextField.setEnabled(b);

        super.setEnabled(b);
    }

    private Charset charset(String charsetName) {
        if (CHARSET_AUTO.equals(charsetName)) {
            return null;
        }
        return Charset.forName(charsetName);
    }

    private String lineDelimiterType(String name) {
        switch (name) {
            case LINE_DELIMITER_TYPE_WINDOWS:
                return LINE_DELIMITER_WINDOWS;
            case LINE_DELIMITER_TYPE_UNIX:
            default:
                return LINE_DELIMITER_UNIX;
        }
    }

    private int outputType(String type) {
        switch (type) {
            case OUTPUT_TYPE_SAMI:
                return TYPE_SAMI;
            case OUTPUT_TYPE_SUB_RIP:
                return TYPE_SUB_RIP;
        }
        return 0;
    }

    private float parseFloat(String str) {
        Matcher matcher = PATTERN_SYNC.matcher(str);
        return matcher.find()
            ? Float.parseFloat(matcher.group())
            : 0;
    }

    private String parseFloatString(String str) {
        return String.valueOf(parseFloat(str));
    }

    private void convert(List<File> files) {
        List<File> list = files.stream()
            .filter(file -> PATTERN_FILE_EXTENSION.matcher(file.getName()).find() && file.isFile())
            .collect(toList());

        run = true;
        setEnabled(false);
        syncTextField.transferFocus();
        progressBar.setValue(0);
        progressBar.setMaximum(list.size());
        statusLabel.setText("Converting");

        Charset inputCharset = charset(inputCharsetChoice.getSelectedItem());
        Charset outputCharset = charset(outputCharsetChoice.getSelectedItem());
        String lineDelimiter = lineDelimiterType(lineDelimiterChoice.getSelectedItem());
        int outputType = outputType(outTypeChoice.getSelectedItem());
        long sync = (long) (parseFloat(syncTextField.getText()) * 1000);

        commonPool().execute(() -> {
            String message = "Success";
            try {
                list.parallelStream().forEach((file) -> {
                    new Converter(file)
                        .setInputCharset(inputCharset)
                        .setOutputCharset(outputCharset)
                        .setLineDelimiter(lineDelimiter)
                        .setSync(sync)
                        .write(outputType);
                    invokeLater(() -> progressBar.setValue(progressBar.getValue() + 1));
                });
            } catch (SubtitleException e) {
                e.printStackTrace();
                message = "Error " + e.getMessage();
            }

            String m = message;
            invokeLater(() -> {
                progressBar.setValue(0);
                statusLabel.setText(m);
                setEnabled(true);
                run = false;
            });
        });
    }

    public void exit() {
        try {
            preferences.put("OutputType", outTypeChoice.getSelectedItem());
            preferences.put("InputCharset", inputCharsetChoice.getSelectedItem());
            preferences.put("OutputCharset", outputCharsetChoice.getSelectedItem());
            preferences.put("LineDelimiter", lineDelimiterChoice.getSelectedItem());
            preferences.putInt("WindowBoundsX", getX());
            preferences.putInt("WindowBoundsY", getY());
            preferences.flush();
        } catch (BackingStoreException e) {
            e.printStackTrace();
        }
        removeAll();
        dispose();
        System.exit(0);
    }

}
