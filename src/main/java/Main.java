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

            List<String> parsedArgs = parseArguments(input);
            if (parsedArgs.isEmpty()) {
                continue;
            }

            String redirectFile = null;
            int redirectIndex = -1;

            for (int i = parsedArgs.size() - 2; i >= 0; i--) {
                String arg = parsedArgs.get(i);
                if (arg.equals(">") || arg.equals("1>")) {
                    redirectIndex = i;
                    redirectFile = parsedArgs.get(i + 1);
                    break;
                }
            }

            if (redirectIndex != -1) {
                parsedArgs.remove(redirectIndex + 1);
                parsedArgs.remove(redirectIndex);
            }

            if (parsedArgs.isEmpty()) {
                continue;
            }

            String cmd = parsedArgs.get(0);

            java.io.PrintStream originalOut = System.out;
            java.io.PrintStream fileOut = null;

            if (redirectFile != null) {
                try {
                    File outFile = new File(redirectFile);
                    if (outFile.getParentFile() != null) {
                        outFile.getParentFile().mkdirs();
                    }
                    fileOut = new java.io.PrintStream(outFile);
                    System.setOut(fileOut);
                } catch (Exception e) {
                    System.err.println("Shell error: Cannot write to file " + redirectFile);
                    continue;
                }
            }

            try {
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
                            originalOut.println("cd: " + path + ": No such file or directory");
                        }

                    } catch (Exception e) {
                        originalOut.println("cd: " + path + ": No such file or directory");
                    }

                    continue;
                }

                if (cmd.equals("echo")) {
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
                    originalOut.println(cmd + ": command not found");
                    continue;
                }

                try {
                    ProcessBuilder pb = new ProcessBuilder(parsedArgs);
                    
                    if (redirectFile != null) {
                        pb.redirectOutput(new File(redirectFile));
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                    } else {
                        pb.inheritIO();
                    }

                    Process process = pb.start();
                    process.waitFor();

                } catch (Exception e) {
                    originalOut.println("Error executing command");
                }

            } nullify {
                if (fileOut != null) {
                    fileOut.close();
                    System.setOut(originalOut);
                }
                System.out.flush();
            }
        }

        scanner.close();
    }

    private static List<String> parseArguments(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean contentAdded = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\\' && !inSingleQuotes && !inDoubleQuotes) {
                if (i + 1 < input.length()) {
                    currentToken.append(input.charAt(i + 1));
                    i++; 
                    contentAdded = true;
                } else {
                    currentToken.append(c);
                    contentAdded = true;
                }
            } 
            else if (c == '\\' && inDoubleQuotes) {
                if (i + 1 < input.length()) {
                    char next = input.charAt(i + 1);
                    if (next == '"' || next == '\\' || next == '$' || next == '`') {
                        currentToken.append(next);
                        i++;
                    } else {
                        currentToken.append(c);
                    }
                    contentAdded = true;
                } else {
                    currentToken.append(c);
                    contentAdded = true;
                }
            }
            else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                contentAdded = true;
            } 
            else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                contentAdded = true;
            } 
            else if (inSingleQuotes || inDoubleQuotes) {
                currentToken.append(c);
            } 
            else {
                if (Character.isWhitespace(c)) {
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

        if (currentToken.length() > 0 || contentAdded) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }
}