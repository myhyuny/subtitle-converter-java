package myhyuny.subtitle.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;

/**
 * @author Hyunmin Kang
 */
public class IO {

    public static byte[] readAll(InputStream in) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            IO.copy(in, out);
            return out.toByteArray();
        }
    }

    public static String readAll(Reader in) throws IOException {
        try (StringWriter out = new StringWriter()) {
            IO.copy(in, out);
            return out.toString();
        }
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] b = new byte[8192];
        for (int len; (len = in.read(b)) != -1; ) {
            out.write(b, 0, len);
        }
        out.flush();
    }

    public static void copy(Reader in, Writer out) throws IOException {
        char[] cbuf = new char[8192];
        for (int len; (len = in.read(cbuf)) != -1; ) {
            out.write(cbuf, 0, len);
        }
        out.flush();
    }

}
