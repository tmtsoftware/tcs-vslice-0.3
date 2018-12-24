package atst.giss.abplc;

import java.io.Console;
import java.util.Scanner;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 *  Use and output:
 *  <pre>
 *  ajava atst.giss.abplc.PlcioPcFormatTestHarness
 *  Enter your pcFormat: icci8r16r16r16r16iccrrrii
 *  Type ID "i"  NO optional byte length using type len, byteLength = 2.
 *  Type ID "c"  NO optional byte length using type len, byteLength = 1.
 *  Type ID "c"  NO optional byte length using type len, byteLength = 1.
 *  Type ID "i"  optional byteLength = 8.
 *  Type ID "r"  optional byteLength = 16.
 *  Type ID "r"  optional byteLength = 16.
 *  Type ID "r"  optional byteLength = 16.Type ID "r"  optional byteLength = 16.
 *  Type ID "i"  NO optional byte length using type len, byteLength = 2.
 *  Type ID "c"  NO optional byte length using type len, byteLength = 1.
 *  Type ID "c"  NO optional byte length using type len, byteLength = 1.
 *  Type ID "r"  NO optional byte length using type len, byteLength = 4.
 *  Type ID "r"  NO optional byte length using type len, byteLength = 4.
 *  Type ID "r"  NO optional byte length using type len, byteLength = 4.
 *  Type ID "i"  NO optional byte length using type len, byteLength = 2.
 *  Type ID "i"  NO optional byte length using type len, byteLength = 2.
 * </pre>
 * @author Alastair Borrowman (OSL)
 *
 */
public class PlcioPcFormatTestHarness {
	private static final Pattern PATTERN_TYPE_IDS = Pattern.compile("[cijqrd]"); // one of PLCIO type IDs
	private static final Pattern PATTERN_BYTE_LEN = Pattern.compile("[0-9]+"); // one or more digits

	public static void main(String[] args){
		Console console = System.console();
        if (console == null) {
            System.err.println("No console.");
            System.exit(1);
        }
        	
        String pcFormatStr = console.readLine("%nEnter your pcFormat: ");
        
        // use 2 scanners on the pc_format string:
        // - 1st for scanning type IDs
        Scanner typeIdScanner = new Scanner(pcFormatStr);
        // - 2nd for scanning for optional byte lengths given for a type ID
        Scanner byteLengthScanner = new Scanner(pcFormatStr);
        
        int byteLengthTotal = 0;
        String nextTypeID = typeIdScanner.findInLine(PATTERN_TYPE_IDS);
        String nextByteLength = byteLengthScanner.findInLine(PATTERN_BYTE_LEN);
        while(nextTypeID != null) {
        	// get the type and byte length of this member
        	char pcFormatTypeID = nextTypeID.charAt(0);        
			int byteLength = 0;

			console.format("Type ID \"%s\" ", pcFormatTypeID);

			// check whether this type ID has been given an optional byte length
			if (nextByteLength != null) {
				MatchResult typeIdMatchRes = typeIdScanner.match();
				MatchResult byteLengthMatchRes = byteLengthScanner.match();

				if (typeIdMatchRes.end() == byteLengthMatchRes.start()) {
					byteLength = Integer.parseInt(nextByteLength);
					// move to next byte length
					nextByteLength = byteLengthScanner.findInLine(PATTERN_BYTE_LEN);
					console.format(" optional byteLength = %d.%n", byteLength);
				}
				else {
					// no optional byte length given for this type ID so use defined
					// byte length for the type defined by the type ID
			    	try {
			    		byteLength = PlcioPcFormat.getTypeByteLength(pcFormatTypeID);
			    	} catch(ABPlcioExceptionBadPlcTagProperties ex) {
			    		console.format(" Given PLCIO type ID of '%c' is NOT VALID.%n", pcFormatTypeID);
			    		byteLength = -1;
			    	}
			    		
		        	console.format(" NO optional byte length using type len, byteLength = %d.%n", byteLength);
				}
				byteLengthTotal += byteLength;
			}
			else {
				// no optional byte length given so use defined byte length for
				// the type defined by the type ID
		    	try {
		    		byteLength = PlcioPcFormat.getTypeByteLength(pcFormatTypeID);
		    	} catch(ABPlcioExceptionBadPlcTagProperties ex) {
		    		console.format(" Given PLCIO type ID of '%c' is NOT VALID.%n", pcFormatTypeID);
		    		byteLength = -1;
		    	}
		    		
	        	console.format(" NO optional byte length using type len, byteLength = %d.%n", byteLength);
	        	byteLengthTotal += byteLength;
			}

        	// move on to next type ID
        	nextTypeID = typeIdScanner.findInLine(PATTERN_TYPE_IDS);
        } // end while

        typeIdScanner.close();
        byteLengthScanner.close();
        
        console.format("Total byte length = %d.%n", byteLengthTotal);
    } // end main

} // end class
