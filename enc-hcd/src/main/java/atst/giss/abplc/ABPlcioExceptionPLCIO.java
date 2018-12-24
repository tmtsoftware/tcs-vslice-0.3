package atst.giss.abplc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Class containing the exception thrown when unable to communicate with the
 * GIS PLC due to a PLCIO error.
 * 
 * @author Alastair Borrowman (OSL)
 *
 */
public class ABPlcioExceptionPLCIO extends ConnectionException {

	private static final long serialVersionUID = 1L;
	
	private static final Pattern PATTERN_PLCIO_ERROR_CODE = Pattern.compile("[0-9]+");
	private static final Pattern PATTERN_PLCIO_ERROR_MSG = Pattern.compile("PLCIO Err [0-9]+: ");
	
	private final PlcioErrorCode myPlcioErrorCode;
	private final int myPlcioErrorCodeInt;
	private final String myPlcioErrorString;
	
	/**
	 * Enumeration of PLCIO error codes returned when call to PLCIO
	 * function fails.
	 * <p>
	 * Used
	 * to determine appropriate recovery action.
	 * 
	 * @author Alastair Borrowman (OSL)
	 *
	 */
	public enum PlcioErrorCode {
		/** The PLCIO error returned when the tag name used does not exist
		 * in the PLC. */
		BAD_TAG_NAME(20, "PLCIO Err 20 Bad Tag Name"),
		/** The PLCIO error returned when a connection to the PLC could not
		 * be established. */
		CONNECT(45, "PLCIO Err 45 Connect"),
		/** The PLCIO error returned when a request was made to the PLC and
		 * no response was received within timeout. */
		TIMEOUT(48, "PLCIO Err 48 Timeout"),
		/** Enumeration used when no specific enumeration exists for the
		 * PLCIO error returned. */
		NOT_HANDLED(999, "PLCIO Err Not Handled");
		
		private final int private_errorCode;
		private final String private_errorString;
		
		PlcioErrorCode(int errorCode, String errorString) {
			private_errorCode = errorCode;
			private_errorString = errorString;
		}
		
		/**
		 * Return the error code value of this error.
		 * 
		 * @return The error code equating to this error.
		 */
		public int getErrorCode() {
			return this.private_errorCode;
		}
		
		/**
		 * Return the string representing this error
		 * 
		 * @return The string representing this error
		 */
		public String getErrorString() {
			return this.private_errorString;
		}
		
		/**
		 * Translate an integer representing a error code into its
		 * enum value.
		 * 
		 * @param errorCode int equating to a command value.
		 * 
		 * @return The enum value of the command name. Value {@linkplain #NOT_HANDLED}
		 * is returned if cmdInt is not a recognized command.
		 */
		public static PlcioErrorCode parse(int errorCode) {
			for (PlcioErrorCode error : PlcioErrorCode.values()) {
				if (error.getErrorCode() == errorCode) {
					return error;
				}
			}
			return NOT_HANDLED;
		}
		
	} // end enum PlcioErrorCode

	public ABPlcioExceptionPLCIO() {
		super();
		myPlcioErrorCode = PlcioErrorCode.NOT_HANDLED;
		myPlcioErrorCodeInt = -1;
		myPlcioErrorString = null;
	}
	
	/**
	 * Exception thrown in {@linkplain ABPlcioMaster}'s JNI methods accessing
	 * PLCIO functions when the function returns an error.
	 * 
	 * @param message The PLCIO error message.
	 */
	public ABPlcioExceptionPLCIO(String message) {
		super(message);
		myPlcioErrorCode = PlcioErrorCode.NOT_HANDLED;
		myPlcioErrorCodeInt = -1;
		myPlcioErrorString = null;
	}

	public ABPlcioExceptionPLCIO(Throwable cause) {
		super(cause);
		myPlcioErrorCode = PlcioErrorCode.NOT_HANDLED;
		myPlcioErrorCodeInt = -1;
		myPlcioErrorString = null;
	}
	
	/**
	 * Exception thrown when a call
	 * to access the GIS PLC using the PLCIO library, by calling
	 * {@linkplain IABPlcioMaster#plcAccess(IPlcioCall)}, fails.
	 * 
	 * @param message Explanation of PLCIO call being made at time of failure,
	 * includes the PLCIO error status code and message.
	 * @param cause The full exception thrown by {@linkplain IABPlcioMaster#plcAccess(IPlcioCall)}
	 */
	public ABPlcioExceptionPLCIO(String message, Throwable cause) {
		super(message, cause);
		
		// retrieve PLCIO error information from message:
		// the PLCIO error is identified by string using format 'PLCIO Err [0-9]+: ',
		// the digits in the format represent the PLCIO error code,
		// following this is the PLCIO error string
		Matcher errorMatcher = PATTERN_PLCIO_ERROR_MSG.matcher(message);
		if (errorMatcher.find()) {
			String errCodeString = errorMatcher.group();
			// in 'message' the PLCIO error string begins at end of match and
			// continues until end of string
			myPlcioErrorString = message.substring(errorMatcher.end());
			// from the error code string extract the error code number
			Matcher errCodeNumber = PATTERN_PLCIO_ERROR_CODE.matcher(errCodeString);
			if (errCodeNumber.find()) {
				myPlcioErrorCodeInt = Integer.parseInt(errCodeNumber.group());
				myPlcioErrorCode = PlcioErrorCode.parse(myPlcioErrorCodeInt);
			}
			else {
				// failed to find PLCIO error code in message
				myPlcioErrorCode = PlcioErrorCode.NOT_HANDLED;
				myPlcioErrorCodeInt = -1;
				Log.warn("ABPlcioExceptionPLCIO couldn't find PLCIO error code in '"+
						message+", error string extracted successfully='"+myPlcioErrorString+"'");
			}
		}
		else {
			// failed to find PLCIO error in message
			myPlcioErrorCode = PlcioErrorCode.NOT_HANDLED;
			myPlcioErrorCodeInt = -1;
			myPlcioErrorString = message;
			Log.warn("ABPlcioExceptionPLCIO couldn't find PLCIO error information in '"+
					message);
		}
	} // end Constructor
	
	/**
	 * Get the PLCIO error status code returned by PLCIO function
	 * that caused this exception.
	 * 
	 * @return PLCIO error status code.
	 */
	public PlcioErrorCode getPlcioErrorCode() {
		return myPlcioErrorCode;
	} // end getPlcioErrorCode()
	
	/**
	 * Get the PLCIO error status code as the raw integer value
	 * returned by PLCIO function that caused this exception
	 *  
	 * @return The raw PLCIO error status code.
	 */
	public int getPlcioErrorCodeInt() {
		return myPlcioErrorCodeInt;
	}
	
	/**
	 * Get the PLCIO error description returned by PLCIO function
	 * that caused this exception.
	 * 
	 * @return PLCIO error description.
	 */
	public String getPlcioErrorString() {
		return myPlcioErrorString;
	} // end getPlcioErrorString()
	
} // end class ABPlcioExceptionPLCIO
