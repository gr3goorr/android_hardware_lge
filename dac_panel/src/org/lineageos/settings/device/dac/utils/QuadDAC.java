package org.lineageos.settings.device.dac.utils;

import android.media.AudioSystem;
import android.os.RemoteException;
import android.os.SystemProperties;

import java.util.ArrayList;

import vendor.lge.hardware.audio.dac.control.V2_0.Feature;
import vendor.lge.hardware.audio.dac.control.V2_0.FeatureStates;
import vendor.lge.hardware.audio.dac.control.V2_0.IDacControl;

public class QuadDAC {

    private static final String TAG = "QuadDAC";

    public QuadDAC() {}

    private static IDacControl dac;

    private static ArrayList<Integer> dac_features;

    public static boolean dac_service_available = false;

    public static void initialize() throws RemoteException {
        dac = IDacControl.getService(true);
        dac_features = dac.getSupportedFeatures();
        dac_service_available = true;
    }

    public static boolean enable() throws RemoteException
    {
        try {
            boolean success = dac.setHifiDacState(true);

            int mode = getDACMode();
            int left_balance = getLeftBalance();
            int right_balance = getRightBalance();
            int avc_vol = getAVCVolume();
            int digital_filter = getDigitalFilter();
            success = setDACMode(mode) && success;
            success = setLeftBalance(left_balance) && success;
            success = setRightBalance(right_balance) && success;
            success = setAVCVolume(avc_vol) && success;
            success = setDigitalFilter(digital_filter) && success;

            // Sound presets are disabled on the open-source audio HAL.
            if(dac_features.contains(Feature.SoundPreset)) {
                int sound_preset = getSoundPreset();
                success = setSoundPreset(sound_preset) && success;
            }

            // Kernel-side implementation needed for custom filters
            if(dac_features.contains(Feature.CustomFilter)) {
                int custom_filter_shape = getCustomFilterShape();
                int custom_filter_symmetry = getCustomFilterSymmetry();
                int[] custom_filter_coefficients = new int[14];
                int i;
                for(i = 0; i < 14; i++) {
                    custom_filter_coefficients[i] = getCustomFilterCoeff(i);
                }
                success = setCustomFilterShape(custom_filter_shape) && success;
                success = setCustomFilterSymmetry(custom_filter_symmetry) && success;
                for(i = 0; i < 14; i++) {
                    success = setCustomFilterCoeff(i, custom_filter_coefficients[i]) && success;
                }
            }
            return success;
        } catch(Exception e) { return false; }
    }

    public static boolean disable() throws RemoteException
    {
        return dac.setHifiDacState(false);
    }

    public static ArrayList<Integer> getSupportedFeatures() {
        return dac_features;
    }

    public static FeatureStates getSupportedFeatureValues(int feature) throws RemoteException
    {
        return dac.getSupportedFeatureValues(feature);
    }

    public static boolean setDACMode(int mode) throws RemoteException
    {
        return dac.setFeatureValue(Feature.HifiMode, mode);
    }

    public static int getDACMode() throws RemoteException
    {
        return dac.getFeatureValue(Feature.HifiMode);
    }

    public static boolean setAVCVolume(int avc_volume) throws RemoteException
    {
        return dac.setFeatureValue(Feature.AVCVolume, avc_volume);
    }

    public static int getAVCVolume() throws RemoteException
    {
        return dac.getFeatureValue(Feature.AVCVolume);
    }

    public static boolean setDigitalFilter(int filter) throws RemoteException
    {
        boolean success = dac.setFeatureValue(Feature.DigitalFilter, filter);
        if(dac_features.contains(Feature.CustomFilter) && filter == 3) {/* Custom filter */
            /*
            * If it's a custom filter, we need to apply its settings. Any of the functions
            * below should suffice since it'll load all settings from memory by parsing its
            * data.
            */
            success = setCustomFilterShape(getCustomFilterShape()) && success;
        }
        return success;
    }

    public static int getDigitalFilter() throws RemoteException
    {
        return dac.getFeatureValue(Feature.DigitalFilter);
    }

    public static boolean setSoundPreset(int preset) throws RemoteException
    {
        if(dac_features.contains(Feature.SoundPreset))
            return dac.setFeatureValue(Feature.SoundPreset, preset);
        return false;
    }

    public static int getSoundPreset() throws RemoteException
    {
        if(!dac_features.contains(Feature.SoundPreset))
            return 0;
        return dac.getFeatureValue(Feature.SoundPreset);
    }

    public static boolean setLeftBalance(int balance) throws RemoteException
    {
        return dac.setFeatureValue(Feature.BalanceLeft, balance);
    }

    public static int getLeftBalance() throws RemoteException
    {
        return dac.getFeatureValue(Feature.BalanceLeft);
    }

    public static boolean setRightBalance(int balance) throws RemoteException
    {
        return dac.setFeatureValue(Feature.BalanceRight, balance);
    }

    public static int getRightBalance() throws RemoteException
    {
        return dac.getFeatureValue(Feature.BalanceRight);
    }

    public static boolean isEnabled() throws RemoteException
    {
        return dac.getHifiDacState();
    }

    public static boolean setCustomFilterShape(int shape) throws RemoteException
    {
        return dac.setCustomFilterShape(shape);
    }

    public static int getCustomFilterShape() throws RemoteException
    {
        return dac.getCustomFilterShape();
    }

    public static boolean setCustomFilterSymmetry(int symmetry) throws RemoteException
    {
        return dac.setCustomFilterSymmetry(symmetry);
    }

    public static int getCustomFilterSymmetry() throws RemoteException
    {
        return dac.getCustomFilterSymmetry();
    }

    public static boolean setCustomFilterCoeff(int coeffIndex, int coeff_val) throws RemoteException
    {
        return dac.setCustomFilterCoeff(coeffIndex, coeff_val);
    }

    public static int getCustomFilterCoeff(int coeffIndex) throws RemoteException
    {
        return dac.getCustomFilterCoeff(coeffIndex);
    }

    public static boolean resetCustomFilterCoeffs() throws RemoteException
    {
        return dac.resetCustomFilterCoeffs();
    }
}
