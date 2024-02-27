package community.revteltech.nfc.dto;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

public class NdefStatus {

    private final int maxSize;
    private final boolean isWritable;
    private final boolean canMakeReadOnly;

    public NdefStatus(int maxSize,
                      boolean isWritable,
                      boolean canMakeReadOnly) {

        this.maxSize = maxSize;
        this.isWritable = isWritable;
        this.canMakeReadOnly = canMakeReadOnly;

    }

    public WritableMap toWritableMap() {

        WritableMap writableMap = Arguments.createMap();
        writableMap.putInt("maxSize", maxSize);
        writableMap.putBoolean("isWritable", isWritable);
        writableMap.putBoolean("canMakeReadOnly", canMakeReadOnly);

        return writableMap;
    }


}
