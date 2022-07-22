package com.dayani.m.roboplatform;

import android.content.Context;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dayani.m.roboplatform.requirements.BluetoothReqFragment;
import com.dayani.m.roboplatform.requirements.HotspotReqFragment;
import com.dayani.m.roboplatform.requirements.WiNetReqFragment;
import com.dayani.m.roboplatform.utils.OnRequestPageChange;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link OnRequestPageChange} interface
 * to handle interaction events.
 * Use the {@link WirelessConnFrontPanelFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class WirelessConnFrontPanelFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = WirelessConnFrontPanelFragment.class.getSimpleName();



    private OnRequestPageChange mListener;

    public WirelessConnFrontPanelFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment WirelessConnFrontPanelFragment.
     */
    public static WirelessConnFrontPanelFragment newInstance() {
        WirelessConnFrontPanelFragment fragment = new WirelessConnFrontPanelFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

//    @Override
//    public void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        if (getArguments() != null) {
//
//        }
//    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View mView = inflater.inflate(R.layout.fragment_wireless_conn_front_panel, container, false);

        mView.findViewById(R.id.btnNetwork).setOnClickListener(this);
        mView.findViewById(R.id.btnHotspot).setOnClickListener(this);
        mView.findViewById(R.id.btnBluetooth).setOnClickListener(this);

        return mView;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnRequestPageChange) {
            mListener = (OnRequestPageChange) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnRequestPageChange");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btnNetwork: {
                Log.d(TAG, "Wireless Network");
                mListener.onRequestPageChange(WiNetReqFragment.newInstance(), null);
                break;
            }
            case R.id.btnHotspot: {
                Log.d(TAG, "Mobile Hotspot");
                mListener.onRequestPageChange(HotspotReqFragment.newInstance(), null);
                break;
            }
            case R.id.btnBluetooth: {
                Log.d(TAG, "Bluetooth");
                mListener.onRequestPageChange(BluetoothReqFragment.newInstance(), null);
                break;
            }
            default: {
                Log.e(TAG, "Undefined Action");
                break;
            }
        }
    }
}
