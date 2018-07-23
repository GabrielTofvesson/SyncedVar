import net.tofvesson.networking.SyncHandler;
import net.tofvesson.networking.SyncedVar;

public class Main {
    @SyncedVar(nonNegative = true)
    public int syncTest = 5;

    @SyncedVar
    public static long staticTest = 90;

    @SyncedVar
    public static float value = 1337f;

    public static void main(String[] args){
        Main testObject = new Main();
        SyncHandler sync = new SyncHandler();
        sync.registerSyncObject(testObject);
        //sync.registerSyncObject(Main.class);

        byte[] ser = sync.serialize();

        System.out.println("Created and serialized snapshot of field values:\n"+testObject.syncTest+"\n"+staticTest+"\n"+value+"\n");

        testObject.syncTest = 20;
        staticTest = 32;
        value = 9.0f;

        System.out.println("Set a new state of test values:\n"+testObject.syncTest+"\n"+staticTest+"\n"+value+"\n");

        sync.deserialize(ser);

        System.out.println("Deserialized snapshot values:\n"+testObject.syncTest+"\n"+staticTest+"\n"+value+"\n");
    }
}
