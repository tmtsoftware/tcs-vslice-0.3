package atst.giss.abplc;

/**
 * Interface describing PLCIO <i>pcFormat</i> strings as represented by objects
 * of type {@linkplain PlcioPcFormatType}.
 * 
 * @author Alastair Borrowman (OSL)
 *
 */
public interface IPlcioPcFormatType {

	/**
	 * Get the typeId describing the type of this type descriptor of the pcFormat string.
	 * <p>
	 * E.g. if the type of this type descriptor is int the typeId will be {@code j}.
   * @return the typeId
	 */
	public char getTypeId();
	
	/**
	 * Get the byte length used to store all data described by this type.
   * @return byte length used to store all data described by this type.
	 */
	public int getByteLen();
	
	/**
	 * Get the number of members.
	 * <p>
	 * E.g. if the typeId is {@code j} (int),
	 * and byteLen is 8, then number of members will be 2
	 * (total byte length divided by byte length of type int (4)).
   * @return number of members.
	 */
	public int getNumberOfMembers();
	
	/**
	 * Get a String representation of the type.
	 * <p>
	 * E.g. if the typeID is {@code j} the
	 * typeIdStr will be int.
	 * @return String representation of the type.
	 */
	public String getTypeIdStr();
}
