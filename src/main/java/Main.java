import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc=new Scanner(System.in);
        while(true){
            System.out.print("$ ");
            if(!sc.hasNext()){
                break;
            }
            String a=sc.nextLine().trim();
            if (a.isEmpty()) {
                continue;
            }
            if(a.equals("exit")){
                break;
            }
            if(a.equals("type echo")){
                System.out.println("echo is a shell builtin");
            }
            else if(a.equals("type exit")){
                System.out.println("exit is a shell builtin");
            }
            else if(a.equals("type type")){
                System.out.println("type is a shell builtin");
            }
            else if(a.startsWith("type")){
                System.out.println(a.substring(5)+": not found");
            }
            if(a.startsWith("echo ")){
                System.out.println(a.substring(5));
            }
            else
            System.err.println(a+": command not found");
        }
    }
}
