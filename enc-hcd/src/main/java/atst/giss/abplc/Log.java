package atst.giss.abplc;

// dummy class created to satisfy errors

public class Log {

    public static void debug(String type, int number, String message) {
        System.out.println(message);
    }

    public static void severe(String type, String message) {
        System.out.println(message);
    }

    public static void warn(String message) {
        System.out.println(message);
    }

    public static void warn(String type, String message) {
        System.out.println(message);
    }

    public static void note(String message) {
        System.out.println(message);
    }

    public static int getDebugLevel(String level) {
        return 3;
    }

}
