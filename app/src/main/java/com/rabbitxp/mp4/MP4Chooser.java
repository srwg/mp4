package com.rabbitxp.mp4;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;


public class MP4Chooser extends ListActivity implements OnItemClickListener {
    /**
     * Called when the activity is first created.
     */

    public static File PATH;
    public static File PATH_CUT;
    private String[] m_Files;

    @SuppressLint("NewApi")
    @Override
    public void onCreate(Bundle bd) {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1
            );
        }
        //File[] paths = this.getExternalFilesDirs(null);
        File path = new File("/sdcard/RRMJ");
        PATH = new File(path, "download");
        PATH_CUT = new File(path, "cut");
        super.onCreate(bd);
        if (bd != null) {
            m_Files = bd.getStringArray("mp4_files");
        } else {
            FilenameFilter filter = new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith("mp4");
                }
            };
            File[] fs = PATH.listFiles(filter);
            m_Files = new String[fs.length];
            for (int i = 0; i < fs.length; ++i) {
                String fn = fs[i].getName();
                m_Files[i] = fn.substring(0, fn.length() - 4);
            }
        }

        setListAdapter(new ArrayAdapter<String>(this, R.layout.mp4item, m_Files) {
            @Override
            public View getView(int pos, View v, ViewGroup p) {
                if (v == null) {
                    LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    v = vi.inflate(R.layout.mp4item, null);
                }
                ((TextView) v).setText(m_Files[pos]);
                return v;
            }
        });

        getListView().setOnItemClickListener(this);
    }


    @Override
    protected void onSaveInstanceState(Bundle bd) {
        bd.putStringArray("mp4_files", m_Files);
    }

    @Override
    public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
        String label = (String) ((TextView) v).getText();
        if (label.contains("Error")) return;
        try {
            String fn = PATH + "/" + label;
            int[] time_table = new MP4Info(new File(fn + ".mp4")).getKeyTimes();
            Intent show_movie = new Intent(getBaseContext(), CutterGUI.class);
            Bundle bd = new Bundle();
            bd.putString("mp4_fn", fn);
            bd.putString("cut_fn", PATH_CUT + "/" + label + ".cut");
            bd.putIntArray("key_time", time_table);
            show_movie.putExtras(bd);
            startActivity(show_movie);
        } catch (IOException e) {
            ((TextView) v).setText("Error: " + e.getMessage());
        }
    }
}
