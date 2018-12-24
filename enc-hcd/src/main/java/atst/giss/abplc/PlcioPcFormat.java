package atst.giss.abplc;

import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;


/**
 * The abstract class PlcioPcFormat contains constants and helper methods
 * used when operating with PLCIO <i>pcFormat</i> strings. PLCIO uses the
 * pcFormat string to describe the data types contained
 * in a PLC tag so that it can correctly encode/decode PLC tag data.
 * The {@code abplc} package uses the same format string to
 * communicate tag data information between collaborating objects.
 * <p>
 * Each different data type contained in the PLC tag is described in
 * a PLCIO pcFormat string using the format:
 * <ul>
 * <li><code>&lt;typeId&gt;[optional length in bytes]</code>
 * </ul>
 * The typeId may be one of the following:
 * <ul>
 * <li>{@code c} &mdash; char, of length 1 byte
 * <li>{@code i} &mdash; short, of length 2 bytes
 * <li>{@code j} &mdash; int, of length 4 bytes
 * <li>{@code q} &mdash; long (or quad), of length 8 bytes
 * <li>{@code r} &mdash; real, of length 4 bytes
 * <li>{@code d} &mdash; double, of length 8 bytes
 * </ul>
 * A pcFormat string is composed of one or more of the above typeId and
 * optional length pairs, and in the abplc package this is termed a <i>type descriptor</i>
 * of the pcFormat string. Using the optional byte length means that a
 * single type descriptor describes data contained in multiple occurrences of
 * the same type, the term <i>member</i> is used to reference the individual
 * data contained in each type occurrence.
 * <p>
 * The class {@linkplain PlcioPcFormatType} is
 * used to store information about individual type descriptors of a pcFormat string.
 * Therefore, numerous objects of this class may be instantiated to store
 * information about all the data contained in a complete pcFormat string.
 * The Java ArrayList collection is the most suitable structure for storing all
 * PlcioPcFormatType objects relating to a complete pcFormat string,
 * as the order in which they are contained in the string must be maintained
 * for correct encoding/decoding of PLC tags.
 * 
 * @author Alastair Borrowman (OSL)
 */
public abstract class PlcioPcFormat {
 
    /*
     *  Private class constants
     */
    /** Log category of the PlcioPcFormat class. */
    private static final String LOG_CAT = "PCFORMAT";
    /** Pattern used to find pcFormat type identifiers in a pcFormat string. */
    private static final Pattern PATTERN_TYPE_ID = Pattern.compile("[cijqrd]"); // one of PLCIO type IDs
    /** Pattern used to find optional byte length integers in a pcFormat string. */
    private static final Pattern PATTERN_BYTE_LEN = Pattern.compile("[0-9]+"); // one or more integers

    // Public class constants - PLCIO typeIds
    /**
     * The typeId used to describe a type of char is <b>c</b>.
     */
    public static final char TYPE_C = 'c';
    /**
     * The typeId used to describe a type of short is <b>i</b>.
     */
    public static final char TYPE_I = 'i';
    /**
     * The typeId used to describe a type of int is <b>j</b>.
     */
    public static final char TYPE_J = 'j';
    /**
     * The typeId used to describe a type of long is <b>q</b>.<br>
     * PLCIO uses the term <i>quad</i> instead of long.
     */
    public static final char TYPE_Q = 'q';
    /**
     * The typeId used to describe a type of real is <b>r</b>.
     */
    public static final char TYPE_R = 'r';
    /**
     * The typeId used to describe a type of double is <b>d</b>.
     */
    public static final char TYPE_D = 'd';
     
    // Public class constants - type byte lengths
    /**
     * The length in bytes used by PLCIO for type char = <b>1</b> byte.<br>
     * <b>NB</b> In PLCIO the type of char has length 1 byte but
     * a type of Java char has length 2 bytes. We are interested
     * in how PLCIO represents a char and so give it length 1 byte.
     */
    public static final int BYTE_LEN_C = 1;
    /**
     * The length in bytes used by PLCIO for type short = <b>2</b> bytes.
     */
    public static final int BYTE_LEN_I = 2;
    /**
     * The length in bytes used by PLCIO for type int = <b>4</b> bytes.
     */
    public static final int BYTE_LEN_J = 4;
    /**
     * The length in bytes used by PLCIO for type long = <b>8</b> bytes.
     */
    public static final int BYTE_LEN_Q = 8;
    /**
     * The length in bytes used by PLCIO for type real = <b>4</b> bytes.
     */
    public static final int BYTE_LEN_R = 4;
    /**
     * The length in bytes used by PLCIO for type dobule = <b>8</b> bytes.
     */
    public static final int BYTE_LEN_D = 8;

    // Public class constants - string representations of types used by PLCIO
    /**
     * A {@linkplain String} used to describe PLCIO type <b>char</b>.
     */
    public static final String TYPE_STR_C = "char";
    /**
     * A {@linkplain String} used to describe PLCIO type <b>short</b>.
     */
    public static final String TYPE_STR_I = "short";
    /**
     * A {@linkplain String} used to describe PLCIO type <b>int</b>.
     */
    public static final String TYPE_STR_J = "int";
    /**
     * A {@linkplain String} used to describe PLCIO type <b>long</b>.
     */
    public static final String TYPE_STR_Q = "long";
    /**
     * A {@linkplain String} used to describe PLCIO type <b>float</b>.
     */
    public static final String TYPE_STR_R = "float";
    /**
     * A {@linkplain String} used to describe PLCIO type <b>double</b>.
     */
    public static final String TYPE_STR_D = "double";
    
    /**
     * Get the CSF log categories in use by this class.
     * 
     * @return Log catetories used by this class.
     */
    public static String[] getLogCatsUsed() {
        return new String[] {LOG_CAT};
    }
    
    /**
     * Class method to calculate the total byte length of the given PLCIO pcFormat
     * string.
     * @param pcFormatStr    The pcFormat string from which the total bytes contained
     * in the string are to be calculated.
     * @return    The total number of bytes described by the pcFormat string.
     * @throws ABPlcioExceptionBadPlcTagProperties -- if the tag has bad properties
     */
    public static int getPlcioPcFormatStrTotalBytes(String pcFormatStr)
            throws ABPlcioExceptionBadPlcTagProperties {
        int totalByteLength = 0;

        // NB. the code used here for parsing the pcFormat string is very similar to that
        // used in plcioPcFormatStr2ArrayList() but it is NOT the same - here we are only
        // interested in keeping a running total of the number of bytes in the pcFormat string,
        // plcioPcFormatStr2ArrayList() does this but ALSO creates new PlcioPcFormatType
        // objects for each type described in the pcFormat string.
        
        // use 2 scanners on the pcFormat string:
        // - 1st for scanning type IDs
        Scanner typeIdScanner = new Scanner(pcFormatStr);
        // - 2nd for scanning for optional byte lengths given for a type ID
        Scanner byteLengthScanner = new Scanner(pcFormatStr);
        
        String nextTypeID = typeIdScanner.findInLine(PATTERN_TYPE_ID);
        String nextByteLength = byteLengthScanner.findInLine(PATTERN_BYTE_LEN);
        while(nextTypeID != null) {
            // get the type and byte length of this type descriptor
            char pcFormatTypeID = nextTypeID.charAt(0);        

            // check whether this type ID has been given an optional byte length
            if (nextByteLength != null) {
                MatchResult typeIdMatchRes = typeIdScanner.match();
                MatchResult byteLengthMatchRes = byteLengthScanner.match();

                if (typeIdMatchRes.end() == byteLengthMatchRes.start()) {
                    totalByteLength += Integer.parseInt(nextByteLength);
                    // move to next byte length
                    nextByteLength = byteLengthScanner.findInLine(PATTERN_BYTE_LEN);
                }
                else {
                    // no optional byte length given for this type ID so use defined
                    // byte length for the type defined by the type ID
                    totalByteLength += getTypeByteLength(pcFormatTypeID);
                }
            }
            else {
                // no optional byte length given so use defined byte length for
                // the type defined by the type ID
                totalByteLength += getTypeByteLength(pcFormatTypeID);
            }
            
            // move on to next type ID
            nextTypeID = typeIdScanner.findInLine(PATTERN_TYPE_ID);
        } // end while
            
        typeIdScanner.close();
        byteLengthScanner.close();

        return totalByteLength;
    } // end getPlcioPcFormatStrTotalBytes()
   
    /**
     * Class method to create a Java ArrayList of PlcioPcFormatType
     * objects that describes all data contained in a PLCIO pcFormat
     * string.
     * @param pcFormatStr    The pcFormat string from which all type descriptions
     * will be extracted and used to create an ArrayList of PlcioPcFormatType
     * objects.
     * @return    An ArrayList of PlcioPcFormatType objects fully describing
     * the PLC tag defined by the pcFormat string.
     * 
     * @throws ABPlcioExceptionBadPlcTagProperties -- if the tag has bad properties
     */
    public static ArrayList<PlcioPcFormatType> plcioPcFormatStr2ArrayList(String pcFormatStr)
            throws ABPlcioExceptionBadPlcTagProperties {
        Log.debug(LOG_CAT, 4, "PLCIO pcFormat = '" + pcFormatStr + "'");
       
        // use 2 scanners on the pcFormat string:
        // - 1st for scanning type IDs
        Scanner typeIdScanner = new Scanner(pcFormatStr);
        // - 2nd for scanning for optional byte lengths given for a type ID
        Scanner byteLengthScanner = new Scanner(pcFormatStr);

        // create ArrayList holding PLCIO pcFormat information
        ArrayList<PlcioPcFormatType> pcFormatTypeAL = new ArrayList<PlcioPcFormatType>();

        // initialize the total byte length of this pcFormat
        int byteLengthTotal = 0;

        String nextTypeID = typeIdScanner.findInLine(PATTERN_TYPE_ID);
        String nextByteLength = byteLengthScanner.findInLine(PATTERN_BYTE_LEN);
        while(nextTypeID != null) {
            // get the type and byte length of this type descriptor
            char pcFormatTypeID = nextTypeID.charAt(0);        
            int byteLength = 0;
            String byteLengthStr = null; 

            // check whether this type ID has been given an optional byte length
            if (nextByteLength != null) {
                MatchResult typeIdMatchRes = typeIdScanner.match();
                MatchResult byteLengthMatchRes = byteLengthScanner.match();

                if (typeIdMatchRes.end() == byteLengthMatchRes.start()) {
                    byteLength = Integer.parseInt(nextByteLength);
                    byteLengthStr = nextByteLength;
                    // move to next byte length
                    nextByteLength = byteLengthScanner.findInLine(PATTERN_BYTE_LEN);
                }
                else {
                    // no optional byte length given for this type ID so use defined
                    // byte length for the type defined by the type ID
                    byteLength = getTypeByteLength(pcFormatTypeID);
                }
            }
            else {
                // no optional byte length given so use defined byte length for
                // the type defined by the type ID
                byteLength = getTypeByteLength(pcFormatTypeID);
            }

            // store the pcFormat information 
            PlcioPcFormatType pcFormatType = new PlcioPcFormatType(pcFormatTypeID, byteLength, byteLengthStr);
            pcFormatTypeAL.add(pcFormatType);

            // keep a running total of number of bytes described in pcFormat
            byteLengthTotal += byteLength;

            // move on to next type ID
            nextTypeID = typeIdScanner.findInLine(PATTERN_TYPE_ID);

            Log.debug(LOG_CAT, 4, "\t[" + pcFormatTypeAL.indexOf(pcFormatType) + "] " + pcFormatType);
        } // end while
       
        typeIdScanner.close();
        byteLengthScanner.close();

        Log.debug(LOG_CAT, 3, "PLCIO pcFormat \"" + pcFormatStr + "\" contains " + pcFormatTypeAL.size() +
                " PLCIO typeIDs, total byte length = " + byteLengthTotal);

        return pcFormatTypeAL;
    } // end plcioPcFormat2ArrayList()

    /**
     * Given a PLCIO type ID return the type's length in bytes.
     * 
     * @param typeC A valid PLCIO type ID.
     * 
     * @return The length in bytes of the PLCIO type ID.
     * 
     * @throws ABPlcioExceptionBadPlcTagProperties if type ID is not a valid PLCIO type ID.
     */
    public static int getTypeByteLength(char typeC) throws ABPlcioExceptionBadPlcTagProperties {
        int byteLen = 0;
   
        switch (typeC) {
        case TYPE_C:
            byteLen = BYTE_LEN_C;
            break;
        case TYPE_I:
            byteLen = BYTE_LEN_I;
            break;
        case TYPE_J:
            byteLen = BYTE_LEN_J;
               break;
        case TYPE_Q:
            byteLen = BYTE_LEN_Q;
            break;
        case TYPE_R:
            byteLen = BYTE_LEN_R;
            break;
           case TYPE_D:
               byteLen = BYTE_LEN_D;
               break;
           default:
            throw new ABPlcioExceptionBadPlcTagProperties("The type ID '"+String.valueOf(typeC)+
                    "' is not a valid PLCIO type ID");
        } // end switch
       
        return byteLen;
    } // end getTypeByteLength()
    
    /**
     * Given a PLCIO type ID return the type's string description.
     * 
     * @param typeC A valid PLCIO type ID.
     * 
     * @return The string description of the PLCIO type ID.
     * 
     * @throws ABPlcioExceptionBadPlcTagProperties if type ID is not a valid PLCIO type ID.
     */
    public static String getTypeString(char typeC) throws ABPlcioExceptionBadPlcTagProperties {
        String typeString = null;
        
        switch (typeC) {
        case TYPE_C:
            typeString = TYPE_STR_C;
            break;
        case TYPE_I:
            typeString = TYPE_STR_I;
            break;
        case TYPE_J:
            typeString = TYPE_STR_J;
               break;
        case TYPE_Q:
            typeString = TYPE_STR_Q;
            break;
        case TYPE_R:
            typeString = TYPE_STR_R;
            break;
           case TYPE_D:
               typeString = TYPE_STR_D;
               break;
           default:
               // unrecognized type ID
            throw new ABPlcioExceptionBadPlcTagProperties("The type ID '"+String.valueOf(typeC)+
                    "' is not a valid PLCIO type ID");
        } // end switch
       
        return typeString;
    } // end getTypeString()

} // end class PlcioPcFormat
