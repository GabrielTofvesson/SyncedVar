import net.tofvesson.networking.SyncHandler;
import net.tofvesson.networking.SyncedVar;

public class Main {
    @SyncedVar("NonNegative")
    public int syncTest = 5;

    @SyncedVar
    public static long staticTest = 90;

    @SyncedVar
    public static float value = 1f;

    @SyncedVar
    public boolean testbool = false;

    @SyncedVar
    public static boolean testbool1 = true;

    @SyncedVar
    public static boolean[] test = {true, false};

    public static void main(String[] args){
        Main testObject = new Main();
        SyncHandler sync = new SyncHandler();
        sync.registerSyncObject(testObject);
        sync.registerSyncObject(Main.class);

        // Generate mismatch check
        byte[] mismatchCheck = sync.generateMismatchCheck();

        // Generate snapshot of values to serialize
        byte[] ser = sync.serialize();

        System.out.println("Created and serialized snapshot of field values:\n\t"+
                testObject.syncTest+"\n\t"+
                staticTest+"\n\t"+
                value+"\n\t"+
                testObject.testbool+"\n\t"+
                testbool1+"\n\t"+
                test[0]+"\n\t"+
                test[1]+"\n"
        );

        // Modify all the values
        testObject.syncTest = 20;
        staticTest = 32;
        value = 9.0f;
        testObject.testbool = true;
        testbool1 = false;
        test = new boolean[3];
        test[0] = false;
        test[1] = true;
        test[2] = true;

        System.out.println("Set a new state of test values:\n\t"+
                testObject.syncTest+"\n\t"+
                staticTest+"\n\t"+
                value+"\n\t"+
                testObject.testbool+"\n\t"+
                testbool1+"\n\t"+
                test[0]+"\n\t"+
                test[1]+"\n\t"+
                test[2]+"\n"
        );

        // Do mismatch check
        if(!sync.doMismatchCheck(mismatchCheck)) throw new RuntimeException("Target sync mismatch");

        // Load snapshot values back
        sync.deserialize(ser);

        System.out.println("Deserialized snapshot values:\n\t"+
                testObject.syncTest+"\n\t"+
                staticTest+"\n\t"+
                value+"\n\t"+
                testObject.testbool+"\n\t"+
                testbool1+"\n\t"+
                test[0]+"\n\t"+
                test[1]+"\n\n" +
                "Snapshot size: \"+ser.length+\" bytes"
        );
    }
}
