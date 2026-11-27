package com.android.internal.telephony;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.PersistableBundle;
import android.os.RemoteException;

public interface ICarrierConfigLoader extends IInterface {
    PersistableBundle getConfigForSubId(int subId, String callingPackage) throws RemoteException;

    PersistableBundle getConfigForSubIdWithFeature(
            int subId,
            String callingPackage,
            String callingFeatureId
    ) throws RemoteException;

    void overrideConfig(
            int subId,
            PersistableBundle overrides,
            boolean persistent
    ) throws RemoteException;

    abstract class Stub extends Binder implements ICarrierConfigLoader {
        public static ICarrierConfigLoader asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
