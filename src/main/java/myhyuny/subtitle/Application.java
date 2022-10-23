package myhyuny.subtitle;

import static java.util.Arrays.stream;
import static myhyuny.subtitle.Converter.PATTERN_FILE_EXTENSION;

import java.io.File;
import java.lang.reflect.InvocationTargetException;

/**
 * SubtitleConverter
 *
 * @author Hyunmin Kang
 */
public class Application {

    public static void main(String[] args) {
        if (args.length < 1) {
            try {
                Class<?> c = Class.forName("myhyuny.subtitle.MainFrame");
                c.getMethod("setVisible", boolean.class).invoke(c.getConstructor().newInstance(), true);
            } catch (
                ClassNotFoundException | InstantiationException | IllegalAccessException |
                NoSuchMethodException | InvocationTargetException ignore
            ) {
                System.out.println("Usage: java -jar sc.jar [files...]");
                System.out.println();
            }
            return;
        }

        try {
            stream(args).parallel()
                .filter(u -> PATTERN_FILE_EXTENSION.matcher(u).find())
                .map(File::new)
                .filter(File::isFile)
                .forEach((file) -> {
                    File out = new Converter(file).write();
                    System.out.println(file.getName() + " -> " + out.getName());
                });
            System.out.println();
        } catch (SubtitleException e) {
            System.err.println(e.getMessage());
            System.out.println();
            System.exit(1);
        }
    }

}
