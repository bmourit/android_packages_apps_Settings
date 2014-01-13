/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings;

import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;
import static android.provider.Settings.System.SCREEN_OFF_ANIMATION;
import static android.provider.Settings.System.ACCELEROMETER_COORDINATE_MODE;

import android.app.ActivityManagerNative;
import android.app.Dialog;
import android.app.AlertDialog.Builder;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.content.res.TypedArray;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.widget.CompoundButton;
import android.widget.Checkable;
import android.widget.Switch;
import android.view.View;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.AttributeSet;
import android.util.Log;
import java.util.List;

import com.android.internal.util.MemInfoReader;
import com.android.internal.view.RotationPolicy;
import com.android.settings.DreamSettings;

import java.util.ArrayList;

import com.android.settings.tvout.TvoutUtils;
import com.android.settings.tvout.TvoutScreenResizeActivity;

public class DisplaySettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener, OnPreferenceClickListener {
    private static final String TAG = "DisplaySettings";

    /** If there is no setting in the provider, use this. */
    private static final int FALLBACK_SCREEN_TIMEOUT_VALUE = 30000;

    private static final String KEY_SCREEN_TIMEOUT = "screen_timeout";
    private static final String KEY_ACCELEROMETER = "accelerometer";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_NOTIFICATION_LIGHT = "notification_light";
    private static final String KEY_BATTERY_LIGHT = "battery_light";
    private static final String KEY_SCREEN_SAVER = "screensaver";
    private static final String KEY_SCREEN_OFF_ANIMATION = "screen_off_animation";
    private static final String KEY_WAKE_WHEN_PLUGGED_OR_UNPLUGGED = "wake_when_plugged_or_unplugged";
    private static final String KEY_TVOUT_SETTINGS = "tvout_settings";
    private static final String KEY_CVBS_MODE_SELECTOR = "cvbs_mode_selector";
    private static final String KEY_HDMI_MODE_SELECTOR = "hdmi_mode_selector";
    private static final String KEY_TVOUT_SCREEN_RESIZE = "tvout_screen_resize";
    private static final String KEY_COORDINATE = "accelerometer_coordinate";
    private static final String KEY_CALIBRATION = "accelerometer_calibration";
    private static final String KEY_ENHANCED_COLOR_SYSTEM = "toggle_enhanced_color_system";

    private static final int DLG_GLOBAL_CHANGE_WARNING = 1;
    public static final String TVOUT_STATUS_PROPERTY = "hw.config.hdmi_status"; 
    private static final String ENHANCED_COLOR_PROPERTY = "sys.image_enhanced_system";
    private DisplayManager mDisplayManager;

    private CheckBoxPreference mAccelerometer;
    private WarnedListPreference mFontSizePref;
    private Preference mNotificationLight;
    private Preference mChargingLight;
    private CheckBoxPreference mWakeWhenPluggedOrUnplugged;

    private final Configuration mCurConfig = new Configuration();
    
    private ListPreference mScreenTimeoutPreference;
    private Preference mScreenSaverPreference;

    private ListPreference mScreenOffAnimationPreference;

    private ListPreference mCoordinate;
    private CheckBoxPreference mEnhancedColor;
    private PreferenceCategory mTvoutSettingsCategory;
    private ListPreference mCvbsModePref;
    private TvoutSettingsListPreference mHdmiModePref;
    private Preference mTvoutScreenResize;
    private boolean mSupportCvbs;
    private boolean mSupportHdmi;
    private Preference mGsensorCalib;

    private TvoutUtils.CvbsUtils mCvbsUtils;
    private TvoutUtils.HdmiUtils mHdmiUtils;
    static DisplaySettings mDisplay;
    private final RotationPolicy.RotationPolicyListener mRotationPolicyListener =
            new RotationPolicy.RotationPolicyListener() {
        @Override
        public void onChange() {
            updateAccelerometerRotationCheckbox();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSupportCvbs = getPackageManager().hasSystemFeature(PackageManager.FEATURE_TVOUT_CVBS);
        mSupportHdmi = getPackageManager().hasSystemFeature(PackageManager.FEATURE_TVOUT_HDMI);
        ContentResolver resolver = getActivity().getContentResolver();

        addPreferencesFromResource(R.xml.display_settings);

        mAccelerometer = (CheckBoxPreference) findPreference(KEY_ACCELEROMETER);
        mAccelerometer.setPersistent(false);
        if (!RotationPolicy.isRotationSupported(getActivity())
                || RotationPolicy.isRotationLockToggleSupported(getActivity())) {
            // If rotation lock is supported, then we do not provide this option in
            // Display settings.  However, is still available in Accessibility settings,
            // if the device supports rotation.
            getPreferenceScreen().removePreference(mAccelerometer);
        }

        mScreenSaverPreference = findPreference(KEY_SCREEN_SAVER);
        if (mScreenSaverPreference != null
                && getResources().getBoolean(
                        com.android.internal.R.bool.config_dreamsSupported) == false) {
            getPreferenceScreen().removePreference(mScreenSaverPreference);
        }

        mScreenTimeoutPreference = (ListPreference) findPreference(KEY_SCREEN_TIMEOUT);
        final long currentTimeout = Settings.System.getLong(resolver, SCREEN_OFF_TIMEOUT,
                FALLBACK_SCREEN_TIMEOUT_VALUE);
        addNeverTimeout(getActivity(), mScreenTimeoutPreference);
        mScreenTimeoutPreference.setValue(String.valueOf(currentTimeout));
        mScreenTimeoutPreference.setOnPreferenceChangeListener(this);
        disableUnusableTimeouts(mScreenTimeoutPreference);
        updateTimeoutPreferenceDescription(currentTimeout);

        mScreenOffAnimationPreference = (ListPreference) findPreference(KEY_SCREEN_OFF_ANIMATION);
        final int currentAnimation = Settings.System.getInt(resolver, SCREEN_OFF_ANIMATION,
                1 /* CRT-off */);
        mScreenOffAnimationPreference.setValue(String.valueOf(currentAnimation));
        mScreenOffAnimationPreference.setOnPreferenceChangeListener(this);
        updateScreenOffAnimationPreferenceDescription(currentAnimation);

        mFontSizePref = (WarnedListPreference) findPreference(KEY_FONT_SIZE);
        mFontSizePref.setOnPreferenceChangeListener(this);
        mFontSizePref.setOnPreferenceClickListener(this);
        mNotificationLight = (Preference) findPreference(KEY_NOTIFICATION_LIGHT);
        if (mNotificationLight != null
                && getResources().getBoolean(
                        com.android.internal.R.bool.config_intrusiveNotificationLed) == false) {
            getPreferenceScreen().removePreference(mNotificationLight);
        }

        mDisplayManager = (DisplayManager)getActivity().getSystemService(
                Context.DISPLAY_SERVICE);
        mCoordinate = (ListPreference) findPreference(KEY_COORDINATE);
        //mCoordinate.setTitle(com.android.internal.R.string.accelerometer_coordinate);
        String[] coordEntriesArray = {getResources().getString(com.android.internal.R.string.accele_coord_default), getResources().getString(com.android.internal.R.string.accele_coord_special)};
        mCoordinate.setEntries(coordEntriesArray);
        String[] coordEntriesValueArray = {"0", "1"};
        mCoordinate.setEntryValues(coordEntriesValueArray);
        mCoordinate.setOnPreferenceChangeListener(this);
	getPreferenceScreen().removePreference(mCoordinate);
        mGsensorCalib = (Preference) findPreference(KEY_CALIBRATION);

        mEnhancedColor = (CheckBoxPreference)findPreference(KEY_ENHANCED_COLOR_SYSTEM);
        long tmp = getMemoryTotalSize();
        if(tmp < 512*1024*1024) {
            getPreferenceScreen().removePreference(mEnhancedColor);
        }
        mEnhancedColor.setChecked(getEnhancedColorSystem(resolver));

        mTvoutSettingsCategory = (PreferenceCategory)findPreference(KEY_TVOUT_SETTINGS);
        //mTvoutSettingsCategory.setOrderingAsAdded(true);
        if(mSupportCvbs) {
        	mCvbsModePref = new TvoutSettingsListPreference(getActivity());
        	mCvbsModePref.setKey(KEY_CVBS_MODE_SELECTOR);    
        	mCvbsModePref.setTitle(getResources().getString(R.string.cvbs_mode));
        	mCvbsModePref.setPersistent(false);
        	mTvoutSettingsCategory.addPreference(mCvbsModePref);
        	mCvbsModePref.setDialogTitle(getResources().getString(R.string.cvbs_mode));
        	mCvbsModePref.setEntries(getResources().getStringArray(R.array.tvout_cvbs_entries));
        	mCvbsModePref.setEntryValues(getResources().getStringArray(R.array.tvout_cvbs_values));
        	mCvbsModePref.setOnPreferenceChangeListener(this);
        }
        if(mSupportHdmi) {
        	mHdmiModePref = new TvoutSettingsListPreference(getActivity());
        	mHdmiModePref.setLayoutResource(R.layout.preference_tvout);
        	mHdmiModePref.setKey(KEY_HDMI_MODE_SELECTOR);    
        	mHdmiModePref.setTitle(getResources().getString(R.string.hdmi_mode));
        	mHdmiModePref.setPersistent(false);
        	mTvoutSettingsCategory.addPreference(mHdmiModePref);
        	mHdmiModePref.setDialogTitle(getResources().getString(R.string.hdmi_mode));
        	mHdmiModePref.setOnPreferenceChangeListener(this);
        }
        if(mSupportHdmi || mSupportCvbs) {
        	mTvoutScreenResize = new Preference(getActivity());
        	mTvoutScreenResize.setTitle(getResources().getString(R.string.tvout_sreen_resize));
        	mTvoutSettingsCategory.addPreference(mTvoutScreenResize);
        } else {
        	getPreferenceScreen().removePreference(mTvoutSettingsCategory);
        }
        mCvbsUtils = (TvoutUtils.CvbsUtils)TvoutUtils.getInstanceByName(TvoutUtils.TVOUT_CVBS);
        mHdmiUtils = (TvoutUtils.HdmiUtils)TvoutUtils.getInstanceByName(TvoutUtils.TVOUT_HDMI); 
        
        mDisplay = this;

        mChargingLight = (Preference) findPreference(KEY_BATTERY_LIGHT);
        if (mChargingLight != null
                && getResources().getBoolean(
                        com.android.internal.R.bool.config_intrusiveBatteryLed) == false) {
            getPreferenceScreen().removePreference(mChargingLight);
        }

        // Default value for wake-on-plug behavior from config.xml
        boolean wakeUpWhenPluggedOrUnpluggedConfig = getResources().getBoolean(
                com.android.internal.R.bool.config_unplugTurnsOnScreen);

        mWakeWhenPluggedOrUnplugged =
                (CheckBoxPreference) findPreference(KEY_WAKE_WHEN_PLUGGED_OR_UNPLUGGED);
        mWakeWhenPluggedOrUnplugged.setChecked(Settings.Global.getInt(resolver,
                Settings.Global.WAKE_WHEN_PLUGGED_OR_UNPLUGGED,
                (wakeUpWhenPluggedOrUnpluggedConfig ? 1 : 0)) == 1);
    }

    private long getMemoryTotalSize() {
     	MemInfoReader memReader = new MemInfoReader();
     	memReader.readMemInfo();
     	return memReader.getTotalSize();
    }

    public static boolean getEnhancedColorSystem(ContentResolver resolver) {
        int enhanced = Settings.System.getInt(resolver, "actions_enhanced_color_system", 0) ;
        String value = SystemProperties.get(ENHANCED_COLOR_PROPERTY, "-1");
        if(!value.equals(String.valueOf(enhanced))) {
        	SystemProperties.set(ENHANCED_COLOR_PROPERTY, String.valueOf(enhanced));
        }
        return enhanced > 0;
    }

    public static void setEnhancedColorSystem(ContentResolver resolver, boolean bEnhanced) {
    	int enhanced = bEnhanced ? 1:0;
    	Settings.System.putInt(resolver, "actions_enhanced_color_system", enhanced) ;
        SystemProperties.set(ENHANCED_COLOR_PROPERTY, String.valueOf(enhanced));
    }
	private void updateTvoutMenuStatus(boolean isPlugIn) {
		boolean resetValueFlag = isPlugIn;
		Log.d(TAG, "run in updateTvoutMenuStatus");
		if(mSupportCvbs) {
			Log.d(TAG, "the cvbs select mode="+mCvbsUtils.getLastSelectModeValue());
			mCvbsModePref.setValue(mCvbsUtils.getLastSelectModeValue());
		}
		if(!mSupportHdmi) {
			return;
		}
		/** if ListPreference' dialog is showing, may be need to update the 
		 * hdmi mode list,when hdmi line plug,here just dismiss*/
		if(mHdmiModePref.getDialog() != null && mHdmiModePref.getDialog().isShowing()) {
			mHdmiModePref.getDialog().dismiss();
			resetValueFlag = true;
        }
		String[] hdmiEntries = null;
		String[] hdmiValues = null;
		if(mHdmiUtils.isCablePlugIn()) {
			hdmiEntries = mHdmiUtils.getSupportedModesList();
			hdmiValues = mHdmiUtils.getSupportedVidList();
		} 
		if(hdmiEntries == null || hdmiValues == null) {
			hdmiEntries = getResources().getStringArray(R.array.tvout_hdmi_entries);
			hdmiValues = getResources().getStringArray(R.array.tvout_hdmi_values);
		}
		/** if the mode is always switch to hdmi, don't display "Disconnect" item */
		hdmiEntries = deleteOneElementFromArray(hdmiEntries, 0);
		hdmiValues = deleteOneElementFromArray(hdmiValues, 0);
		mHdmiModePref.setEntries(hdmiEntries);
		mHdmiModePref.setEntryValues(hdmiValues);
		Log.d(TAG, "the hdmi select mode="+mHdmiUtils.getLastSelectModeValue());
		mHdmiModePref.setValue(mHdmiUtils.getLastSelectModeValue());
	}
	private String[] deleteOneElementFromArray(String[] array, int index) {
		int i = 0, j = 0;
		String[] retArr = new String[array.length - 1];
		for(i=0;i<array.length;i++) {
			if(index == i) {
				continue;
			}
			retArr[j++] = array[i];
		}
		return retArr;
	}
	public static void tryToUpdateTvoutMenuStatus(boolean isPlugIn) {
		if(mDisplay == null) {
			return;
		}
		try {
			mDisplay.updateTvoutMenuStatus(isPlugIn);
		} catch (Exception e) {
			Log.e(TAG, "can't update the tvout menu status!");
		}
	}

    private void updateScreenOffAnimationPreferenceDescription(int currentAnim) {
        ListPreference preference = mScreenOffAnimationPreference;
        String summary;
        if (currentAnim < 0) {
            // Unsupported value
            summary = "";
        } else {
            final CharSequence[] entries = preference.getEntries();
            final CharSequence[] values = preference.getEntryValues();
            if (entries == null || entries.length == 0) {
                summary = "";
            } else {
                summary = entries[currentAnim].toString();
            }
        }
        preference.setSummary(summary);
    }

    private void updateTimeoutPreferenceDescription(long currentTimeout) {
        ListPreference preference = mScreenTimeoutPreference;
        String summary;
        /*
        if (currentTimeout < 0) {
            // Unsupported value
            summary = "";
        } else {
        */
            final CharSequence[] entries = preference.getEntries();
            final CharSequence[] values = preference.getEntryValues();
            if (entries == null || entries.length == 0) {
                summary = "";
            } else {
                int best = 0;
                for (int i = 0; i < values.length; i++) {
                    long timeout = Long.parseLong(values[i].toString());
                    if (currentTimeout >= timeout) {
                        best = i;
                        if(i == 0) {
                        	break;
                        }
                    }
                }
            if(best > 0) {
                summary = preference.getContext().getString(R.string.screen_timeout_summary,
                        entries[best]);
            } else {
            	summary = entries[0].toString();
            }
        }
        preference.setSummary(summary);
    }

    private void addNeverTimeout(Context context, ListPreference screenTimeoutPreference) {
    	final CharSequence[] entries = screenTimeoutPreference.getEntries();
        final CharSequence[] values = screenTimeoutPreference.getEntryValues();
        ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();
        revisedEntries.add(context.getResources().getString(R.string.screensaver_timeout_zero_summary));
        revisedValues.add(String.valueOf(Integer.MAX_VALUE));
        for (int i = 0; i < values.length; i++) {
            revisedEntries.add(entries[i]);
            revisedValues.add(values[i]);
        }
        screenTimeoutPreference.setEntries(
                revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
        screenTimeoutPreference.setEntryValues(
                revisedValues.toArray(new CharSequence[revisedValues.size()]));
    }

    private void disableUnusableTimeouts(ListPreference screenTimeoutPreference) {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) getActivity().getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        final long maxTimeout = dpm != null ? dpm.getMaximumTimeToLock(null) : 0;
        if (maxTimeout == 0) {
            return; // policy not enforced
        }
        final CharSequence[] entries = screenTimeoutPreference.getEntries();
        final CharSequence[] values = screenTimeoutPreference.getEntryValues();
        ArrayList<CharSequence> revisedEntries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> revisedValues = new ArrayList<CharSequence>();
        for (int i = 0; i < values.length; i++) {
            long timeout = Long.parseLong(values[i].toString());
            if (timeout <= maxTimeout) {
                revisedEntries.add(entries[i]);
                revisedValues.add(values[i]);
            }
        }
        if (revisedEntries.size() != entries.length || revisedValues.size() != values.length) {
            final int userPreference = Integer.parseInt(screenTimeoutPreference.getValue());
            screenTimeoutPreference.setEntries(
                    revisedEntries.toArray(new CharSequence[revisedEntries.size()]));
            screenTimeoutPreference.setEntryValues(
                    revisedValues.toArray(new CharSequence[revisedValues.size()]));
            if (userPreference <= maxTimeout) {
                screenTimeoutPreference.setValue(String.valueOf(userPreference));
            } else if (revisedValues.size() > 0
                    && Long.parseLong(revisedValues.get(revisedValues.size() - 1).toString())
                    == maxTimeout) {
                // If the last one happens to be the same as the max timeout, select that
                screenTimeoutPreference.setValue(String.valueOf(maxTimeout));
            } else {
                // There will be no highlighted selection since nothing in the list matches
                // maxTimeout. The user can still select anything less than maxTimeout.
                // TODO: maybe append maxTimeout to the list and mark selected.
            }
        }
        screenTimeoutPreference.setEnabled(revisedEntries.size() > 0);
    }

    int floatToIndex(float val) {
        String[] indices = getResources().getStringArray(R.array.entryvalues_font_size);
        float lastVal = Float.parseFloat(indices[0]);
        for (int i=1; i<indices.length; i++) {
            float thisVal = Float.parseFloat(indices[i]);
            if (val < (lastVal + (thisVal-lastVal)*.5f)) {
                return i-1;
            }
            lastVal = thisVal;
        }
        return indices.length-1;
    }
    
    public void readFontSizePreference(ListPreference pref) {
        try {
            mCurConfig.updateFrom(ActivityManagerNative.getDefault().getConfiguration());
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to retrieve font size");
        }

        // mark the appropriate item in the preferences list
        int index = floatToIndex(mCurConfig.fontScale);
        pref.setValueIndex(index);

        // report the current size in the summary text
        final Resources res = getResources();
        String[] fontSizeNames = res.getStringArray(R.array.entries_font_size);
        pref.setSummary(String.format(res.getString(R.string.summary_font_size),
                fontSizeNames[index]));
    }
    
    @Override
    public void onResume() {
        super.onResume();

        RotationPolicy.registerRotationPolicyListener(getActivity(),
                mRotationPolicyListener);

        updateState();
    }

    @Override
    public void onPause() {
        super.onPause();

        RotationPolicy.unregisterRotationPolicyListener(getActivity(),
                mRotationPolicyListener);
    }

    @Override
    public Dialog onCreateDialog(int dialogId) {
        if (dialogId == DLG_GLOBAL_CHANGE_WARNING) {
            return Utils.buildGlobalChangeWarningDialog(getActivity(),
                    R.string.global_font_change_title,
                    new Runnable() {
                        public void run() {
                            mFontSizePref.click();
                        }
                    });
        }
        return null;
    }

    private void updateState() {
        updateAccelerometerRotationCheckbox();
        readFontSizePreference(mFontSizePref);
        updateScreenSaverSummary();
        updateTvoutMenuStatus(false);
        updateCoordinateStatus(null);
    }

    private void updateCoordinateStatus(Object value) {
    	if(value == null) {
            int  nCoordinate = Settings.System.getInt(getContentResolver(), ACCELEROMETER_COORDINATE_MODE, 0);
            value = String.valueOf(nCoordinate);
    	}
    	if(value instanceof String) {
    		mCoordinate.setValue((String)value);
    	}
        CharSequence[] summaries = mCoordinate.getEntries();
        CharSequence[] values = mCoordinate.getEntryValues();
        for (int i=0; i<values.length; i++) {
            if (values[i].equals(value)) {
            	mCoordinate.setSummary(summaries[i]);
                break;
            }
        }
    }

    private void updateScreenSaverSummary() {
        if (mScreenSaverPreference != null) {
            mScreenSaverPreference.setSummary(
                    DreamSettings.getSummaryTextWithDreamName(getActivity()));
        }
    }

    private void updateAccelerometerRotationCheckbox() {
        if (getActivity() == null) return;

        mAccelerometer.setChecked(!RotationPolicy.isRotationLocked(getActivity()));
    }

    public void writeFontSizePreference(Object objValue) {
        try {
            mCurConfig.fontScale = Float.parseFloat(objValue.toString());
            ActivityManagerNative.getDefault().updatePersistentConfiguration(mCurConfig);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to save font size");
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mAccelerometer) {
            RotationPolicy.setRotationLockForAccessibility(
                    getActivity(), !mAccelerometer.isChecked());
        } else if(preference == mTvoutScreenResize) {
        	final Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClass(getActivity(), TvoutScreenResizeActivity.class);
            startActivity(intent);
        } else if(preference == mGsensorCalib) {
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName("com.actions.sensor.calib", "com.actions.sensor.calib.SensorActivity");
            startActivity(intent);
        } else if(preference == mEnhancedColor) {
        	setEnhancedColorSystem(getContentResolver(), mEnhancedColor.isChecked());
        } else if (preference == mWakeWhenPluggedOrUnplugged) {
            Settings.Global.putInt(getContentResolver(),
                    Settings.Global.WAKE_WHEN_PLUGGED_OR_UNPLUGGED,
                    mWakeWhenPluggedOrUnplugged.isChecked() ? 1 : 0);
            return true;
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();
        if (KEY_SCREEN_TIMEOUT.equals(key)) {
            int value = Integer.parseInt((String) objValue);
            try {
                Settings.System.putInt(getContentResolver(), SCREEN_OFF_TIMEOUT, value);
                updateTimeoutPreferenceDescription(value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist screen timeout setting", e);
            }
        }
        if (KEY_FONT_SIZE.equals(key)) {
            writeFontSizePreference(objValue);
        }
        if (KEY_COORDINATE.equals(key)) {
            int value = Integer.parseInt((String) objValue);
            try {
            	Settings.System.putInt(getContentResolver(),
            	ACCELEROMETER_COORDINATE_MODE, value);
            }catch (NumberFormatException e) {
            	Log.e(TAG, "could not persist coordinate change setting", e);
            }
            updateCoordinateStatus(objValue);
        }
        if (KEY_CVBS_MODE_SELECTOR.equals(key)) {
        	int value = Integer.parseInt((String) objValue);
        	mCvbsUtils.switchToSelectModeByModeValue(value);
        }
        if (KEY_HDMI_MODE_SELECTOR.equals(key)) {
        	int value = Integer.parseInt((String) objValue);
        	mHdmiUtils.switchToSelectModeByModeValue(value);
        }
        if (KEY_SCREEN_OFF_ANIMATION.equals(key)) {
            int value = Integer.parseInt((String) objValue);
            try {
                Settings.System.putInt(getContentResolver(), SCREEN_OFF_ANIMATION, value);
                updateScreenOffAnimationPreferenceDescription(value);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist screen-off animation setting", e);
            }
        }

        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mFontSizePref) {
            if (Utils.hasMultipleUsers(getActivity())) {
                showDialog(DLG_GLOBAL_CHANGE_WARNING);
                return true;
            } else {
                mFontSizePref.click();
            }
        }
        return false;
    }

    public class TvoutSettingsListPreference extends ListPreference implements CompoundButton.OnCheckedChangeListener {
    	private Switch mSwitchView = null;
    	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    	 	Log.d(TAG, "isChecked="+isChecked);
    	 	if(getLastSwitchStatus() != isChecked) {
    	 		setLastSwitchStatus(isChecked);
    	 		mHdmiUtils.setHdmiEnable(isChecked);
    	 	}
    	}
        public TvoutSettingsListPreference(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public TvoutSettingsListPreference(Context context) {
        	super(context, null);
        }
        private boolean getLastSwitchStatus() {
        	String value = SystemProperties.get(TVOUT_STATUS_PROPERTY, "1");
        	SystemProperties.set(TVOUT_STATUS_PROPERTY, value);
        	return Integer.parseInt(value) > 0 ? true : false;
        }
        private void setLastSwitchStatus(boolean bStatus) {
        	String value = bStatus ? "1" : "0";
        	SystemProperties.set(TVOUT_STATUS_PROPERTY, value);
        }
        protected void onClick() {
        	if(mSwitchView != null && !mSwitchView.isChecked()) {
        		return;
        	}
            if (getDialog() != null && getDialog().isShowing()) return;
            showDialog(null);
       }
        @Override
        protected void onBindView(View view) {
        	super.onBindView(view);
             View checkableView = view.findViewById(R.id.tvoutSwitch);
             Log.d(TAG, "checkableView="+checkableView+",isEnabled="+this.isEnabled());
             if (checkableView != null && checkableView instanceof Checkable) {
                 ((Checkable) checkableView).setChecked(getLastSwitchStatus());
                 if (checkableView instanceof Switch) {
                 mSwitchView = (Switch) checkableView;
                 mSwitchView.setOnCheckedChangeListener(this);
                 }
              }
        }
        
        @Override
        protected void onPrepareDialogBuilder(Builder builder) {
        	updateTvoutMenuStatus(false);
        	super.onPrepareDialogBuilder(builder);
        }
    }
}
