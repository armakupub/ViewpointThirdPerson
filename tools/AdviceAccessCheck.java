import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// ZombieBuddy copies an advice body into the class it patches, so everything the body reaches in
// the mod's own classes must be public: anything else compiles, then throws IllegalAccessError
// in the game, every time the patched method runs.
// Usage: java tools/AdviceAccessCheck.java <src dir>
public class AdviceAccessCheck {
    static final Pattern ADVICE = Pattern.compile("@Patch\\.(?:OnEnter|OnExit)[^\\n]*\\n\\s*public static [\\w<>\\[\\]]+ \\w+\\((?:[^()]|\\([^()]*\\))*\\)\\s*\\{");
    static final Pattern REFERENCE = Pattern.compile("\\b([A-Z]\\w*)\\.([a-zA-Z_]\\w*)(\\s*\\()?");

    public static void main(String[] args) throws IOException {
        Map<String, String> sources = new HashMap<>();
        try (Stream<Path> files = Files.walk(Path.of(args[0]))) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                String name = p.getFileName().toString();
                sources.put(name.substring(0, name.length() - 5), Files.readString(p));
            }
        }
        TreeSet<String> problems = new TreeSet<>();
        for (Map.Entry<String, String> file : sources.entrySet()) {
            for (String body : adviceBodies(file.getValue())) {
                Matcher m = REFERENCE.matcher(body);
                while (m.find()) {
                    String owner = sources.get(m.group(1));
                    if (owner == null) continue;
                    String modifiers = modifiers(owner, m.group(2), m.group(3) != null);
                    if (modifiers == null) {
                        problems.add(file.getKey() + ": " + m.group(1) + "." + m.group(2) + " has no declaration found");
                    } else if (!modifiers.contains("public")) {
                        problems.add(file.getKey() + ": " + m.group(1) + "." + m.group(2) + " is not public");
                    }
                }
            }
        }
        if (problems.isEmpty()) return;
        problems.forEach(p -> System.err.println("[build] ERROR: advice reaches " + p));
        System.exit(1);
    }

    static List<String> adviceBodies(String source) {
        List<String> bodies = new ArrayList<>();
        Matcher m = ADVICE.matcher(source);
        while (m.find()) {
            int i = m.end();
            int depth = 1;
            while (depth > 0 && i < source.length()) {
                char c = source.charAt(i++);
                if (c == '{') depth++;
                else if (c == '}') depth--;
            }
            bodies.add(source.substring(m.end(), i));
        }
        return bodies;
    }

    // The modifiers before the method, or else the field, of that name; null if there is none.
    static String modifiers(String source, String member, boolean method) {
        Pattern declaration = Pattern.compile("(?m)^\\s*((?:(?:public|protected|private|static|final|volatile|synchronized|transient)\\s+)*)"
                + "[\\w<>\\[\\].]+\\s+" + (method ? "" : "(?:\\w+\\s*,\\s*)*") + Pattern.quote(member) + (method ? "\\s*\\(" : "\\s*[;=,]"));
        Matcher m = declaration.matcher(source);
        return m.find() ? m.group(1) : null;
    }
}
