package android.app;

import android.content.ComponentName;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface IInstrumentationWatcher extends IInterface {
    void instrumentationStatus(ComponentName name, int resultCode, Bundle results) throws RemoteException;

    void instrumentationFinished(ComponentName name, int resultCode, Bundle results) throws RemoteException;

    abstract class Stub extends Binder implements IInstrumentationWatcher {
        public Stub() {
            throw new RuntimeException("Stub!");
        }

        public static IInstrumentationWatcher asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }

        @Override
        public IBinder asBinder() {
            throw new RuntimeException("Stub!");
        }
    }
}
