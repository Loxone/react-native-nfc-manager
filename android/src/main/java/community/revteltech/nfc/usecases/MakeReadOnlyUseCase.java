package community.revteltech.nfc.usecases;

import android.nfc.Tag;
import android.nfc.tech.Ndef;

import androidx.annotation.NonNull;

import com.nxp.nfclib.CardType;
import com.nxp.nfclib.NxpNfcLib;
import com.nxp.nfclib.ntag.INTag213215216;
import com.nxp.nfclib.ntag.NTagFactory;

import java.io.IOException;

/**
 * Use case that makes a given NFC tag read-only based on the NFC card type.
 */
public class MakeReadOnlyUseCase {

    /**
     * Makes the given tag read-only.
     * @param tag The tag which shall be made read-only.
     * @param nfcLib Helper library that will be used for making the tag read-only.
     * @return True if the given  tag could be made read-only, false if not.
     */
    public static boolean invoke(@NonNull Tag tag, @NonNull NxpNfcLib nfcLib) throws IOException {

        CardType cardType = nfcLib.getCardType(tag);
        final boolean result;

        if (cardType == CardType.NTag216) {
            result = handleNTag216(nfcLib);
        } else {
            result = handleNdef(tag);
        }

        return result;

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
