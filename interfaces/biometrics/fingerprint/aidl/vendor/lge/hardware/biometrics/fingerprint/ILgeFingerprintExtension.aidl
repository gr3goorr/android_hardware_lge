package vendor.lge.hardware.biometrics.fingerprint;

import vendor.lge.hardware.biometrics.fingerprint.FingerCmdLge;


@VintfStability
interface ILgeFingerprintExtension {

    void extraCmd(in FingerCmdLge cmd, in byte[] cmdParam);
}
