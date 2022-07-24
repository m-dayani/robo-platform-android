package com.dayani.m.roboplatform.utils;

import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.dump.RequirementListFragment.OnListFragmentInteractionListener;

import java.util.ArrayList;

/**
 * {@link RecyclerView.Adapter} that can display a
 * {@link ActivityRequirements.RequirementItem} and makes a call to the
 * specified {@link OnListFragmentInteractionListener}.
 */
public class RequirementListRecyclerViewAdapter extends
        RecyclerView.Adapter<RequirementListRecyclerViewAdapter.ViewHolder> {

    private static final String TAG = RequirementListRecyclerViewAdapter.class.getSimpleName();

    private ArrayList<ActivityRequirements.RequirementItem> mValues;
    private final OnListFragmentInteractionListener mListener;

    public RequirementListRecyclerViewAdapter(ArrayList<ActivityRequirements.RequirementItem> items,
                                              OnListFragmentInteractionListener listener) {
        mValues = items;
        mListener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Log.d(TAG, "onCreateViewHolder");
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_requirement_list_item, parent, false);
        //TODO: set/create view holder with an implementation of onClickInterface
        ViewHolder.IMyViewHolderClicks vhListener = new ViewHolder.IMyViewHolderClicks() {
            @Override
            public void onPotato(View caller) {

            }

            @Override
            public void onTomato(ImageView callerImage) {

            }
        };
        return new ViewHolder(view, vhListener);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        Log.d(TAG, "onBindViewHolder");
        holder.mItem = mValues.get(position);
//        holder.mIdView.setText(Integer.toString(mValues.get(position).id));
//        holder.mContentView.setText(mValues.get(position).name);
        holder.mReqAction.setText(mValues.get(position).name);
        holder.mReqAction.setId(mValues.get(position).id);
        holder.setViewState(mValues.get(position).state);

        holder.mView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mListener) {
                    // Notify the active callbacks interface (the activity, if the
                    // fragment is attached to one) that an item has been selected.
                    mListener.onListFragmentInteraction(holder.mItem);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }


    public void updateRequirements(ArrayList<ActivityRequirements.RequirementItem> reqItems) {
        Log.d(TAG, "updateRequirements");
        this.mValues = reqItems;
        this.notifyDataSetChanged();
    }

    public void setItemState(int position, ActivityRequirements.RequirementState state) {
        this.mValues.get(position).state = state;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public final View mView;
        public final Button mReqAction;
        public final ImageView mReqState;
        public ActivityRequirements.RequirementItem mItem;

        private final IMyViewHolderClicks mListener;

        public ViewHolder(View view, IMyViewHolderClicks mListener) {
            super(view);
            mView = view;
            mReqAction = (Button) view.findViewById(R.id.requirementAction);
            mReqState = (ImageView) view.findViewById(R.id.requirementState);

            this.mListener = mListener;
        }

        public void setViewState(ActivityRequirements.RequirementState state) {
            switch (state) {
                case PERMITTED: {
                    this.mReqState.setImageResource(android.R.drawable.checkbox_on_background);
                    break;
                }
                case PENDING: /*{
                    this.mReqState.setImageResource(android.R.drawable.ic_popup_sync);
                    break;
                }*/
                default: {
                    this.mReqState.setImageResource(android.R.drawable.ic_lock_lock);
                    break;
                }
            }
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mReqAction.getText() + "'";
        }

        @Override
        public void onClick(View view) {
            if (view instanceof ImageView){
                mListener.onTomato((ImageView)view);
            } else {
                mListener.onPotato(view);
            }
        }

        public interface IMyViewHolderClicks {
            public void onPotato(View caller);
            public void onTomato(ImageView callerImage);
        }
    }
}
