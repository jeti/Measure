package io.jeti.measure.server.area;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import io.jeti.measure.server.area.AreaAdapter.AreaViewHolder;
import io.jeti.measure.R;
import io.realm.OrderedRealmCollection;
import io.realm.RealmRecyclerViewAdapter;

public class AreaAdapter extends RealmRecyclerViewAdapter<Area, AreaViewHolder> {

    private final View.OnClickListener listener;

    public AreaAdapter(OrderedRealmCollection<Area> areaList, View.OnClickListener listener) {
        super(areaList, true);
        this.listener = listener;
    }

    @Override
    public AreaViewHolder onCreateViewHolder(ViewGroup parent, final int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.area_list_item, parent, false);
        view.setOnClickListener(listener);
        return new AreaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(AreaViewHolder holder, int position) {
        Area area = getData().get(position);
        holder.setArea(area);
    }

    public static class AreaViewHolder extends RecyclerView.ViewHolder {

        private final TextView areaName;
        private final TextView areaDate;
        private final TextView areaUUID;
        private final TextView areaFile;

        public AreaViewHolder(View view) {
            super(view);
            areaName = view.findViewById(R.id.area_text);
            areaDate = view.findViewById(R.id.area_date);
            areaUUID = view.findViewById(R.id.area_uuid);
            areaFile = view.findViewById(R.id.area_file);
        }

        public void setArea(Area area) {
            areaName.setText(area.getName());
            areaDate.setText(area.getDateString());
            areaUUID.setText(area.getUuid());
            String file = area.getFile();
            if (area.getFile() != null) {
                file = "";
            }
            areaFile.setText(file);
        }
    }
}