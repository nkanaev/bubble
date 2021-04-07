package com.nkanaev.comics.model;

import java.io.File;


public class Comic implements Comparable {
    private Storage mShelf;
    private int mCurrentPage;
    private int mNumPages;
    private int mId;
    private String mType;
    private File mFile;
    public final long updatedAt;

    public Comic(Storage shelf, int id, String filepath, String filename,
                 String type, int numPages, int currentPage, long updatedAt) {
        mShelf = shelf;
        mId = id;
        mNumPages = numPages;
        mCurrentPage = currentPage;
        mFile = new File(filepath, filename);
        mType = type;
        this.updatedAt = updatedAt;
    }

    public int getId() {
        return mId;
    }

    public File getFile() {
        return mFile;
    }

    public int getCurrentPage() {
        return mCurrentPage;
    }

    public int getTotalPages() {
        return mNumPages;
    }

    public void setCurrentPage(int page) {
        mShelf.bookmarkPage(getId(), page);
        mCurrentPage = page;
    }

    public boolean isRead() {
        return mCurrentPage == mNumPages;
    }

    public boolean isStarted() {
        return mCurrentPage != 0;
    }

    public String getType() {
        return mType;
    }

    public int compareTo(Object another) {
        return mFile.compareTo(((Comic) another).getFile());
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof Comic) && getId() == ((Comic)o).getId();
    }

    @Override
    public int hashCode() {
        return mId;
    }

    @Override
    public String toString() {
        return "Comic{" +
                "mCurrentPage=" + mCurrentPage +
                ", mNumPages=" + mNumPages +
                ", mId=" + mId +
                ", mType='" + mType + '\'' +
                ", mFile=" + mFile +
                ", updatedAt=" + updatedAt +
                '}';
    }
}