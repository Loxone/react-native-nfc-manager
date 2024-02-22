package community.revteltech.nfc;

import java.security.SecureRandom;
import java.util.Arrays;

public class ByteUtil {

    private static final SecureRandom secureRandom = new SecureRandom();

    public static byte[] randomThreedesKey() {
        byte[] bytes = randomBytes(8);
        bytes = concatArrays(bytes, bytes);
        return bytes;
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    /**
     * Utility method to concatenate two byte arrays.
     *
     * @param first First array
     * @param rest  Any remaining arrays
     * @return Concatenated copy of input arrays
     */
    private static byte[] concatArrays(byte[] first, byte[]... rest) {
        int totalLength = first.length;
        for (byte[] array : rest) {
            totalLength += array.length;
        }
        byte[] result = Arrays.copyOf(first, totalLength);
        int offset = first.length;
        for (byte[] array : rest) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

}
