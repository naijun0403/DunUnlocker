package com.android.internal.telephony;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.telephony.SubscriptionInfo;
import java.util.List;

public interface ISub extends IInterface {
    List<SubscriptionInfo> getActiveSubscriptionInfoList(
            String callingPackage,
            String callingFeatureId,
            boolean isForAllProfiles
    ) throws RemoteException;

    abstract class Stub extends Binder implements ISub {
        public static ISub asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
