package com.nkanaev.comics.fragment;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.*;

import com.nkanaev.comics.Constants;
import com.nkanaev.comics.MainApplication;
import com.nkanaev.comics.R;
import com.nkanaev.comics.activity.MainActivity;
import com.nkanaev.comics.managers.DirectoryListingManager;
import com.nkanaev.comics.managers.Scanner;
import com.nkanaev.comics.managers.Utils;
import com.nkanaev.comics.model.Comic;
import com.nkanaev.comics.model.Storage;
import com.nkanaev.comics.view.DirectorySelectDialog;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;


public class LibraryFragment extends Fragment
        implements
        DirectorySelectDialog.OnDirectorySelectListener,
        LibraryBrowserAdapter.LibraryItemClickListener,
        SwipeRefreshLayout.OnRefreshListener {
    private final static String BUNDLE_DIRECTORY_DIALOG_SHOWN = "BUNDLE_DIRECTORY_DIALOG_SHOWN";

    private DirectoryListingManager mComicsListManager;
    private DirectorySelectDialog mDirectorySelectDialog;
    private SwipeRefreshLayout mRefreshLayout;
    private View mEmptyView;
    private RecyclerView recyclerView;
    private LibraryBrowserAdapter libraryBrowserAdapter;
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
        if (Scanner.getInstance().isRunning()) {
            setLoading(true);
        }
    }

    @Override
    public void onPause() {
        Scanner.getInstance().removeUpdateHandler(mUpdateHandler);
        super.onPause();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final ViewGroup view = (ViewGroup) inflater.inflate(R.layout.fragment_library, container, false);

        mRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.fragmentLibraryLayout);
        mRefreshLayout.setColorSchemeColors(getActivity().getResources().getColor(R.color.primary));
        mRefreshLayout.setOnRefreshListener(this);
        mRefreshLayout.setEnabled(true);

        int deviceWidth = Utils.getDeviceWidth(getActivity());
        int columnWidth = getActivity().getResources().getInteger(R.integer.grid_group_column_width);
        int numColumns = Math.round((float) deviceWidth / columnWidth);

        libraryBrowserAdapter = new LibraryBrowserAdapter(((MainActivity) getActivity()).getPicasso());
        libraryBrowserAdapter.setOnItemClickListener(this);
        libraryBrowserAdapter.setCompact(MainApplication.getPreferences().getBoolean(Constants.SETTINGS_LIBRARY_COMPACT_LIST, false));
        recyclerView = (RecyclerView) view.findViewById(R.id.groupRecyclerView);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(), numColumns);
        recyclerView.setLayoutManager(gridLayoutManager);
        recyclerView.setAdapter(libraryBrowserAdapter);
        swapData();

        mEmptyView = view.findViewById(R.id.library_empty);

        showEmptyMessage(mComicsListManager.getCount() == 0);
        getActivity().setTitle(R.string.menu_library);

        return view;
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem menuItem = menu.findItem(R.id.menuChangeLayoutView);
        if(libraryBrowserAdapter.isCompactList()) {
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
        inflater.inflate(R.menu.library, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuLibrarySetDir: {
                if (Scanner.getInstance().isRunning()) {
                    Scanner.getInstance().stop();
                }
                mDirectorySelectDialog.show();
                return true;
            }
            case R.id.menuChangeLayoutView: {
                SharedPreferences preferences = MainApplication.getPreferences();
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean(Constants.SETTINGS_LIBRARY_COMPACT_LIST, !preferences.getBoolean(Constants.SETTINGS_LIBRARY_COMPACT_LIST, false));
                editor.commit();
                libraryBrowserAdapter.setCompact(preferences.getBoolean(Constants.SETTINGS_LIBRARY_COMPACT_LIST, false));
                swapData();
                getActivity().invalidateOptionsMenu();
                return true;
            }
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
    public void onLibraryItemClick(int position) {
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
        mComicsListManager = new DirectoryListingManager(comics, getLibraryDir());
    }

    private void refreshLibraryDelayed() {
        if (!mIsRefreshPlanned) {
            final Runnable updateRunnable = new Runnable() {
                @Override
                public void run() {
                    getComics();
                    swapData();
                    mIsRefreshPlanned = false;
                }
            };
            mIsRefreshPlanned = true;
            recyclerView.postDelayed(updateRunnable, 100);
        }
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            mRefreshLayout.setRefreshing(true);
            libraryBrowserAdapter.setOnItemClickListener(null);
        }
        else {
            mRefreshLayout.setRefreshing(false);
            showEmptyMessage(mComicsListManager.getCount() == 0);
            libraryBrowserAdapter.setOnItemClickListener(this);
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
                fragment.swapData();
                fragment.setLoading(false);
            }
        }
    }

    public void swapData(){
        libraryBrowserAdapter.swapData(mComicsListManager);
    }
}
