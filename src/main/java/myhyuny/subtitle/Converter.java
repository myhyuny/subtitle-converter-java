package myhyuny.subtitle;

import static java.lang.Long.parseLong;
import static java.nio.charset.StandardCharsets.UTF_16BE;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newInputStream;
import static java.nio.file.Files.newOutputStream;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.DOTALL;
import static myhyuny.subtitle.Subtitle.TYPE_SAMI;
import static myhyuny.subtitle.Subtitle.TYPE_SUB_RIP;
import static myhyuny.subtitle.io.IO.readAll;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converter
 *
 * @author Hyunmin Kang
 */
public class Converter {

    private static final Pattern PATTERN_EXTENSION = Pattern.compile("[^.]+$");
    private static final Pattern PATTERN_SUB_RIP = Pattern.compile("\\s{2,}\\d+\\s+");
    private static final Pattern PATTERN_SUB_RIP_DATA = Pattern.compile(
        "(\\d{2}:\\d{2}:\\d{2},\\d{1,3})\\s+-->\\s+(\\d{2}:\\d{2}:\\d{2},\\d{1,3})\\s+(.+)", DOTALL
    );
    private static final Pattern PATTERN_SAMI = Pattern.compile("\\s*<sync\\s+", CASE_INSENSITIVE);
    private static final Pattern PATTERN_SAMI_DATA = Pattern.compile(
        "start=['\"]?(\\d+)['\"]?\\s*[^>]*>\\s*(.*)\\s*", CASE_INSENSITIVE | DOTALL
    );
    static final Pattern PATTERN_SAMI_NEW_LINE_TAG = Pattern.compile("<br[^>]*/?>", CASE_INSENSITIVE);
    static final Pattern PATTERN_SAMI_TAG = Pattern.compile("</?\\w+\\s*[^>]*\\s*/?>");
    static final Pattern PATTERN_NEW_LINE = Pattern.compile("\\n");
    static final Pattern PATTERN_LEFT_TRIM = Pattern.compile("\\n\\s+");
    static final Pattern PATTERN_RIGHT_TRIM = Pattern.compile("\\s+\\n");
    static final Pattern PATTERN_SPACE = Pattern.compile("[\t 　]+");
    private static final Pattern PATTERN_COMMENTS = Pattern.compile("<!--.*?-->", DOTALL);

    static final Pattern PATTERN_FILE_EXTENSION = Pattern.compile("\\.(sa?mi|srt)$", CASE_INSENSITIVE);
    private static final String FILE_EXTENSION_SAMI = "smi";
    private static final String FILE_EXTENSION_SUB_RIP = "srt";

    static final String LINE_DELIMITER_UNIX = "\n";
    static final String LINE_DELIMITER_WINDOWS = "\r\n";

    private static final Charset CHARSET_UTF_32BE = Charset.forName("UTF-32BE");
    private static final Charset CHARSET_UTF_32LE = Charset.forName("UTF-32LE");
    private static final Charset CHARSET_EUC_KR = Charset.forName("EUC-KR");
    private static final Charset CHARSET_CP949 = Charset.forName("x-windows-949");
    private static final Charset CHARSET_DEFAULT;

    static {
        String language = System.getProperty("user.language");
        String id = TimeZone.getDefault().getID();

        if ("ko".equals(language) || "Asia/Seoul".equals(id)) {
            CHARSET_DEFAULT = CHARSET_CP949;
        } else {
            CHARSET_DEFAULT = Charset.forName(System.getProperty("sun.jnu.encoding"));
        }
    }

    private final SimpleDateFormat formatSubRip = new SimpleDateFormat("HH:mm:ss,SSS");

    {
        formatSubRip.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    private List<Subtitle> subtitles;
    private File inputFile;
    private String inputType;
    private Charset inputCharset = null;
    private Charset outputCharset = UTF_8;
    private String lineDelimiter;
    private long sync;

    public Converter(File input) {
        inputFile = input;
    }

    public Charset getInputCharset() {
        return inputCharset;
    }

    public Converter setInputCharset(Charset inputCharset) {
        this.inputCharset = inputCharset;
        return this;
    }

    public Charset getOutputCharset() {
        return outputCharset;
    }

    public Converter setOutputCharset(Charset outputCharset) {
        this.outputCharset = outputCharset;
        return this;
    }

    public String getLineDelimiter() {
        return lineDelimiter;
    }

    public Converter setLineDelimiter(String lineDelimiter) {
        this.lineDelimiter = lineDelimiter;
        return this;
    }

    public long getSync() {
        return sync;
    }

    public Converter setSync(long sync) {
        this.sync = sync;
        return this;
    }

    private void fileOpen(File file) throws SubtitleException {
        String inputSubtitle = null;

        if (inputCharset == null) {
            try (BufferedInputStream in = new BufferedInputStream(newInputStream(file.toPath()))) {
                byte[] bytes = readAll(in);

                if (bytes[0] == (byte) 0x00 && bytes[1] == (byte) 0x00 && bytes[2] == (byte) 0xFE && bytes[3] == (byte) 0xFF) {
                    inputCharset = CHARSET_UTF_32BE;
                } else if (bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
                    inputCharset = UTF_8;
                } else if (bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
                    inputCharset = UTF_16BE;
                } else if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
                    if (bytes[2] == (byte) 0x00 && bytes[3] == (byte) 0x00) {
                        inputCharset = CHARSET_UTF_32LE;
                    } else {
                        inputCharset = UTF_16LE;
                    }

                } else {
                    if (CHARSET_DEFAULT.equals(Charset.defaultCharset())) {
                        inputCharset = CHARSET_DEFAULT;
                    } else {
                        String jnu = new String(bytes, CHARSET_DEFAULT);
                        String def = new String(bytes, Charset.defaultCharset());
                        inputSubtitle = jnu.length() < def.length() ? jnu : def;
                    }
                }

                if (inputSubtitle == null) {
                    inputSubtitle = new String(bytes, getInputCharset());
                }

            } catch (IOException e) {
                throw new SubtitleException("File read error. (" + file.getName() + ')', e);
            }

        } else {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(newInputStream(file.toPath()), inputCharset))) {
                inputSubtitle = readAll(reader);
            } catch (IOException e) {
                throw new SubtitleException("File read error. (" + file.getName() + ')', e);
            }
        }

        inputFile = file;
        Matcher matcher = PATTERN_EXTENSION.matcher(file.getName());
        if (!matcher.find()) {
            throw new SubtitleException("File extension dose not exist. (" + file.getName() + ')');
        }

        String subtitle = inputSubtitle.replaceAll(LINE_DELIMITER_WINDOWS, LINE_DELIMITER_UNIX);
        switch (inputType = matcher.group(0).toLowerCase()) {
            case FILE_EXTENSION_SAMI:
                if (!loadingSAMI(subtitle)) {
                    loadingAuto(subtitle);
                }
                break;
            case FILE_EXTENSION_SUB_RIP:
                if (!loadingSubRip(subtitle)) {
                    loadingAuto(subtitle);
                }
                break;
        }
    }

    private void loadingAuto(String text) {
        if (loadingSAMI(text) || loadingSubRip(text)) {
            return;
        }
        throw new SubtitleException("Unknown file type (" + inputFile.getName() + ')');
    }

    private boolean loadingSAMI(String sami) {
        sami = PATTERN_COMMENTS.matcher(sami).replaceAll("");
        String[] split = PATTERN_SAMI.split(sami);

        if (split.length < 2) {
            return false;
        }

        inputType = FILE_EXTENSION_SAMI;

        long start, end = 0;
        String text = "";
        subtitles = new ArrayList<>();
        Subtitle subtitle = new Subtitle(TYPE_SAMI);
        for (String sync : split) {
            Matcher matcher;
            if (!(matcher = PATTERN_SAMI_DATA.matcher(sync)).find()) {
                continue;
            }

            start = end;
            end = parseLong(matcher.group(1));

            if (text.length() > 0) {
                if (text.equals(subtitle.getText())) {
                    subtitle.setEnd(end);
                } else {
                    if (subtitle.getEnd() > start) {
                        subtitle.setEnd(end);
                    }
                    subtitle = new Subtitle(TYPE_SAMI, start, end, text, this.sync);
                    subtitles.add(subtitle);
                }
            }

            text = matcher.group(2).trim();
        }

        return true;
    }

    private boolean loadingSubRip(String srt) {
        String[] split = PATTERN_SUB_RIP.split(srt);
        if (split.length < 2) {
            return false;
        }

        inputType = FILE_EXTENSION_SUB_RIP;

        subtitles = new ArrayList<>();
        for (String str : split) {
            Matcher matcher = PATTERN_SUB_RIP_DATA.matcher(str);
            if (matcher.find()) {
                String text = matcher.group(3);
                try {
                    Matcher m;
                    if ((m = PATTERN_SPACE.matcher(text)).find()) {
                        text = m.replaceAll(" ");
                    }
                    if ((m = PATTERN_LEFT_TRIM.matcher(text)).find()) {
                        text = m.replaceAll(LINE_DELIMITER_UNIX);
                    }
                    if ((m = PATTERN_RIGHT_TRIM.matcher(text)).find()) {
                        text = m.replaceAll(LINE_DELIMITER_UNIX);
                    }
                    subtitles.add(new Subtitle(
                        TYPE_SUB_RIP, formatSubRip.parse(matcher.group(1)),
                        formatSubRip.parse(matcher.group(2)), text.trim(), sync
                    ));

                } catch (ParseException e) {
                    throw new SubtitleException("Time parse error. (path: " + inputFile.getName() + ", line: " + (subtitles.size() + 1) + ')', e);
                }
            }
        }

        return true;
    }

    private void writeFile(File file, String subtitle) throws SubtitleException {
        try (BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(newOutputStream(file.toPath()), outputCharset)
        )) {
            writer.write(subtitle);
        } catch (IOException e) {
            throw new SubtitleException("File write error. (" + file.getName() + ')', e);
        }
    }

    public File writeSami() throws SubtitleException {
        if (FILE_EXTENSION_SAMI.equals(inputType) && sync != 0L) {
            throw new RuntimeException("Unsupported Type: " + inputFile.getName());
        }

        if (lineDelimiter == null) {
            lineDelimiter = LINE_DELIMITER_WINDOWS;
        }

        StringBuilder builder = new StringBuilder(
            "<SAMI>\n<HEAD>\n<TITLE></TITLE>\n<STYLE><!--\np { font-family: sans-serif; text-align: center; }\n"
        );

        String p;
        if (CHARSET_CP949.equals(inputCharset) ||
            CHARSET_EUC_KR.equals(inputCharset)
        ) {
            builder.append(".KRCC { Name: Korean; lang: ko-KR; }\n");
            p = "<P Class=KRCC>";
        } else {
            p = "<P>";
        }

        builder.append("--></STYLE>\n</HEAD>\n<BODY>\n");
        long end = 0;
        for (final Subtitle item : subtitles) {
            long e = item.getEnd();
            if (e < 0L) {
                continue;
            }
            long start = item.getStart();
            if (start != end && end != 0) {
                builder.append("<SYNC Start=").append(end).append(">\n");
            }
            builder.append("<SYNC Start=").append(start).append(">\n").append(p).append(item.getSami()).append('\n');
            end = e;
        }

        builder.append("<SYNC Start=").append(end).append(">\n</BODY>\n</SAMI>");
        String text = builder.toString().trim();

        if (LINE_DELIMITER_WINDOWS.equals(lineDelimiter)) {
            text = text.replace(LINE_DELIMITER_UNIX, LINE_DELIMITER_WINDOWS);
        }

        String name = PATTERN_EXTENSION.split(inputFile.getName(), 0)[0];
        File file = new File(inputFile.getParent(), name + FILE_EXTENSION_SAMI);

        writeFile(file, text);

        return file;
    }

    public File writeSubRip() throws SubtitleException {
        if (FILE_EXTENSION_SUB_RIP.equals(inputType) && sync != 0L) {
            throw new RuntimeException("Unsupported Type: " + inputFile.getName());
        }

        if (lineDelimiter == null) {
            lineDelimiter = LINE_DELIMITER_WINDOWS;
        }

        int i = 0;
        StringBuilder builder = new StringBuilder();
        for (final Subtitle item : subtitles) {
            long end = item.getEnd();
            if (end < 0L) {
                continue;
            }
            String text = item.getPlain();
            if (text.length() < 1) {
                continue;
            }
            builder.append(++i).append('\n').append(formatSubRip.format(new Date(item.getStart()))).append(" --> ")
                .append(formatSubRip.format(new Date(end))).append('\n').append(text).append("\n\n");
        }

        String text = builder.toString().trim();

        if (LINE_DELIMITER_WINDOWS.equals(lineDelimiter)) {
            text = text.replace(LINE_DELIMITER_UNIX, LINE_DELIMITER_WINDOWS);
        }

        String name = PATTERN_EXTENSION.split(inputFile.getName(), 0)[0];
        File file = new File(inputFile.getParent(), name + FILE_EXTENSION_SUB_RIP);

        writeFile(file, text);

        return file;
    }

    public File write(int outputType) throws SubtitleException {
        if (subtitles == null) {
            fileOpen(inputFile);
        }

        switch (outputType) {
            case TYPE_SAMI:
                return writeSami();
            case TYPE_SUB_RIP:
                return writeSubRip();
        }

        switch (inputType) {
            case FILE_EXTENSION_SAMI:
                return writeSubRip();
            case FILE_EXTENSION_SUB_RIP:
                return writeSami();
        }

        throw new SubtitleException("Unknown output type(" + outputType + ')');
    }

    public File write() throws SubtitleException {
        return write(0);
    }

}

