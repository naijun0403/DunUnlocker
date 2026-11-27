package android.app;

import android.os.Binder;
import android.os.IBinder;

public class UiAutomationConnection extends Binder implements IUiAutomationConnection {
    public UiAutomationConnection() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public IBinder asBinder() {
        throw new RuntimeException("Stub!");
    }
}
