import net.tofvesson.networking.*;

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

    @SyncedVar
    public static DiffTracked<Integer> tracker = new DiffTracked<>(5, Integer.class);

    @SyncedVar
    public static DiffTrackedArray<Long> tracker2 = new DiffTrackedArray<>(Long.class, 8, i -> (long)i);

    public static void main(String[] args){
        Main testObject = new Main();
        SyncHandler.Companion.registerSerializer(DiffTrackedSerializer.Companion.getSingleton());

        SyncHandler sync = new SyncHandler();
        sync.registerSyncObject(testObject);
        sync.registerSyncObject(Main.class);

        // Generate mismatch check
        byte[] mismatchCheck = sync.generateMismatchCheck();

        // Trigger change flags
        tracker.setValue(9);
        tracker2.set(3L, 2);
        tracker2.set(5L, 0);

        // Generate snapshot of values to serialize
        byte[] ser = sync.serialize();

        System.out.print("Created and serialized snapshot of field values:\n\t"+
                testObject.syncTest+"\n\t"+
                staticTest+"\n\t"+
                value+"\n\t"+
                testObject.testbool+"\n\t"+
                testbool1+"\n\t"+
                test[0]+"\n\t"+
                test[1]+"\n\t"+
                tracker
        );
        for(Long value : tracker2.getValues())
            System.out.print("\n\t"+value);
        System.out.println('\n');

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
        tracker.setValue(400);
        tracker2.set(8L, 2);
        tracker2.set(100L, 0);

        System.out.print("Set a new state of test values:\n\t"+
                testObject.syncTest+"\n\t"+
                staticTest+"\n\t"+
                value+"\n\t"+
                testObject.testbool+"\n\t"+
                testbool1+"\n\t"+
                test[0]+"\n\t"+
                test[1]+"\n\t"+
                test[2]+"\n\t"+
                tracker
        );
        for(Long value : tracker2.getValues())
            System.out.print("\n\t"+value);
        System.out.println('\n');

        // Do mismatch check
        if(!sync.doMismatchCheck(mismatchCheck)) throw new RuntimeException("Target sync mismatch");

        // Load snapshot values back
        sync.deserialize(ser);

        System.out.print("Deserialized snapshot values:\n\t"+
                testObject.syncTest+"\n\t"+
                staticTest+"\n\t"+
                value+"\n\t"+
                testObject.testbool+"\n\t"+
                testbool1+"\n\t"+
                test[0]+"\n\t"+
                test[1]+"\n\t"+
                tracker);
        for(Long value : tracker2.getValues())
            System.out.print("\n\t"+value);
        System.out.println("\n\nSnapshot size: "+ser.length+" bytes");

    }
}
