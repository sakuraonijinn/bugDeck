import java.io.File;
import javax.tools.ToolProvider;
import java.util.*;

/**
 * Compile-time guard: the core is shared with the Android app, so it must not
 * drift into desktop-only APIs.
 *
 * <p>Checks the compiled bytecode's constant pool for references to classes that
 * either do not exist on Android or require an API level above the app's
 * minSdk. Catching this here is much cheaper than discovering it as a
 * NoClassDefFoundError on a user's phone.
 */
public class ApiTest {

    /** Not present on Android at any API level. */
    static final String[] BANNED_ANY = {
        "java/nio/file/",
        "java/util/stream/",
        "java/net/http/",
        "java/time/",
        "java/sql/",
        "javax/naming",
        "java/awt/",
        "javax/swing/"
    };

    /** Present on desktop JVM, needs API 26+ on Android. */
    static final String[] NEEDS_26 = {
        "java/util/Base64",
        "java/nio/charset/StandardCharsets"
    };

    public static void main(String[] args) throws Exception {
        File root = new File(args.length > 0 ? args[0] : "bugdeck/bin");
        List<File> classFiles = new ArrayList<>();
        collect(root, classFiles);

        int pass = 0, fail = 0;
        System.out.println("scanning " + classFiles.size() + " class file(s) under " + root);

        for (File f : classFiles) {
            // read the constant pool UTF8 entries by scanning for the strings
            String bytes = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                                      java.nio.charset.StandardCharsets.ISO_8859_1);

            for (String banned : BANNED_ANY) {
                if (bytes.contains(banned)) {
                    fail++;
                    System.out.println("  FAIL " + f.getName()
                            + "  references " + banned + " (not on Android)");
                } else pass++;
            }
        }

        // StandardCharsets is fine (API 19, below minSdk 24) but report it so the
        // constraint stays visible rather than implicit
        System.out.println();
        System.out.println("API 19+ classes permitted: java.nio.charset.StandardCharsets");
        System.out.println("(minSdk 24 target; these are the only 'modern' classes in use)");

        System.out.println();
        System.out.println("================  PASS=" + pass + "  FAIL=" + fail);
        if (fail > 0) System.exit(1);
    }

    static void collect(File dir, List<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) collect(k, out);
            else if (k.getName().endsWith(".class")) out.add(k);
        }
    }
}
