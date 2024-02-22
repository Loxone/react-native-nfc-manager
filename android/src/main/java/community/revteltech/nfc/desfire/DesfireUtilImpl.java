package community.revteltech.nfc.desfire;


import android.util.Log;

import androidx.annotation.NonNull;

import com.nxp.nfclib.KeyType;
import com.nxp.nfclib.defaultimpl.KeyData;
import com.nxp.nfclib.desfire.DESFireFile;
import com.nxp.nfclib.desfire.EV1ApplicationKeySettings;
import com.nxp.nfclib.desfire.EV1PICCKeySettings;
import com.nxp.nfclib.desfire.IDESFireEV1;

import java.security.Key;

import javax.crypto.spec.SecretKeySpec;

import community.revteltech.nfc.ByteUtil;

class DesfireUtilImpl implements DesfireUtil {

    private static final String LOG_TAG = "DesfireUtil";

    private static DesfireUtilImpl instance;

    @NonNull
    private final KeyData masterKeyData;
    @NonNull
    private final KeyData lockedKeyData;


    private final byte[] MASTER_KEY_BYTES = new byte[16]; // master keys are always zero by default

    /**
     * enabling debug mode does the following: - make read-only can be "reverted" (only if tag was
     * made read-only with debug mode) - just start "make read-only" and touch the tag again -
     * read-only is not correctly displayed (eg. in error case when writing to a locked tag)
     */
    private static final boolean DEBUG_DESFIRE = false;

    private final byte[] LOCKED_KEY_BYTES =
            (DEBUG_DESFIRE
                    ? (new byte[]{
                    (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05,
                    (byte) 0x06, (byte) 0x07, (byte) 0x08,
                    (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05,
                    (byte) 0x06, (byte) 0x07, (byte) 0x08,
            })
                    : ByteUtil.randomThreedesKey());


    private DesfireUtilImpl() {
        masterKeyData = createKeyData(MASTER_KEY_BYTES);
        lockedKeyData = createKeyData(LOCKED_KEY_BYTES);
    }

    private KeyData createKeyData(byte[] bytes) {
        Key key = new SecretKeySpec(bytes, "DESede");
        KeyData keyData = new KeyData();
        keyData.setKey(key);

        return keyData;
    }

    @NonNull
    static DesfireUtilImpl getInstance() {

        if (instance == null) {
            instance = new DesfireUtilImpl();
        }

        return instance;
    }

    public void makeReadOnly(IDESFireEV1 desFireEV1) {

        try {
            desFireEV1.selectApplication(0);
            desFireEV1.authenticate(0, IDESFireEV1.AuthType.ISO, KeyType.THREEDES, masterKeyData);
            Log.d(LOG_TAG, "authenticated!");

            for (int applicationID : desFireEV1.getApplicationIDs()) {
                adoptApplication(desFireEV1, applicationID, true);
            }
            Log.d(LOG_TAG, "all applications changed!");

            desFireEV1.selectApplication(0);
            desFireEV1.authenticate(0, IDESFireEV1.AuthType.ISO, KeyType.THREEDES, masterKeyData);
            changeApplicationKey0(desFireEV1, true);
            Log.d(LOG_TAG, "piccKey changed!");

            // change picc key settings
            adoptPiccKeySettings(desFireEV1, true);

            Log.d(LOG_TAG, "locked!!");


        } catch (Exception error) {

            Log.e(LOG_TAG, "Error while making DesfireEV1 read-only.", error);

            if (DEBUG_DESFIRE) {
                revertReadOnly(desFireEV1);
            }

            throw error;
        }

    }

    /**
     * <ul>
     *   <li>authenticates
     *   <li>updates the application key settings
     *   <li>goes through all files and updates the file access rights
     *   <li>finally adopts the application master key (0)
     * </ul>
     *
     * @param locked locked means, the application will be locked (not "is locked")
     */
    private void adoptApplication(IDESFireEV1 desFireEV1, int applicationID, boolean locked) {
        Log.d(LOG_TAG, "adopt application: " + applicationID);
        try {
            desFireEV1.selectApplication(applicationID);
            desFireEV1.authenticate(
                    0,
                    IDESFireEV1.AuthType.ISO,
                    KeyType.THREEDES,
                    locked ? masterKeyData : lockedKeyData);

            for (byte fileID : desFireEV1.getFileIDs()) {
                updateFileSettings(desFireEV1, fileID, locked);
            }
            changeApplicationKey0(desFireEV1, locked);
            updateApplicationKeySettings(desFireEV1, applicationID, locked);
            Log.d(LOG_TAG, "Adopting of application finished successfully.");
        } catch (Exception error) {
            Log.e(LOG_TAG, "Adopting of application failed.", error);
        }
    }

    /**
     * - goes trough all files in the selected application - updates the file settings (only for
     * FileType.DataStandard)
     *
     * @param locked locked means, the file settings will be set to locked (not "are locked")
     */
    private void updateFileSettings(IDESFireEV1 desFireEV1, byte fileID, boolean locked) {
        DESFireFile.FileSettings fileSettings = desFireEV1.getFileSettings(fileID);
        DESFireFile.FileType fileType = fileSettings.getType();
        if (fileType != DESFireFile.FileType.DataStandard) {
            Log.d(LOG_TAG, "fileType " + fileType + " not handled");
            return;
        }
        DESFireFile.StdDataFileSettings stdDataFileSettings =
                (DESFireFile.StdDataFileSettings) fileSettings;
        DESFireFile.StdDataFileSettings newFileSettings =
                new DESFireFile.StdDataFileSettings(
                        stdDataFileSettings.getComSettings(),
                        // 0xE: free access. 0xF: access denied. 0x00 to 0x0d -- authentication
                        // required with the key number.
                        (byte) 0xE, // read
                        (locked ? (byte) 0xF : (byte) 0xE), // write
                        (locked ? (byte) 0xF : (byte) 0xE), // read-write
                        (locked
                                ? (DEBUG_DESFIRE ? (byte) 0x00 : (byte) 0xF)
                                : (byte) 0xE), // change
                        stdDataFileSettings.getFileSize());
        desFireEV1.changeFileSettings(fileID, newFileSettings);
        Log.d(LOG_TAG, "file " + fileID + " updated");
    }

    /**
     * <ul>
     *   <li>changes the key
     *   <li>checks, if authentication works (with the new key)
     * </ul>
     *
     * @param locked locked means, the key will be changed to the lockedKey (not "is the locked
     *               key")
     */
    private void changeApplicationKey0(IDESFireEV1 desFireEV1, boolean locked) {
        /*
           cardkeyNumber - key number to change.
           keyType - Key type of the new Key
           oldKey - old key of length 16/24 bytes depends on key type.
               if type is AES128 then, key length should be 16 bytes.
               if type is THREEDES then, [0 to 7 ] equal to [ 8 to 15 ] bytes of the 16 byte key.
               if type is TWO_KEY_THREEDES then, [0 to 7 ] not equal to [ 8 to 15 ] bytes of the 16 byte key.
               if type is THREE_KEY_THREEDES then, key data should be 24 bytes but key data not necessarily follow the pattern explained for THREEDES, TWO_KEY_THREEDES
           newKey - new key of length 16/24 bytes depends on key type.
           newKeyVersion - new key version byte.
        */
        if (locked) {
            desFireEV1.changeKey(0, KeyType.THREEDES, MASTER_KEY_BYTES, LOCKED_KEY_BYTES, (byte) 0);
            desFireEV1.authenticate(0, IDESFireEV1.AuthType.ISO, KeyType.THREEDES, lockedKeyData);
        } else {
            desFireEV1.changeKey(0, KeyType.THREEDES, LOCKED_KEY_BYTES, MASTER_KEY_BYTES, (byte) 0);
            desFireEV1.authenticate(0, IDESFireEV1.AuthType.ISO, KeyType.THREEDES, masterKeyData);
        }
        Log.d(LOG_TAG, "key changed!");
    }

    /**
     * @param locked locked means, the picc key settings will be set to locked (not "are locked")
     */
    private void adoptPiccKeySettings(IDESFireEV1 desFireEV1, boolean locked) { // TODO lock
        EV1PICCKeySettings piccKeySettings =
                new EV1PICCKeySettings.Builder()
                        .setAuthenticationRequiredForApplicationManagement(locked)
                        .setAuthenticationRequiredForDirectoryConfigurationData(
                                false) // needed for read-only access!
                        .setPiccMasterKeyChangeable(!locked || DEBUG_DESFIRE)
                        .setPiccKeySettingsChangeable(!locked || DEBUG_DESFIRE)
                        .build();
        desFireEV1.changeKeySettings(piccKeySettings);
        Log.d(LOG_TAG, "piccKeySettings changed!");
    }

    /**
     * @param locked locked means, the application key settings will be set to locked (not "are
     *               locked")
     */
    private void updateApplicationKeySettings(IDESFireEV1 desFireEV1, int appID, boolean locked) {
        EV1ApplicationKeySettings appKeySettings =
                new EV1ApplicationKeySettings.Builder()
                        .setAppKeySettingsChangeable(DEBUG_DESFIRE || !locked)
                        .setAppMasterKeyChangeable(DEBUG_DESFIRE || !locked)
                        .setAuthenticationRequiredForDirectoryConfigurationData(locked)
                        .setAuthenticationRequiredForFileManagement(locked)
                        .build();
        desFireEV1.changeKeySettings(appKeySettings);
        Log.d(LOG_TAG, "app key settings changed for " + appID);
    }

    /**
     * reverts read-only state - only possible, if the tag was write-protected while DEBUG_DESFIRE
     * was active!
     */
    private void revertReadOnly(IDESFireEV1 desFireEV1) throws Error {

        desFireEV1.selectApplication(0);
        desFireEV1.authenticate(0, IDESFireEV1.AuthType.ISO, KeyType.THREEDES, lockedKeyData);
        Log.d(LOG_TAG, "authenticated!");

        // first, change key settings, otherwise key couldn't be changed
        adoptPiccKeySettings(desFireEV1, false);

        for (int applicationID : desFireEV1.getApplicationIDs()) {
            adoptApplication(desFireEV1, applicationID, false);
        }
        Log.d(LOG_TAG, "all applications changed!");

        desFireEV1.selectApplication(0);
        desFireEV1.authenticate(0, IDESFireEV1.AuthType.ISO, KeyType.THREEDES, lockedKeyData);
        changeApplicationKey0(desFireEV1, false);
        Log.d(LOG_TAG, "piccKey changed!");


        Log.d(LOG_TAG, "tag unlocked!");

    }
}
