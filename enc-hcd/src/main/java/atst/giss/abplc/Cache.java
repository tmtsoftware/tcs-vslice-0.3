package atst.giss.abplc;


// dummy class to help things compile

public class Cache {

    public static Attribute lookup(String key) {
        switch (key){
            case "tag:RAW1:item:b1":
                return new Attribute("dfd","sdfdf");

        }
        return null;
    }

    public static void storeAll(IAttributeTable attributeTable) {

    }

    public static void store(Attribute attribute) {

    }

}
