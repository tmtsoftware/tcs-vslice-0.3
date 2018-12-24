package atst.giss.abplc;

public class TestMain {

    public static void main(String[] args) {
        System.out.println("testing");
        try {

            // create the class ABPlcioMaster
            IABPlcioMaster master = new ABPlcioMaster();

            // setup for a simple command (open)

            PlcioCall plcioCallOpen = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_OPEN, "virtual",
                    "RAW1", 0);


            String[] itemNames = {"i","c"};
            String[] itemTypes = {IPlcTag.PropTypes.REAL.getTypeString(),IPlcTag.PropTypes.STRING.getTypeString()};
            String[] memberValue = {"2.5", "z"};

            //Reading Tag RAW1
            PlcTag plcTag = new PlcTag("RAW1", IPlcTag.DIRECTION_READ, "rc",
                    10000, 2, 5, itemNames, itemTypes);
            PlcioCall plcioCallRead = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_READ, "virtual",
                    "RAW1", 0, plcTag);


            //writing to PLC Tag RAW1
            PlcTag plcTagW = new PlcTag("RAW1", IPlcTag.DIRECTION_WRITE, "rc",
                    10000, 2, 5, itemNames, itemTypes);
            plcTagW.setMemberValues(memberValue);
            PlcioCall plcioCallWrite = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_WRITE, "virtual",
                    "RAW1", 0, plcTagW);


            String[] timesecItemNames = {"time"};
            String[] timesecItemTypes = {IPlcTag.PropTypes.INTEGER.getTypeString()};
            PlcTag plcTagTimesec = new PlcTag("TIMESEC", IPlcTag.DIRECTION_READ, "j",
                    10000, 1, 4, timesecItemNames, timesecItemTypes);
            PlcioCall plcioCallTimesec = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_READ, "virtual",
                    "RAW1", 0, plcTagTimesec);


            PlcioCall plcioCallClose = new PlcioCall(IPlcioCall.PlcioMethodName.PLC_CLOSE, "virtual",
                    "RAW1", 0);

            master.plcAccess(plcioCallOpen);
            master.plcAccess(plcioCallRead);
            master.plcAccess(plcioCallWrite);
            master.plcAccess(plcioCallTimesec);
            master.plcAccess(plcioCallClose);
            System.out.println("testing complete c="+(char)Integer.parseInt(plcTag.getMemberValue("c")));
            System.out.println("testing complete i="+plcTag.getMemberValue("i"));
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }




    }
}
