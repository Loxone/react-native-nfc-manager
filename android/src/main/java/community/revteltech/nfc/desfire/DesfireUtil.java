package community.revteltech.nfc.desfire;

import com.nxp.nfclib.desfire.IDESFireEV1;

public interface DesfireUtil {
    /**
     * Makes the given tag read-only.
     */
    void makeReadOnly(IDESFireEV1 desFireEV1);
    boolean isReadOnly(IDESFireEV1 desFireEV1);

    static DesfireUtil getInstance() {
        return DesfireUtilImpl.getInstance();
    }
}

