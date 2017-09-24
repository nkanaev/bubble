package com.nkanaev.comics.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.nkanaev.comics.R;

/**
 * Created by sean on 01/10/17.
 */

public class IsReadImageView extends ImageView {

    public static final int CHECKBOX_TICKED = R.drawable.abc_btn_check_to_on_mtrl_015;
    public static final int CHECKBOX_EMPTY = R.drawable.abc_btn_check_to_on_mtrl_000;

    public static final int COLOUR_READ = R.color.circle_green;
    public static final int COLOUR_UNREAD = R.color.darkest;

    private boolean isRead = false;

    public IsReadImageView(Context context) {
        super(context);
    }

    public IsReadImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public boolean isRead() {
        return isRead;
    }

    public void setIsRead(boolean isRead) {
        this.isRead = isRead;
        this.setImageResource(isRead ? CHECKBOX_TICKED : CHECKBOX_EMPTY);
        this.setColorFilter(getResources().getColor(isRead ? COLOUR_READ : COLOUR_UNREAD));
    }
}
