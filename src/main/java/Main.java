import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        while (true) {

            System.out.print("$ ");
            System.out.flush();

            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                continue;
            }

            if (input.equals("exit")) {
                break;
            }

            // Parse the input into arguments handling single quotes
            List<String> parsedArgs = parseArguments(input);
            if (parsedArgs.isEmpty()) {
                continue;
            }

            String cmd = parsedArgs.get(0);

            if (cmd.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
                continue;
            }

            if (cmd.equals("cd")) {
                String path = parsedArgs.size() > 1 ? parsedArgs.get(1) : "~";
                File target;

                if (path.equals("~")) {
                    String home = System.getenv("HOME");
                    target = new File(home);
                } else if (path.startsWith("/")) {
                    target = new File(path);
                } else {
                    File current = new File(System.getProperty("user.dir"));
                    target = new File(current, path);
                }

                try {
                    File resolved = new File(target.getCanonicalPath());

                    if (resolved.isDirectory()) {
                        System.setProperty("user.dir", resolved.getAbsolutePath());
                    } else {
                        System.out.println("cd: " + path + ": No such file or directory");
                    }

                } catch (Exception e) {
                    System.out.println("cd: " + path + ": No such file or directory");
                }

                continue;
            }

            if (cmd.equals("echo")) {
                // Join all parsed arguments after 'echo' with a single space
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < parsedArgs.size(); i++) {
                    sb.append(parsedArgs.get(i));
                    if (i < parsedArgs.size() - 1) {
                        sb.append(" ");
                    }
                }
                System.out.println(sb.toString());
                continue;
            }

            if (cmd.equals("type")) {
                String targetCmd = parsedArgs.size() > 1 ? parsedArgs.get(1) : "";

                if (targetCmd.equals("echo") || targetCmd.equals("exit") || targetCmd.equals("type") || targetCmd.equals("pwd") || targetCmd.equals("cd")) {
                    System.out.println(targetCmd + " is a shell builtin");
                    continue;
                }

                String pathEnv = System.getenv("PATH");
                String found = null;

                if (pathEnv != null) {
                    String[] paths = pathEnv.split(File.pathSeparator);

                    for (String dir : paths) {
                        File file = new File(dir, targetCmd);

                        if (file.exists() && file.canExecute()) {
                            found = file.getAbsolutePath();
                            break;
                        }
                    }
                }

                if (found != null) {
                    System.out.println(targetCmd + " is " + found);
                } else {
                    System.out.println(targetCmd + ": not found");
                }

                continue;
            }

            // External Command handling
            String pathEnv = System.getenv("PATH");
            String found = null;

            if (pathEnv != null) {
                String[] paths = pathEnv.split(File.pathSeparator);

                for (String dir : paths) {
                    File file = new File(dir, cmd);

                    if (file.exists() && file.canExecute()) {
                        found = file.getAbsolutePath();
                        break;
                    }
                }
            }

            if (found == null) {
                System.out.println(cmd + ": command not found");
                continue;
            }

            try {
                ProcessBuilder pb = new ProcessBuilder(parsedArgs);
                pb.inheritIO();

                Process process = pb.start();
                process.waitFor();

                // If the system output stream was modified by inheritIO, restore terminal sync 
                System.out.flush();

            } catch (Exception e) {
                System.out.println("Error executing command");
            }
        }

        scanner.close();
    }

    /**
     * Parses the command line input into a list of arguments.
     * Handles single quotes, preservation of internal spaces, and concatenation.
     */
    private static List<String> parseArguments(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean contentAdded = false; // Tracks if we encountered text/empty quotes to form an argument

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\'') {
                inSingleQuotes = !inSingleQuotes;
                contentAdded = true; // Handles empty quotes like hello''world or just ''
            } else if (inSingleQuotes) {
                // Inside quotes, everything is literal
                currentToken.append(c);
            } else {
                // Outside quotes
                if (Character.isWhitespace(c)) {
                    // Spaces outside quotes act as delimiters
                    if (currentToken.length() > 0 || contentAdded) {
                        tokens.add(currentToken.toString());
                        currentToken.setLength(0);
                        contentAdded = false;
                    }
                } else {
                    currentToken.append(c);
                }
            }
        }

        // Add the last token if exists
        if (currentToken.length() > 0 || contentAdded) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }
}