package atst.giss.abplc;

/**
 * Class containing the exception thrown when the propertyDB attributes describing
 * a PLC tag cannot be used to successfully create or work with a {@linkplain PlcTag}
 * object.
 * 
 * @author Alastair Borrowman (OSL)
 */
public class ABPlcioExceptionBadPlcTagProperties extends Exception{

	private static final long serialVersionUID = 1L;

	public ABPlcioExceptionBadPlcTagProperties() {
		super();
	}
	
	/**
	 * Throw an exception due to inability to use propertyDB attributes describing
	 * a PLC tag to successfully create a or work with a {@linkplain PlcTag}.
	 * 
	 * @param message The message to be used to describe the cause of the exception.
	 */
	public ABPlcioExceptionBadPlcTagProperties(String message) {
		super(message);
	}

	public ABPlcioExceptionBadPlcTagProperties(Throwable cause) {
		super(cause);
	}

	public ABPlcioExceptionBadPlcTagProperties(String message, Throwable cause) {
		super(message, cause);
	}
	
} // end class ABPlcioExceptionBadPlcTagProperties
