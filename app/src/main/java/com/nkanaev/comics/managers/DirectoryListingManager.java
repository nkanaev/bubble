package com.nkanaev.comics.managers;

import android.text.TextUtils;
import com.nkanaev.comics.model.Comic;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DirectoryListingManager {
    private final List<Comic> mComics;
    private final List<String> mDirectoryDisplays;
    private final List<Boolean> mIsRead;
    private final File mLibraryDir;

    public DirectoryListingManager(List<Comic> comics, List<Boolean> areAllRead, String libraryDir) {
        mComics = comics;
        mIsRead = new ArrayList<>(areAllRead);
        mLibraryDir = new File(libraryDir != null ? libraryDir : "/");

        List<String> directoryDisplays = new ArrayList<>();
        for (Comic comic : mComics) {
            File comicDir = comic.getFile().getParentFile();
            if (comicDir.equals(mLibraryDir)) {
                directoryDisplays.add("~ (" + comicDir.getName() + ")");
            }
            else if (comicDir.getParentFile().equals(mLibraryDir)) {
                directoryDisplays.add(comicDir.getName());
            }
            else {
                List<String> intermediateDirs = new ArrayList<>();
                File current = comicDir;

                while (current != null && !current.equals(mLibraryDir)) {
                    intermediateDirs.add(0, current.getName());
                    current = current.getParentFile();
                }
                if (current == null) {
                    // impossible, but occurs
                    directoryDisplays.add(comicDir.getName());
                }
                else {
                    directoryDisplays.add(TextUtils.join(" | ", intermediateDirs));
                }
            }
        }

        mDirectoryDisplays = directoryDisplays;
    }

    public String getDirectoryDisplayAtIndex(int idx) {
        return mDirectoryDisplays.get(idx);
    }

    public Boolean getReadStatusAtIndex(int idx) {
        return mIsRead.get(idx);
    }

    public Comic getComicAtIndex(int idx) {
        return mComics.get(idx);
    }

    public String getDirectoryAtIndex(int idx) {
        return mComics.get(idx).getFile().getParent();
    }

    public int getCount() {
        return mComics.size();
    }
}
