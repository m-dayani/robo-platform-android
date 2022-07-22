package com.dayani.m.roboplatform.dump;

import android.content.Context;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.utils.ActivityRequirements;
import com.dayani.m.roboplatform.utils.RequirementListRecyclerViewAdapter;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Activities containing this fragment MUST implement the {@link OnListFragmentInteractionListener}
 * interface.
 */
public class RequirementListFragment extends Fragment {

    private static final String TAG = RequirementListFragment.class.getSimpleName();

    //private static final String ARG_COLUMN_COUNT = "column-count";
    private static final String ARG_PERMISSIONS = "arg_permissions";
    private static final String ARG_REQUIREMENTS = "arg_requirements";

    //private int mColumnCount = 1;
    private ActivityRequirements.Requirement[] requirements;
    private String[] permissions;

    private RequirementListRecyclerViewAdapter mAdapter;
    private OnListFragmentInteractionListener mListener;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public RequirementListFragment() {
    }

    // TODO: Customize parameter initialization
    @SuppressWarnings("unused")
    public static RequirementListFragment newInstance(
            ActivityRequirements.Requirement[] requirements, String[] perms) {
        Log.d(TAG, "newInstance");
        RequirementListFragment fragment = new RequirementListFragment();
        Bundle args = new Bundle();
        args.putParcelableArray(ARG_REQUIREMENTS, requirements);
        args.putStringArray(ARG_PERMISSIONS, perms);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        if (getArguments() != null) {
            requirements = (ActivityRequirements.Requirement[])
                    getArguments().getParcelableArray(ARG_REQUIREMENTS);
            permissions = getArguments().getStringArray(ARG_PERMISSIONS);
            Log.v(TAG, Arrays.toString(requirements));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        View view = inflater.inflate(R.layout.z_fragment_requirement_list, container, false);

        // Set the adapter
        if (view instanceof RecyclerView) {
            Context context = view.getContext();
            RecyclerView recyclerView = (RecyclerView) view;

            recyclerView.setLayoutManager(new LinearLayoutManager(context));

            ArrayList<ActivityRequirements.RequirementItem> rItems = getRequiredItems(requirements);

            mAdapter = new RequirementListRecyclerViewAdapter(rItems, mListener);
            //((DummyItem) mAdapter.get(0)).set
            recyclerView.setAdapter(mAdapter);
            //recyclerView.setOnClickListener(this);
        }
        return view;
    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Log.d(TAG, "onAttach");
        if (context instanceof OnListFragmentInteractionListener) {
            mListener = (OnListFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnListFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Log.d(TAG, "onDetach");
        mListener = null;
    }

    private ArrayList<ActivityRequirements.RequirementItem> getRequiredItems(
            ActivityRequirements.Requirement[] requirements) {
        Log.d(TAG, "getRequiredItems");
        ArrayList<ActivityRequirements.RequirementItem> rItems =
                new ArrayList<ActivityRequirements.RequirementItem>();
        if (requirements == null) {
            //if requirements is null: there is no requirement!
            return rItems;
        }
        if (Arrays.asList(requirements).contains(ActivityRequirements.Requirement.PERMISSIONS)) {
            rItems.add(new ActivityRequirements.RequirementItem(
                    ActivityRequirements.Requirement.PERMISSIONS,
                    ActivityRequirements.RequirementState.PENDING,
                    R.id.permissions, "Permissions"));
        }
        if (Arrays.asList(requirements).contains(ActivityRequirements.Requirement.USB_DEVICE)) {
            rItems.add(new ActivityRequirements.RequirementItem(
                    ActivityRequirements.Requirement.USB_DEVICE,
                    ActivityRequirements.RequirementState.PENDING,
                    R.id.usb_device, "USB Device"));
        }
        if (Arrays.asList(requirements).contains(ActivityRequirements.Requirement.WIRELESS_CONNECTION)) {
            rItems.add(new ActivityRequirements.RequirementItem(
                    ActivityRequirements.Requirement.WIRELESS_CONNECTION,
                    ActivityRequirements.RequirementState.PENDING,
                    R.id.wireless_conn, "Wireless Connection"));
        }
        if (Arrays.asList(requirements).contains(ActivityRequirements.Requirement.ENABLE_LOCATION)) {
            rItems.add(new ActivityRequirements.RequirementItem(
                    ActivityRequirements.Requirement.ENABLE_LOCATION,
                    ActivityRequirements.RequirementState.PENDING,
                    R.id.enable_location, "Enable Location"));
        }
        if (Arrays.asList(requirements).contains(ActivityRequirements.Requirement.ALL_SENSORS)) {
            rItems.add(new ActivityRequirements.RequirementItem(
                    ActivityRequirements.Requirement.ALL_SENSORS,
                    ActivityRequirements.RequirementState.PENDING,
                    R.id.all_sensors, "All Sensors"));
        }
        return rItems;
    }

    //private ActivityRequirements.RequirementItem get

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnListFragmentInteractionListener {
        // TODO: Update argument type and name
        void onListFragmentInteraction(ActivityRequirements.RequirementItem item);
    }

    public void updateRequirements(ActivityRequirements.Requirement[] reqs) {
        Log.d(TAG, "updateRequirements");
        ArrayList<ActivityRequirements.RequirementItem> rItems = getRequiredItems(requirements);
        mAdapter.updateRequirements(rItems);
    }
}
