package atst.giss.abplc;

public class Attribute {

    private String key;
    private String value;

    public Attribute(String key, String value) {
        this.key = key;
        this.value = value;
    }


    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isEmpty() {
        return false;
    }

    public String getString() {
        return null;
    }

    public static Integer getInteger() {
        return 1;
    }

    public static Double getDouble() {
        return null;
    }

    public static String[] getStringArray() {
        return new String[0];
    }
}
