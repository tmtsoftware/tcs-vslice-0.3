package atst.giss.abplc;

/**
 * The class PlcioPcFormatType is an immutable class used for storing
 * type information about PLC tags contained in a PLCIO <i>pcFormat</i> string.
 * PLCIO uses the pcFormat string to describe the data types contained
 * in a PLC tag so that it can correctly encode/decode PLC tag data.
 * The {@code abplc} package uses the same format string to
 * communicate tag data information between collaborating objects. The
 * abstract class {@linkplain PlcioPcFormat} contains pcFormat
 * constants and helper methods.
 * <p>
 * A pcFormat string is composed of one or more <i>type descriptions</i>. Each
 * type description is compossed of a PLCIO type identifier (typeId) and optional length
 * in bytes (see {@linkplain PlcioPcFormat} for details).
 * This class is used to store information about individual type descriptors of a
 * pcFormat string. Therefore, numerous objects of this class may be
 * instantiated to store information about all types contained in a
 * complete pcFormat string. The Java ArrayList collection is the most
 * suitable structure for storing all PlcioPcFormatType objects relating
 * to a complete pcFormat string, as the order in which they are contained
 * in the string must be maintained for correct encoding/decoding of PLC tags.
 * The abstract helper class {@linkplain PlcioPcFormat} contains methods using
 * and returning Java ArrayList collections.
 * <p>
 * This class is defined <i>package-private</i> (no explicit access modifier)
 * as it is only to be used by classes of the atst.giss.abplc package.
 * 
 * @author Alastair Borrowman (OSL)
 */
class PlcioPcFormatType implements IPlcioPcFormatType {
	
	// Private final instance variables
	private final char typeID;
	private final int byteLen;
    private final int numberOfMembers;
    private final String typeIdStr;
    private final String typeDescriptor;

    /**
     * Construct a PlcioPcFormatType using given typeId and byte length.
     * @param id	The PLCIO pcFormat typeId of this pcFormat string type descriptor.
     * @param len	The length in bytes of this type descriptor of the pcFormat string.
     * @param lenStr	The type's optional byte length or null if no byte length given
     * 		in type descriptor.
     * 
     * @throws ABPlcioExceptionBadPlcTagProperties
     */
    public PlcioPcFormatType(char id, int len, String lenStr) throws ABPlcioExceptionBadPlcTagProperties {
    	typeID = id;

    	if (len <= 0) {
    		throw new ABPlcioExceptionBadPlcTagProperties("The length in bytes of the pcFormat "+
    	    		" type descriptor must be greater than zero");
    	}
    	byteLen = len;
    	
    	switch (id) {
    	case PlcioPcFormat.TYPE_C:
    	    typeIdStr = PlcioPcFormat.TYPE_STR_C;
    	    if ((byteLen % PlcioPcFormat.BYTE_LEN_C) != 0)
    	    	numberOfMembers = -1;
    	    else 
    	    	numberOfMembers = byteLen / PlcioPcFormat.BYTE_LEN_C;
    	    break;
    	case PlcioPcFormat.TYPE_I:
    	    typeIdStr = PlcioPcFormat.TYPE_STR_I;
    	    if ((byteLen % PlcioPcFormat.BYTE_LEN_I) != 0)
    	    	numberOfMembers = -1;
    	    else 
    	    	numberOfMembers = byteLen / PlcioPcFormat.BYTE_LEN_I;
    	    break;
    	case PlcioPcFormat.TYPE_J:
    	    typeIdStr = PlcioPcFormat.TYPE_STR_J;
    	    if ((byteLen % PlcioPcFormat.BYTE_LEN_J) != 0)
    	    	numberOfMembers = -1;
    	    else 
    	    	numberOfMembers = byteLen / PlcioPcFormat.BYTE_LEN_J;
    	    break;
    	case PlcioPcFormat.TYPE_Q:
    	    typeIdStr = PlcioPcFormat.TYPE_STR_Q;
    	    if ((byteLen % PlcioPcFormat.BYTE_LEN_Q) != 0)
    	    	numberOfMembers = -1;
    	    else 
    	    	numberOfMembers = byteLen / PlcioPcFormat.BYTE_LEN_Q;
    	    break;
    	case PlcioPcFormat.TYPE_R:
    	    typeIdStr = PlcioPcFormat.TYPE_STR_R;
    	    if ((byteLen % PlcioPcFormat.BYTE_LEN_R) != 0)
    	    	numberOfMembers = -1;
    	    else 
    	    	numberOfMembers = byteLen / PlcioPcFormat.BYTE_LEN_R;
    	    break;
    	case PlcioPcFormat.TYPE_D:
    	    typeIdStr = PlcioPcFormat.TYPE_STR_D;
    	    if ((byteLen % PlcioPcFormat.BYTE_LEN_D) != 0)
    	    	numberOfMembers = -1;
    	    else 
    	    	numberOfMembers = byteLen / PlcioPcFormat.BYTE_LEN_D;
    	    break;
    	default:
    	    // unrecognized type ID
    	    typeIdStr = null;
    	    numberOfMembers = 0;
    	    throw new ABPlcioExceptionBadPlcTagProperties("The type ID '"+String.valueOf(id)+
    	    		"' is not a valid PLCIO type ID");
    	} // end switch
    	
    	// given byte length is not suitable for given PLCIO type
    	if (numberOfMembers < 0) {
    		throw new ABPlcioExceptionBadPlcTagProperties("The length in bytes of the pcFormat "+
    				" type descriptor of "+byteLen+" bytes is not valid for the PLCIO type ID '"+
    				id+" ("+typeIdStr+")");
    	}
    	
    	if (lenStr == null) {
    		typeDescriptor = Character.toString(typeID);
    	}
    	else {
    		typeDescriptor = Character.toString(typeID) + lenStr;
    	}
    } // end Constructor

	@Override
	public char getTypeId() {
		return typeID;
	}

	@Override
	public int getByteLen() {
		return byteLen;
	}

	@Override
	public int getNumberOfMembers() {
		return numberOfMembers;
	}

	@Override
	public String getTypeIdStr() {
		return typeIdStr;
	}

    @Override
    public String toString() {
    	return "type descriptor '" + typeDescriptor + "' type '" + this.typeID +
    	    "' ("  + this.typeIdStr +
    	    "), number of members = " + this.numberOfMembers +
    	    ", byte length = " + this.byteLen;
        } // end method toString

} // end class PlcioPcFormatType

