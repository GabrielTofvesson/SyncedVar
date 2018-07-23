import net.tofvesson.networking.SyncHandler;
import net.tofvesson.networking.SyncedVar;

public class Main {
    @SyncedVar(nonNegative = true)
    public int syncTest = 5;

    @SyncedVar
    public static long staticTest = 90;

    @SyncedVar
    public static float value = 1f;

    @SyncedVar
    public boolean testbool = false;

    @SyncedVar
    public static boolean testbool1 = true;

    public static void main(String[] args){
        Main testObject = new Main();
        SyncHandler sync = new SyncHandler();
        sync.registerSyncObject(testObject);
        sync.registerSyncObject(Main.class);

        byte[] mismatchCheck = sync.generateMismatchCheck();
        byte[] ser = sync.serialize();

        System.out.println("Created and serialized snapshot of field values:\n"+testObject.syncTest+"\n"+staticTest+"\n"+value+"\n"+testObject.testbool+"\n"+testbool1+"\n");

        testObject.syncTest = 20;
        staticTest = 32;
        value = 9.0f;
        testObject.testbool = true;
        testbool1 = false;

        System.out.println("Set a new state of test values:\n"+testObject.syncTest+"\n"+staticTest+"\n"+value+"\n"+testObject.testbool+"\n"+testbool1+"\n");

        /* Swap the registry order
        sync.unregisterSyncObject(testObject);
        sync.unregisterSyncObject(Main.class);
        sync.registerSyncObject(Main.class);
        sync.registerSyncObject(testObject);
        */
        if(!sync.doMismatchCheck(mismatchCheck)) throw new RuntimeException("Target sync mismatch");
        sync.deserialize(ser);

        System.out.println("Deserialized snapshot values:\n"+testObject.syncTest+"\n"+staticTest+"\n"+value+"\n"+testObject.testbool+"\n"+testbool1+"\n");
        System.out.println("Snapshot size: "+ser.length+" bytes");
    }
}
