package com.nkanaev.comics.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.nkanaev.comics.Constants;
import com.nkanaev.comics.MainApplication;
import com.nkanaev.comics.R;
import com.nkanaev.comics.activity.MainActivity;
import com.nkanaev.comics.activity.ReaderActivity;
import com.nkanaev.comics.managers.LocalCoverHandler;
import com.nkanaev.comics.managers.Utils;
import com.nkanaev.comics.model.Comic;
import com.nkanaev.comics.model.Storage;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LibraryBrowserFragment extends Fragment
        implements SearchView.OnQueryTextListener {
    public static final String PARAM_PATH = "browserCurrentPath";

    final int ITEM_VIEW_TYPE_COMIC = 1;
    final int ITEM_VIEW_TYPE_COMIC_COMPACT = 10;
    final int ITEM_VIEW_TYPE_HEADER_RECENT = 2;
    final int ITEM_VIEW_TYPE_HEADER_ALL = 3;

    final int NUM_HEADERS = 2;

    private List<Comic> mComics = new ArrayList<>();
    private List<Comic> mAllItems = new ArrayList<>();
    private List<Comic> mRecentItems = new ArrayList<>();

    private String mPath;
    private String mFilterSearch = "";
    private Picasso mPicasso;
    private int mFilterRead = R.id.menu_browser_filter_all;
    private boolean compactList;
    private ComicGridAdapter comicGridAdapter;
    private GridSpacingItemDecoration gridSpacingItemDecoration;

    private RecyclerView mComicListView;

    public static LibraryBrowserFragment create(String path) {
        LibraryBrowserFragment fragment = new LibraryBrowserFragment();
        Bundle args = new Bundle();
        args.putString(PARAM_PATH, path);
        fragment.setArguments(args);
        return fragment;
    }

    public LibraryBrowserFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPath = getArguments().getString(PARAM_PATH);
        getComics();
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_librarybrowser, container, false);
        compactList = MainApplication.getPreferences().getBoolean(Constants.SETTINGS_LIBRARY_BROWSER_COMPACT_LIST, false);
        final int numColumns = calculateNumColumns();
        int spacing = (int) getResources().getDimension(R.dimen.grid_margin);
        gridSpacingItemDecoration = new GridSpacingItemDecoration(numColumns, spacing);

        GridLayoutManager layoutManager = new GridLayoutManager(getActivity(), numColumns);
        layoutManager.setSpanSizeLookup(createSpanSizeLookup());

        mComicListView = (RecyclerView) view.findViewById(R.id.library_grid);
        mComicListView.setHasFixedSize(true);
        mComicListView.setLayoutManager(layoutManager);
        comicGridAdapter = new ComicGridAdapter();
        mComicListView.setAdapter(comicGridAdapter);
        mComicListView.removeItemDecoration(gridSpacingItemDecoration);
        mComicListView.addItemDecoration(gridSpacingItemDecoration);

        getActivity().setTitle(new File(getArguments().getString(PARAM_PATH)).getName());
        mPicasso = ((MainActivity) getActivity()).getPicasso();

        return view;
    }

    @Override
    public void onResume() {
        getComics();
        super.onResume();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem menuItem = menu.findItem(R.id.menuChangeLayoutView);
        if(compactList) {
            menuItem.setTitle(getString(R.string.large_list));
            menuItem.setIcon(R.drawable.ic_view_large_list_white_24dp);
        } else {
            menuItem.setTitle(getString(R.string.compact_list));
            menuItem.setIcon(R.drawable.ic_view_compact_list_white_24dp);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.browser, menu);

        MenuItem searchItem = menu.findItem(R.id.search);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        searchView.setOnQueryTextListener(this);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_browser_filter_all:
            case R.id.menu_browser_filter_read:
            case R.id.menu_browser_filter_unread:
            case R.id.menu_browser_filter_unfinished:
            case R.id.menu_browser_filter_reading:
                item.setChecked(true);
                mFilterRead = item.getItemId();
                filterContent();
                return true;
            case R.id.menuChangeLayoutView:
                SharedPreferences preferences = MainApplication.getPreferences();
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean(Constants.SETTINGS_LIBRARY_BROWSER_COMPACT_LIST,
                        !preferences.getBoolean(Constants.SETTINGS_LIBRARY_BROWSER_COMPACT_LIST, false));
                editor.commit();
                compactList = preferences.getBoolean(Constants.SETTINGS_LIBRARY_BROWSER_COMPACT_LIST, false);
                comicGridAdapter.notifyDataSetChanged();
                recalculateSpansAndSpacing();
                getActivity().invalidateOptionsMenu();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void recalculateSpansAndSpacing() {
        int span = calculateNumColumns();
        ((GridLayoutManager) mComicListView.getLayoutManager()).setSpanCount(span);
        ((GridLayoutManager) mComicListView.getLayoutManager()).setSpanSizeLookup(createSpanSizeLookup());
        mComicListView.removeItemDecoration(gridSpacingItemDecoration);
        gridSpacingItemDecoration = new GridSpacingItemDecoration(span, (int) getResources().getDimension(R.dimen.grid_margin));
        mComicListView.addItemDecoration(gridSpacingItemDecoration);
    }

    @Override
    public boolean onQueryTextChange(String s) {
        mFilterSearch = s;
        filterContent();
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String s) {
        return true;
    }

    public void openComic(Comic comic) {
        if (!comic.getFile().exists()) {
            Toast.makeText(
                    getActivity(),
                    R.string.warning_missing_file,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(getActivity(), ReaderActivity.class);
        intent.putExtra(ReaderFragment.PARAM_HANDLER, comic.getId());
        intent.putExtra(ReaderFragment.PARAM_MODE, ReaderFragment.Mode.MODE_LIBRARY);
        startActivity(intent);
    }

    private void getComics() {
        mComics = Storage.getStorage(getActivity()).listComics(mPath);
        findRecents();
        filterContent();
    }

    private void findRecents() {
        mRecentItems.clear();

        for (Comic c : mComics) {
            if (c.updatedAt > 0) {
                mRecentItems.add(c);
            }
        }

        if (mRecentItems.size() > 0) {
            Collections.sort(mRecentItems, new Comparator<Comic>() {
                @Override
                public int compare(Comic lhs, Comic rhs) {
                    return lhs.updatedAt > rhs.updatedAt ? -1 : 1;
                }
            });
        }

        if (mRecentItems.size() > Constants.MAX_RECENT_COUNT) {
            mRecentItems
                    .subList(Constants.MAX_RECENT_COUNT, mRecentItems.size())
                    .clear();
        }
    }

    private void filterContent() {
        mAllItems.clear();

        for (Comic c : mComics) {
            if (mFilterSearch.length() > 0 && !c.getFile().getName().contains(mFilterSearch))
                continue;
            if (mFilterRead != R.id.menu_browser_filter_all) {
                if (mFilterRead == R.id.menu_browser_filter_read && c.getCurrentPage() != c.getTotalPages())
                    continue;
                if (mFilterRead == R.id.menu_browser_filter_unread && c.getCurrentPage() != 0)
                    continue;
                if (mFilterRead == R.id.menu_browser_filter_unfinished && c.getCurrentPage() == c.getTotalPages())
                    continue;
                if (mFilterRead == R.id.menu_browser_filter_reading &&
                        (c.getCurrentPage() == 0 || c.getCurrentPage() == c.getTotalPages()))
                    continue;
            }
            mAllItems.add(c);
        }

        if (mComicListView != null) {
            mComicListView.getAdapter().notifyDataSetChanged();
        }
    }

    private Comic getComicAtPosition(int position) {
        Comic comic;
        if (hasRecent()) {
            if (position > 0 && position < mRecentItems.size() + 1)
                comic = mRecentItems.get(position - 1);
            else
                comic = mAllItems.get(position - mRecentItems.size() - NUM_HEADERS);
        }
        else {
            comic = mAllItems.get(position);
        }
        return comic;
    }

    private int getItemViewTypeAtPosition(int position) {
        if (hasRecent()) {
            if (position == 0)
                return ITEM_VIEW_TYPE_HEADER_RECENT;
            else if (position == mRecentItems.size() + 1)
                return ITEM_VIEW_TYPE_HEADER_ALL;
        }
        return compactList ? ITEM_VIEW_TYPE_COMIC_COMPACT : ITEM_VIEW_TYPE_COMIC;
    }

    private boolean hasRecent() {
        return mFilterSearch.length() == 0
                && mFilterRead == R.id.menu_browser_filter_all
                && mRecentItems.size() > 0;
    }

    private int calculateNumColumns() {
        int deviceWidth = Utils.getDeviceWidth(getActivity());
        int columnWidth = compactList ?
                getActivity().getResources().getInteger(R.integer.grid_group_column_width) :
                getActivity().getResources().getInteger(R.integer.grid_comic_column_width);

        return Math.round((float) deviceWidth / columnWidth);
    }

    private GridLayoutManager.SpanSizeLookup createSpanSizeLookup() {
        final int numColumns = calculateNumColumns();

        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (getItemViewTypeAtPosition(position) == ITEM_VIEW_TYPE_COMIC
                        || getItemViewTypeAtPosition(position) == ITEM_VIEW_TYPE_COMIC_COMPACT)
                    return 1;
                return numColumns;
            }
        };
    }

    private final class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private int mSpanCount;
        private int mSpacing;

        public GridSpacingItemDecoration(int spanCount, int spacing) {
            mSpanCount = spanCount;
            mSpacing = spacing;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);

            if (hasRecent()) {
                // those are headers
                if (position == 0 || position == mRecentItems.size() + 1)
                    return;

                if (position > 0 && position < mRecentItems.size() + 1) {
                    position -= 1;
                }
                else {
                    position -= (NUM_HEADERS + mRecentItems.size());
                }
            }

            int column = position % mSpanCount;

            outRect.left = mSpacing - column * mSpacing / mSpanCount;
            outRect.right = (column + 1) * mSpacing / mSpanCount;

            if (position < mSpanCount) {
                outRect.top = mSpacing;
            }
            outRect.bottom = mSpacing;
        }
    }


    private final class ComicGridAdapter extends RecyclerView.Adapter {
        @Override
        public int getItemCount() {
            if (hasRecent()) {
                return mAllItems.size() + mRecentItems.size() + NUM_HEADERS;
            }
            return mAllItems.size();
        }

        @Override
        public int getItemViewType(int position) {
            return getItemViewTypeAtPosition(position);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            Context ctx = viewGroup.getContext();

            if (i == ITEM_VIEW_TYPE_HEADER_RECENT) {
                TextView view = (TextView) LayoutInflater.from(ctx)
                        .inflate(R.layout.header_library, viewGroup, false);
                view.setText(R.string.library_header_recent);

                int spacing = (int) getResources().getDimension(R.dimen.grid_margin);
                RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) view.getLayoutParams();
                lp.setMargins(0, spacing, 0, 0);

                return new HeaderViewHolder(view);
            }
            else if (i == ITEM_VIEW_TYPE_HEADER_ALL) {
                TextView view = (TextView) LayoutInflater.from(ctx)
                        .inflate(R.layout.header_library, viewGroup, false);
                view.setText(R.string.library_header_all);

                return new HeaderViewHolder(view);
            }

            View view = LayoutInflater.from(ctx)
                    .inflate(i == ITEM_VIEW_TYPE_COMIC ? R.layout.card_comic : R.layout.card_comic_compact, viewGroup, false);
            return new ComicViewHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
            if (viewHolder.getItemViewType() == ITEM_VIEW_TYPE_COMIC
                    || viewHolder.getItemViewType() == ITEM_VIEW_TYPE_COMIC_COMPACT) {
                Comic comic = getComicAtPosition(i);
                ComicViewHolder holder = (ComicViewHolder) viewHolder;
                holder.setupComic(comic);
            }
        }
    }

    private class HeaderViewHolder extends RecyclerView.ViewHolder {
        public HeaderViewHolder(View itemView) {
            super(itemView);
        }

        public void setTitle(int titleRes) {
            ((TextView) itemView).setText(titleRes);
        }
    }

    private class ComicViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private ImageView mCoverView;
        private TextView mTitleTextView;
        private TextView mPagesTextView;

        public ComicViewHolder(View itemView) {
            super(itemView);
            mCoverView = (ImageView) itemView.findViewById(R.id.comicImageView);
            mTitleTextView = (TextView) itemView.findViewById(R.id.comicTitleTextView);
            mPagesTextView = (TextView) itemView.findViewById(R.id.comicPagerTextView);

            itemView.setClickable(true);
            itemView.setOnClickListener(this);
        }

        public void setupComic(Comic comic) {
            mTitleTextView.setText(comic.getFile().getName());
            mPagesTextView.setText(Integer.toString(comic.getCurrentPage()) + '/' + Integer.toString(comic.getTotalPages()));
            if(mCoverView != null)
                mPicasso.load(LocalCoverHandler.getComicCoverUri(comic))
                    .into(mCoverView);
        }

        @Override
        public void onClick(View v) {
            int i = getAdapterPosition();
            Comic comic = getComicAtPosition(i);
            openComic(comic);
        }
    }
}
