package me.ccrama.redditslide.ui.settings;

import android.os.Bundle;
import android.view.ViewGroup;

import ltd.ucode.slide.ui.BaseActivityAnim;
import ltd.ucode.slide.R;


/**
 * Created by l3d00m on 11/13/2015.
 */
public class SettingsHandling extends BaseActivityAnim {

    private SettingsHandlingFragment fragment = new SettingsHandlingFragment(this);

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_settings_handling);
        setupAppBar(R.id.toolbar, R.string.settings_link_handling, true, true);

        ((ViewGroup) findViewById(R.id.settings_handling)).addView(
                getLayoutInflater().inflate(R.layout.activity_settings_handling_child, null));

        fragment.Bind();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

}
