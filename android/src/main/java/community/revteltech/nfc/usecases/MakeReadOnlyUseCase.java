package community.revteltech.nfc.usecases;

import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.util.Log;

import androidx.annotation.NonNull;

import com.nxp.nfclib.CardType;
import com.nxp.nfclib.NxpNfcLib;
import com.nxp.nfclib.desfire.DESFireFactory;
import com.nxp.nfclib.desfire.IDESFireEV1;
import com.nxp.nfclib.ntag.INTag213215216;
import com.nxp.nfclib.ntag.NTagFactory;

import java.io.IOException;

import community.revteltech.nfc.desfire.DesfireUtil;

/**
 * Use case that makes a given NFC tag read-only based on the NFC card type.
 */
public class MakeReadOnlyUseCase {

    private static final String LOG_TAG = "MakeReadOnlyUseCase";

    /**
     * Makes the given tag read-only.
     *
     * @param tag    The tag which shall be made read-only.
     * @param nfcLib Helper library that will be used for making the tag read-only.
     * @return True if the given  tag could be made read-only, false if not.
     */
    public static boolean invoke(@NonNull Tag tag, @NonNull NxpNfcLib nfcLib) throws Exception {

        CardType cardType = nfcLib.getCardType(tag);
        Log.d(LOG_TAG, "Detected NFC card type: " + cardType.name());

        switch (cardType) {
            case NTag216:
                return handleNTag216(nfcLib);
            case DESFireEV1:
                return handleDESFireEV1(nfcLib);
            default:
                return handleNdef(tag);
        }

    }

    private static boolean handleDESFireEV1(@NonNull NxpNfcLib nfcLib) {

        IDESFireEV1 desFireEV1 = DESFireFactory.getInstance().getDESFire(nfcLib.getCustomModules());
        DesfireUtil util = DesfireUtil.getInstance();

        util.makeReadOnly(desFireEV1);

        return true;
    }


    private static boolean handleNTag216(@NonNull NxpNfcLib nfcLib) {
        INTag213215216 nTag216 = NTagFactory.getInstance().getNTAG216(nfcLib.getCustomModules());
        nTag216.makeCardReadOnly();
        return true;
    }

    private static boolean handleNdef(@NonNull Tag tag) throws IOException {

        try (Ndef ndef = Ndef.get(tag)) {
            ndef.connect();
            return ndef.makeReadOnly();
        }

    }

}
