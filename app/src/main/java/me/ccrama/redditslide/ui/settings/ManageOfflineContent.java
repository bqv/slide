package me.ccrama.redditslide.ui.settings;

import android.os.Bundle;
import android.view.ViewGroup;

import ltd.ucode.slide.ui.BaseActivityAnim;
import ltd.ucode.slide.R;


/**
 * Created by l3d00m on 11/13/2015.
 */
public class ManageOfflineContent extends BaseActivityAnim {

    ManageOfflineContentFragment fragment = new ManageOfflineContentFragment(this);

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_manage_history);
        setupAppBar(R.id.toolbar, R.string.manage_offline_content, true, true);

        ((ViewGroup) findViewById(R.id.manage_history)).addView(
                getLayoutInflater().inflate(R.layout.activity_manage_history_child, null));

        fragment.Bind();
    }

}
