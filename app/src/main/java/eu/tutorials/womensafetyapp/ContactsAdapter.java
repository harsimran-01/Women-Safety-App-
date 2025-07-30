package eu.tutorials.womensafetyapp;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ViewHolder> {

    public interface OnContactRemoveListener {
        void onContactRemove(int position);
    }

    private List<String> contacts;
    private OnContactRemoveListener listener;

    public ContactsAdapter(List<String> contacts, OnContactRemoveListener listener) {
        this.contacts = contacts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ContactsAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactsAdapter.ViewHolder holder, int position) {
        String contact = contacts.get(position);
        holder.tvContact.setText(contact);
        holder.btnRemove.setOnClickListener(v -> {
            if (listener != null) {
                listener.onContactRemove(position);
            }
        });
    }

    @Override
    public int getItemCount() { return contacts.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvContact;
        ImageButton btnRemove;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContact = itemView.findViewById(R.id.tvContact);
            btnRemove = itemView.findViewById(R.id.btnRemoveContact);
        }
    }
}