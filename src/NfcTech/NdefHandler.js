import {Platform} from 'react-native';
import {callNative} from '../NativeNfcManager';
import {handleNativeException} from '../NfcError';

const NdefStatus = {
  NotSupported: 1,
  ReadWrite: 2,
  ReadOnly: 3,
};

class NdefHandler {
  async writeNdefMessage(bytes, options) {
  
    const defaultOptions = { reconnectAfterWrite: false };
    return handleNativeException(
        callNative('writeNdefMessage', [
            bytes, 
            {...defaultOptions, ...options}
        ])
    );
  
  }

  async getNdefMessage() {
    return handleNativeException(callNative('getNdefMessage'));
  }

  async makeReadOnly() {
    const result = await handleNativeException(callNative('makeReadOnly'));

    return result === true;
  }

  async getNdefStatus() {
    if (Platform.OS === 'ios') {
      return handleNativeException(callNative('queryNDEFStatus'));
    } else {
      try {
        const result = await handleNativeException(callNative('getNdefStatus'));
        return {
          status: result.isWritable
            ? NdefStatus.ReadWrite
            : NdefStatus.ReadOnly,
          capacity: result.maxSize,
        };
      } catch (ex) {
        return {
          status: NdefStatus.NotSupported,
          capacity: 0,
        };
      }
    }
  }

  async getCachedNdefMessageAndroid() {
    return handleNativeException(callNative('getCachedNdefMessage'));
  }
}

export {NdefHandler, NdefStatus};
