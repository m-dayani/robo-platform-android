package com.dayani.m.roboplatform.utils.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dayani.m.roboplatform.R;


public class BtPeersAdapter extends RecyclerView.Adapter<BtPeersAdapter.ViewHolder> {

    private String[] peerNames;
    private final PeersListInteraction peersClickListener;

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;

        public ViewHolder(View view) {
            super(view);
            textView = view.findViewById(R.id.btPeerName);
        }

        public TextView getTextView() {
            return textView;
        }

        public void bind(final String name, final PeersListInteraction listener) {
            textView.setText(name);
            if (listener != null) {
                itemView.setOnClickListener(v -> listener.onItemClicked(name));
            }
        }
    }

    /**
     * Initialize the dataset of the Adapter.
     *
     * @param dataSet String[] containing the data to populate views to be used
     * by RecyclerView.
     */
    public BtPeersAdapter(String[] dataSet, PeersListInteraction listener) {
        peerNames = dataSet;
        peersClickListener = listener;
    }

    // Create new views (invoked by the layout manager)
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view, which defines the UI of the list item
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.bt_peer_item, viewGroup, false);

        return new ViewHolder(view);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        viewHolder.bind(peerNames[position], peersClickListener);
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return peerNames.length;
    }

    public void setPeerNames(String[] names) { peerNames = names; }

    public interface PeersListInteraction {

        void onItemClicked(String item);
    }
}

