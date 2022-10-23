package myhyuny.subtitle;

import static myhyuny.subtitle.Converter.PATTERN_LEFT_TRIM;
import static myhyuny.subtitle.Converter.PATTERN_RIGHT_TRIM;
import static myhyuny.subtitle.Converter.PATTERN_SAMI_NEW_LINE_TAG;
import static myhyuny.subtitle.Converter.PATTERN_SAMI_TAG;
import static myhyuny.subtitle.Converter.PATTERN_SPACE;

import java.util.Date;
import java.util.regex.Matcher;

/**
 * @author Hyunmin Kang
 */
class Subtitle {

    static final int TYPE_SAMI = 0x1;
    static final int TYPE_SUB_RIP = 0x1 << 1;
    private int type = 0;
    private long start = 0L;
    private long end = 0L;
    private String text;
    private long sync = 0L;

    Subtitle(int type) {
        this.type = type;
    }

    Subtitle(int type, long sync) {
        this(type);
        this.sync = sync;
    }

    Subtitle(int type, long start, long end, String text, long sync) {
        this(type, sync);
        this.start = start;
        this.end = end;
        this.text = text;
    }

    Subtitle(int type, Date start, Date end, String text, long sync) {
        this(type, start.getTime(), end.getTime(), text, sync);
    }

    long getStart() {
        long l = start + sync;
        if (l < 0L) {
            l = 0L;
        }
        return l;
    }

    long getEnd() {
        long l = end + sync;
        if (l < 0L) {
            l = 0L;
        }
        return l;
    }

    void setEnd(long end) {
        this.end = end;
    }

    String getText() {
        return text;
    }

    String getSami() {
        if (type == TYPE_SAMI) {
            return text;
        }
        String sami = text;
        Matcher matcher = Converter.PATTERN_NEW_LINE.matcher(sami);
        if (matcher.find()) {
            sami = matcher.replaceAll("<BR>\n");
        }
        return sami;
    }

    String getPlain() {
        if (type == TYPE_SAMI) {
            Matcher matcher;
            String str = text.replaceAll("&nbsp;", " ");
            if ((matcher = PATTERN_SAMI_NEW_LINE_TAG.matcher(str)).find()) {
                str = matcher.replaceAll(Converter.LINE_DELIMITER_UNIX);
            }
            if ((matcher = PATTERN_SAMI_TAG.matcher(str)).find()) {
                str = matcher.replaceAll("");
            }
            if ((matcher = PATTERN_SPACE.matcher(str)).find()) {
                str = matcher.replaceAll(" ");
            }
            if ((matcher = PATTERN_LEFT_TRIM.matcher(str)).find()) {
                str = matcher.replaceAll(Converter.LINE_DELIMITER_UNIX);
            }
            if ((matcher = PATTERN_RIGHT_TRIM.matcher(str)).find()) {
                str = matcher.replaceAll(Converter.LINE_DELIMITER_UNIX);
            }
            return str.trim();
        }
        return text;
    }

}
