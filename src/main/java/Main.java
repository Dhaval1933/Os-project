import java.util.Scanner;
public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("$ ");
            if (!sc.hasNextLine()) {
                break;
            }
            String a = sc.nextLine().trim();
            if (a.isEmpty()) {
                continue;
            }
            String[] inputParts = a.split(" ");
            String command = inputParts[0];
            if (command.equals("echo")) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < inputParts.length; i++) {
                    sb.append(inputParts[i]);
                    if (i < inputParts.length - 1) {
                        sb.append(" ");
                    }
                }
                System.out.println(sb.toString());
            } else {
                System.out.println(a + ": command not found");
            }
        }
    }
}
