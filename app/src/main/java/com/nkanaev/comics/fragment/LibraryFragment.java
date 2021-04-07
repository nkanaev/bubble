package com.nkanaev.comics.fragment;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.*;
import android.widget.*;
import com.nkanaev.comics.Constants;
import com.nkanaev.comics.MainApplication;
import com.nkanaev.comics.R;
import com.nkanaev.comics.activity.MainActivity;
import com.nkanaev.comics.managers.DirectoryListingManager;
import com.nkanaev.comics.managers.LocalCoverHandler;
import com.nkanaev.comics.managers.Scanner;
import com.nkanaev.comics.managers.Utils;
import com.nkanaev.comics.model.Comic;
import com.nkanaev.comics.model.Storage;
import com.nkanaev.comics.view.DirectorySelectDialog;
import com.nkanaev.comics.view.IsReadImageView;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;


public class LibraryFragment extends Fragment
        implements
        DirectorySelectDialog.OnDirectorySelectListener,
        AdapterView.OnItemClickListener,
        SwipeRefreshLayout.OnRefreshListener {
    private final static String BUNDLE_DIRECTORY_DIALOG_SHOWN = "BUNDLE_DIRECTORY_DIALOG_SHOWN";

    private DirectoryListingManager mComicsListManager;
    private DirectorySelectDialog mDirectorySelectDialog;
    private SwipeRefreshLayout mRefreshLayout;
    private View mEmptyView;
    private GridView mGridView;
    private Picasso mPicasso;
    private boolean mIsRefreshPlanned = false;
    private Handler mUpdateHandler = new UpdateHandler(this);

    public LibraryFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDirectorySelectDialog = new DirectorySelectDialog(getActivity());
        mDirectorySelectDialog.setCurrentDirectory(Environment.getExternalStorageDirectory());
        mDirectorySelectDialog.setOnDirectorySelectListener(this);

        getComics();

        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        Scanner.getInstance().addUpdateHandler(mUpdateHandler);
        Scanner.getInstance().scanLibrary();
        setLoading(true);
    }

    @Override
    public void onPause() {
        Scanner.getInstance().removeUpdateHandler(mUpdateHandler);
        super.onPause();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final ViewGroup view = (ViewGroup) inflater.inflate(R.layout.fragment_library, container, false);

        mPicasso = ((MainActivity) getActivity()).getPicasso();

        mRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.fragmentLibraryLayout);
        mRefreshLayout.setColorSchemeColors(R.color.primary);
        mRefreshLayout.setOnRefreshListener(this);
        mRefreshLayout.setEnabled(true);

        mGridView = (GridView) view.findViewById(R.id.groupGridView);
        mGridView.setAdapter(new GroupBrowserAdapter());
        mGridView.setOnItemClickListener(this);

        mEmptyView = view.findViewById(R.id.library_empty);

        int deviceWidth = Utils.getDeviceWidth(getActivity());
        int columnWidth = getActivity().getResources().getInteger(R.integer.grid_group_column_width);
        int numColumns = Math.round((float) deviceWidth / columnWidth);
        mGridView.setNumColumns(numColumns);

        showEmptyMessage(mComicsListManager.getCount() == 0);
        getActivity().setTitle(R.string.menu_library);

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.library, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menuLibrarySetDir) {
            if (Scanner.getInstance().isRunning()) {
                Scanner.getInstance().stop();
            }

            mDirectorySelectDialog.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(BUNDLE_DIRECTORY_DIALOG_SHOWN,
                (mDirectorySelectDialog != null) && mDirectorySelectDialog.isShowing());
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDirectorySelect(File file) {
        SharedPreferences preferences = MainApplication.getPreferences();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(Constants.SETTINGS_LIBRARY_DIR, file.getAbsolutePath());
        editor.apply();

        Scanner.getInstance().forceScanLibrary();
        showEmptyMessage(false);
        setLoading(true);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String path = mComicsListManager.getDirectoryAtIndex(position);
        LibraryBrowserFragment fragment = LibraryBrowserFragment.create(path);
        ((MainActivity)getActivity()).pushFragment(fragment);
    }

    @Override
    public void onRefresh() {
        if (!Scanner.getInstance().isRunning()) {
            setLoading(true);
            Scanner.getInstance().scanLibrary();
        }
    }

    private void getComics() {
        List<Comic> comics = Storage.getStorage(getActivity()).listDirectoryComics();
        final List<Boolean> directoryReadStatus = getComicDirectoryReadStatus(comics);
        mComicsListManager = new DirectoryListingManager(comics, directoryReadStatus, getLibraryDir());
    }

    private List<Boolean> getComicDirectoryReadStatus(final List<Comic> topLevelComics) {
        final List<Boolean> isRead = new ArrayList<>();
        for (final Comic comic : topLevelComics) {
            isRead.add(areAllComicsInDirRead(comic.getFile().getParentFile()));
        }
        return isRead;
    }

    private boolean areAllComicsInDirRead(final File comicDir) {
        final List<Comic> comics = Storage.getStorage(getActivity()).listComics(comicDir.getPath());
        boolean areAllComicsRead = true;

        for (final Comic comic : comics) {
            areAllComicsRead = areAllComicsRead & comic.isRead();
        }
        return areAllComicsRead;
    }

    private void refreshLibraryDelayed() {
        if (!mIsRefreshPlanned) {
            final Runnable updateRunnable = new Runnable() {
                @Override
                public void run() {
                    getComics();
                    ((BaseAdapter)mGridView.getAdapter()).notifyDataSetChanged();
                    mIsRefreshPlanned = false;
                }
            };
            mIsRefreshPlanned = true;
            mGridView.postDelayed(updateRunnable, 100);
        }
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            mRefreshLayout.setRefreshing(true);
            mGridView.setOnItemClickListener(null);
        }
        else {
            mRefreshLayout.setRefreshing(false);
            showEmptyMessage(mComicsListManager.getCount() == 0);
            mGridView.setOnItemClickListener(this);
        }
    }

    private String getLibraryDir() {
        return getActivity()
                .getSharedPreferences(Constants.SETTINGS_NAME, 0)
                .getString(Constants.SETTINGS_LIBRARY_DIR, null);
    }

    private void showEmptyMessage(boolean show) {
        mEmptyView.setVisibility(show ? View.VISIBLE : View.GONE);
        mRefreshLayout.setEnabled(!show);
    }

    private static class UpdateHandler extends Handler {
        private WeakReference<LibraryFragment> mOwner;

        public UpdateHandler(LibraryFragment fragment) {
            mOwner = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            LibraryFragment fragment = mOwner.get();
            if (fragment == null) {
                return;
            }

            if (msg.what == Constants.MESSAGE_MEDIA_UPDATED) {
                fragment.refreshLibraryDelayed();
            }
            else if (msg.what == Constants.MESSAGE_MEDIA_UPDATE_FINISHED) {
                fragment.getComics();
                ((BaseAdapter)fragment.mGridView.getAdapter()).notifyDataSetChanged();
                fragment.setLoading(false);
            }
        }
    }

    private final class GroupBrowserAdapter extends BaseAdapter implements View.OnClickListener {
        @Override
        public int getCount() {
            return mComicsListManager.getCount();
        }

        @Override
        public Object getItem(int position) {
            return mComicsListManager.getComicAtIndex(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Comic comic = mComicsListManager.getComicAtIndex(position);
            String dirDisplay = mComicsListManager.getDirectoryDisplayAtIndex(position);
            Boolean isAllRead = mComicsListManager.getReadStatusAtIndex(position);

            if (convertView == null) {
                convertView = getActivity().getLayoutInflater().inflate(R.layout.card_group, parent, false);
            }

            ImageView groupImageView = (ImageView)convertView.findViewById(R.id.card_group_imageview);

            mPicasso.load(LocalCoverHandler.getComicCoverUri(comic))
                    .into(groupImageView);

            TextView tv = (TextView) convertView.findViewById(R.id.comic_group_folder);
            tv.setText(dirDisplay);

            IsReadImageView groupIsReadImageView = (IsReadImageView) convertView.findViewById(R.id.comic_group_isRead);
            groupIsReadImageView.setIsRead(isAllRead);
            groupIsReadImageView.setClickable(true);
            groupIsReadImageView.setOnClickListener(this);

            return convertView;
        }

        @Override
        public void onClick(View view) {
            final int position = mGridView.getPositionForView(view);
            final boolean isRead = mComicsListManager.getReadStatusAtIndex(position);
            final String directory = mComicsListManager.getDirectoryAtIndex(position);
            final List<Comic> comics = Storage.getStorage(getActivity()).listComics(directory);

            for (Comic comic : comics) {
                comic.setCurrentPage(isRead ? 0 : comic.getTotalPages());
            }
            mUpdateHandler.sendEmptyMessage(Constants.MESSAGE_MEDIA_UPDATED);
        }
    }
}
