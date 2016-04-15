public class Debug {

    private static boolean DEBUG = true;
    //private static boolean DEBUG = true;
    //private static boolean PRINT = true;
    private static boolean PRINT = false;
    public static void DEBUG(String s) {
	if (DEBUG)
	    System.out.println(s);
    }

    public static void PRINT(String s) {
	if (PRINT)
	    System.out.print(s);
    }
}