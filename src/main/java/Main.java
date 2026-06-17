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
            System.err.println(a+": command not found");
        }
    }
}
