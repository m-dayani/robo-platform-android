package com.dayani.m.roboplatform.utils.loggers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.dayani.m.roboplatform.utils.android_gnss.GnssContainer;
import com.dayani.m.roboplatform.utils.android_gnss.GnssListener;

import java.text.DecimalFormat;

/**
 * A class representing a UI logger for the application. Its responsibility is show information in
 * the UI.
 */
public class UiLogger implements GnssListener {

    private static final int USED_COLOR = Color.rgb(0x4a, 0x5f, 0x70);

    public UiLogger() {}

    private UIFragmentComponent mUiFragmentComponent;

    public synchronized UIFragmentComponent getUiFragmentComponent() {
        return mUiFragmentComponent;
    }

    public synchronized void setUiFragmentComponent(UIFragmentComponent value) {
        mUiFragmentComponent = value;
    }

    @Override
    public void onProviderEnabled(String provider) {
        logLocationEvent("onProviderEnabled: " + provider);
    }

    @Override
    public void onTTFFReceived(long l) {}

    @Override
    public void onProviderDisabled(String provider) {
        logLocationEvent("onProviderDisabled: " + provider);
    }

    @Override
    public void onLocationChanged(Location location) {
        logLocationEvent("onLocationChanged: " + location + "\n");
    }

    @Override
    public void onLocationStatusChanged(String provider, int status, Bundle extras) {
        String message =
                String.format(
                        "onStatusChanged: provider=%s, status=%s, extras=%s",
                        provider, locationStatusToString(status), extras);
        logLocationEvent(message);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
        StringBuilder builder = new StringBuilder("[ GnssMeasurementsEvent:\n\n");

        builder.append(toStringClock(event.getClock()));
        builder.append("\n");

        for (GnssMeasurement measurement : event.getMeasurements()) {
            builder.append(toStringMeasurement(measurement));
            builder.append("\n");
        }

        builder.append("]");
        logMeasurementEvent("onGnsssMeasurementsReceived: " + builder.toString());
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private String toStringClock(GnssClock gnssClock) {
        final String format = "   %-4s = %s\n";
        StringBuilder builder = new StringBuilder("GnssClock:\n");
        DecimalFormat numberFormat = new DecimalFormat("#0.000");
        if (gnssClock.hasLeapSecond()) {
            builder.append(String.format(format, "LeapSecond", gnssClock.getLeapSecond()));
        }

        builder.append(String.format(format, "TimeNanos", gnssClock.getTimeNanos()));
        if (gnssClock.hasTimeUncertaintyNanos()) {
            builder.append(
                    String.format(format, "TimeUncertaintyNanos", gnssClock.getTimeUncertaintyNanos()));
        }

        if (gnssClock.hasFullBiasNanos()) {
            builder.append(String.format(format, "FullBiasNanos", gnssClock.getFullBiasNanos()));
        }

        if (gnssClock.hasBiasNanos()) {
            builder.append(String.format(format, "BiasNanos", gnssClock.getBiasNanos()));
        }
        if (gnssClock.hasBiasUncertaintyNanos()) {
            builder.append(
                    String.format(
                            format,
                            "BiasUncertaintyNanos",
                            numberFormat.format(gnssClock.getBiasUncertaintyNanos())));
        }

        if (gnssClock.hasDriftNanosPerSecond()) {
            builder.append(
                    String.format(
                            format,
                            "DriftNanosPerSecond",
                            numberFormat.format(gnssClock.getDriftNanosPerSecond())));
        }

        if (gnssClock.hasDriftUncertaintyNanosPerSecond()) {
            builder.append(
                    String.format(
                            format,
                            "DriftUncertaintyNanosPerSecond",
                            numberFormat.format(gnssClock.getDriftUncertaintyNanosPerSecond())));
        }

        builder.append(
                String.format(
                        format,
                        "HardwareClockDiscontinuityCount",
                        gnssClock.getHardwareClockDiscontinuityCount()));

        return builder.toString();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private String toStringMeasurement(GnssMeasurement measurement) {
        final String format = "   %-4s = %s\n";
        StringBuilder builder = new StringBuilder("GnssMeasurement:\n");
        DecimalFormat numberFormat = new DecimalFormat("#0.000");
        DecimalFormat numberFormat1 = new DecimalFormat("#0.000E00");
        builder.append(String.format(format, "Svid", measurement.getSvid()));
        builder.append(String.format(format, "ConstellationType", measurement.getConstellationType()));
        builder.append(String.format(format, "TimeOffsetNanos", measurement.getTimeOffsetNanos()));

        builder.append(String.format(format, "State", measurement.getState()));

        builder.append(
                String.format(format, "ReceivedSvTimeNanos", measurement.getReceivedSvTimeNanos()));
        builder.append(
                String.format(
                        format,
                        "ReceivedSvTimeUncertaintyNanos",
                        measurement.getReceivedSvTimeUncertaintyNanos()));

        builder.append(String.format(format, "Cn0DbHz", numberFormat.format(measurement.getCn0DbHz())));

        builder.append(
                String.format(
                        format,
                        "PseudorangeRateMetersPerSecond",
                        numberFormat.format(measurement.getPseudorangeRateMetersPerSecond())));
        builder.append(
                String.format(
                        format,
                        "PseudorangeRateUncertaintyMetersPerSeconds",
                        numberFormat.format(measurement.getPseudorangeRateUncertaintyMetersPerSecond())));

        if (measurement.getAccumulatedDeltaRangeState() != 0) {
            builder.append(
                    String.format(
                            format, "AccumulatedDeltaRangeState", measurement.getAccumulatedDeltaRangeState()));

            builder.append(
                    String.format(
                            format,
                            "AccumulatedDeltaRangeMeters",
                            numberFormat.format(measurement.getAccumulatedDeltaRangeMeters())));
            builder.append(
                    String.format(
                            format,
                            "AccumulatedDeltaRangeUncertaintyMeters",
                            numberFormat1.format(measurement.getAccumulatedDeltaRangeUncertaintyMeters())));
        }

        if (measurement.hasCarrierFrequencyHz()) {
            builder.append(
                    String.format(format, "CarrierFrequencyHz", measurement.getCarrierFrequencyHz()));
        }

        if (measurement.hasCarrierCycles()) {
            builder.append(String.format(format, "CarrierCycles", measurement.getCarrierCycles()));
        }

        if (measurement.hasCarrierPhase()) {
            builder.append(String.format(format, "CarrierPhase", measurement.getCarrierPhase()));
        }

        if (measurement.hasCarrierPhaseUncertainty()) {
            builder.append(
                    String.format(
                            format, "CarrierPhaseUncertainty", measurement.getCarrierPhaseUncertainty()));
        }

        builder.append(
                String.format(format, "MultipathIndicator", measurement.getMultipathIndicator()));

        if (measurement.hasSnrInDb()) {
            builder.append(String.format(format, "SnrInDb", measurement.getSnrInDb()));
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (measurement.hasAutomaticGainControlLevelDb()) {
                builder.append(
                        String.format(format, "AgcDb", measurement.getAutomaticGainControlLevelDb()));
            }
            if (measurement.hasCarrierFrequencyHz()) {
                builder.append(String.format(format, "CarrierFreqHz", measurement.getCarrierFrequencyHz()));
            }
        }

        return builder.toString();
    }

    @Override
    public void onGnssMeasurementsStatusChanged(int status) {
        logMeasurementEvent("onStatusChanged: " + gnssMeasurementsStatusToString(status));
    }

    @Override
    public void onGnssNavigationMessageReceived(GnssNavigationMessage event) {
        logNavigationMessageEvent("onGnssNavigationMessageReceived: " + event);
    }

    @Override
    public void onGnssNavigationMessageStatusChanged(int status) {
        logNavigationMessageEvent("onStatusChanged: " + getGnssNavigationMessageStatus(status));
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onGnssStatusChanged(GnssStatus gnssStatus) {
        logStatusEvent("onGnssStatusChanged: " + gnssStatusToString(gnssStatus));
    }

    @Override
    public void onNmeaReceived(long timestamp, String s) {
        logNmeaEvent(String.format("onNmeaReceived: timestamp=%d, %s", timestamp, s));
    }

    @Override
    public void onListenerRegistration(String listener, boolean result) {
        logEvent("Registration", String.format("add%sListener: %b", listener, result), USED_COLOR);
    }

    private void logMeasurementEvent(String event) {
        logEvent("Measurement", event, USED_COLOR);
    }

    private void logNavigationMessageEvent(String event) {
        logEvent("NavigationMsg", event, USED_COLOR);
    }

    private void logStatusEvent(String event) {
        logEvent("Status", event, USED_COLOR);
    }

    private void logNmeaEvent(String event) {
        logEvent("Nmea", event, USED_COLOR);
    }

    private void logEvent(String tag, String message, int color) {
        String composedTag = GnssContainer.TAG + tag;
        Log.d(composedTag, message);
        logText(tag, message, color);
    }

    private void logText(String tag, String text, int color) {
        UIFragmentComponent component = getUiFragmentComponent();
        if (component != null) {
            component.logTextFragment(tag, text, color);
        }
    }

    private String locationStatusToString(int status) {
        switch (status) {
            case LocationProvider.AVAILABLE:
                return "AVAILABLE";
            case LocationProvider.OUT_OF_SERVICE:
                return "OUT_OF_SERVICE";
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                return "TEMPORARILY_UNAVAILABLE";
            default:
                return "<Unknown>";
        }
    }

    private String gnssMeasurementsStatusToString(int status) {
        switch (status) {
            case GnssMeasurementsEvent.Callback.STATUS_NOT_SUPPORTED:
                return "NOT_SUPPORTED";
            case GnssMeasurementsEvent.Callback.STATUS_READY:
                return "READY";
            case GnssMeasurementsEvent.Callback.STATUS_LOCATION_DISABLED:
                return "GNSS_LOCATION_DISABLED";
            default:
                return "<Unknown>";
        }
    }

    private String getGnssNavigationMessageStatus(int status) {
        switch (status) {
            case GnssNavigationMessage.STATUS_UNKNOWN:
                return "Status Unknown";
            case GnssNavigationMessage.STATUS_PARITY_PASSED:
                return "READY";
            case GnssNavigationMessage.STATUS_PARITY_REBUILT:
                return "Status Parity Rebuilt";
            default:
                return "<Unknown>";
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private String gnssStatusToString(GnssStatus gnssStatus) {

        StringBuilder builder = new StringBuilder("SATELLITE_STATUS | [Satellites:\n");
        for (int i = 0; i < gnssStatus.getSatelliteCount(); i++) {
            builder
                    .append("Constellation = ")
                    .append(getConstellationName(gnssStatus.getConstellationType(i)))
                    .append(", ");
            builder.append("Svid = ").append(gnssStatus.getSvid(i)).append(", ");
            builder.append("Cn0DbHz = ").append(gnssStatus.getCn0DbHz(i)).append(", ");
            builder.append("Elevation = ").append(gnssStatus.getElevationDegrees(i)).append(", ");
            builder.append("Azimuth = ").append(gnssStatus.getAzimuthDegrees(i)).append(", ");
            builder.append("hasEphemeris = ").append(gnssStatus.hasEphemerisData(i)).append(", ");
            builder.append("hasAlmanac = ").append(gnssStatus.hasAlmanacData(i)).append(", ");
            builder.append("usedInFix = ").append(gnssStatus.usedInFix(i)).append("\n");
        }
        builder.append("]");
        return builder.toString();
    }

    private void logLocationEvent(String event) {
        logEvent("Location", event, USED_COLOR);
    }

    private String getConstellationName(int id) {
        switch (id) {
            case 1:
                return "GPS";
            case 2:
                return "SBAS";
            case 3:
                return "GLONASS";
            case 4:
                return "QZSS";
            case 5:
                return "BEIDOU";
            case 6:
                return "GALILEO";
            default:
                return "UNKNOWN";
        }
    }

    protected static String PREFERENCE_KEY_AUTO_SCROLL =  "autoScroll";

    /**
     * A facade for UI and Activity related operations that are required for {@link GnssListener}s.
     */
    public class UIFragmentComponent {

        private static final int MAX_LENGTH = 42000;
        private static final int LOWER_THRESHOLD = (int) (MAX_LENGTH * 0.5);

        Context mContext;
        TextView mLogView;
        ScrollView mScrollView;

        public UIFragmentComponent(Context context, TextView logView, ScrollView scrollView) {
            this.mContext = context;
            this.mLogView = logView;
            this.mScrollView = scrollView;
        }

        public synchronized void logTextFragment(final String tag, final String text, int color) {
            final SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(tag).append(" | ").append(text).append("\n");
            builder.setSpan(
                    new ForegroundColorSpan(color),
                    0 /* start */,
                    builder.length(),
                    SpannableStringBuilder.SPAN_INCLUSIVE_EXCLUSIVE);

            final Activity activity = (AppCompatActivity) mContext;
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            mLogView.append(builder);
                            SharedPreferences sharedPreferences = PreferenceManager.
                                    getDefaultSharedPreferences(activity);
                            Editable editable = mLogView.getEditableText();
                            int length = editable.length();
                            if (length > MAX_LENGTH) {
                                editable.delete(0, length - LOWER_THRESHOLD);
                            }
                            if (sharedPreferences.getBoolean(PREFERENCE_KEY_AUTO_SCROLL,
                                    false /*default return value*/)){
                                mScrollView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        mScrollView.fullScroll(View.FOCUS_DOWN);
                                    }
                                });
                            }
                        }
                    });
        }

        public void startActivity(Intent intent) {
            mContext.startActivity(intent);
        }
    }
}