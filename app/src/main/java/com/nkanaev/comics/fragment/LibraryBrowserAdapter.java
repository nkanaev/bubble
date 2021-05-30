package com.nkanaev.comics.fragment;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.nkanaev.comics.R;
import com.nkanaev.comics.managers.DirectoryListingManager;
import com.nkanaev.comics.managers.LocalCoverHandler;
import com.nkanaev.comics.model.Comic;
import com.nkanaev.comics.view.GroupImageView;
import com.squareup.picasso.Picasso;

/**
 * Created by fred on 7/8/17.
 */

class LibraryBrowserAdapter extends RecyclerView.Adapter<LibraryBrowserAdapter.GroupHolder> implements View.OnClickListener{

    private DirectoryListingManager comicsListManager;
    private LibraryItemClickListener clickListener;
    private Picasso picasso;
    private boolean compactList;
    private final String TAG = getClass().getSimpleName();

    LibraryBrowserAdapter(Picasso picasso){
        this.picasso = picasso;
    }

    void setOnItemClickListener(LibraryItemClickListener clickListener){
        this.clickListener = clickListener;
    }

    void setCompact(boolean compactList){
        this.compactList = compactList;
    }

    boolean isCompactList() {
        return compactList;
    }

    void swapData(DirectoryListingManager comicsListManager){
        this.comicsListManager = comicsListManager;
        notifyDataSetChanged();
    }

    @Override
    public GroupHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new GroupHolder(LayoutInflater.from(parent.getContext())
                .inflate(viewType == 1 ? R.layout.card_group_compact : R.layout.card_group, parent, false));
    }

    @Override
    public void onBindViewHolder(GroupHolder holder, int position) {
        holder.bind(comicsListManager.getComicAtIndex(position),
                comicsListManager.getDirectoryDisplayAtIndex(position),
                comicsListManager.getComicChapterCount(position));
        holder.itemView.setTag(position);
        holder.itemView.setOnClickListener(this);
    }

    @Override
    public int getItemCount() {
        return comicsListManager == null ? 0 : comicsListManager.getCount();
    }

    @Override
    public int getItemViewType(int position) {
        return compactList ? 1 : 0;
    }

    @Override
    public void onClick(View view) {
        clickListener.onLibraryItemClick((int)view.getTag());
    }

    class GroupHolder extends RecyclerView.ViewHolder{
        private ImageView groupImageView;
        private TextView tv;
        private TextView tvGroupItems;

        GroupHolder(View itemView) {
            super(itemView);
            groupImageView = (ImageView) itemView.findViewById(R.id.card_group_imageview);
            tv = (TextView) itemView.findViewById(R.id.comic_group_folder);
            tvGroupItems = (TextView) itemView.findViewById(R.id.comic_group_items);
        }

        void bind(Comic comic, String dirDisplay, int items){
            tv.setText(dirDisplay);
            if(tvGroupItems != null) tvGroupItems.setText(itemView.getContext().getString(R.string.directory_item_count, items));
            picasso.load(LocalCoverHandler.getComicCoverUri(comic))
                    .into(groupImageView);
        }
    }

    interface LibraryItemClickListener {
        void onLibraryItemClick(int position);
    }
}
