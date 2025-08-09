package com.worldisfire.dopium;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.worldisfire.dopium.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    //shuffle toggleer
    private boolean isShuffleEnabled = false;

    /** Data model for songs **/
    private static class Song {
        String title;
        String path;
        long durationMs;

        Song(String title, String path, long durationMs) {
            this.title = title;
            this.path = path;
            this.durationMs = durationMs;
        }
    }

    private static final int PERMISSION_REQUEST_CODE = 1;

    private ActivityMainBinding binding;
    private ExoPlayer exoPlayer;
    private int currentGroupPosition = -1;
    private int currentChildPosition = -1;

    private ExpandableListView expSongList;
    private Button playPauseBtn;
    private Slider songSeekBar;
    private TextView nowPlayingTitle;

    private Handler seekHandler = new Handler(Looper.getMainLooper());

    private final List<String> folderList = new ArrayList<>();
    private final Map<String, List<Song>> songMap = new HashMap<>();

    private boolean isValidFolder(String folderName, String filePath) {
        if (folderName.startsWith(".")) return false;
        String pathLower = filePath.toLowerCase();
        if (pathLower.contains("/android/") || pathLower.contains("/system/") || pathLower.contains("/emulated/0/android/")) {
            return false;
        }
        return true;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupNavigation();
        initUI();
        initPlayer();
        requestPermissionsIfNeeded();
    }

    /** Toolbar + Drawer Navigation **/
    private void setupNavigation() {
        Toolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);

        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                isShuffleEnabled = !isShuffleEnabled;  // Toggle shuffle
                String msg = isShuffleEnabled ? "Shuffle ON" : "Shuffle OFF";
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            } else if (id == R.id.nav_slideshow) {
                Toast.makeText(this, "Slideshow clicked", Toast.LENGTH_SHORT).show();
            }
            drawer.closeDrawers();
            return true;
        });

    }
    private TextView nextPlayingTitle;

    /** Initialize UI references **/
    private void initUI() {
        currentTime = findViewById(R.id.currentTime);
        totalTime = findViewById(R.id.totalTime);

        expSongList = findViewById(R.id.expSongList);
        playPauseBtn = findViewById(R.id.playPauseBtn);
        songSeekBar = findViewById(R.id.songSeekBar);
        nowPlayingTitle = findViewById(R.id.nowPlayingTitle);
        nextPlayingTitle = findViewById(R.id.nextPlayingTitle);

    }
    private TextView currentTime;
    private TextView totalTime;

    //helper for next song
private String getNextSongTitle() {
    if (currentGroupPosition == -1 || currentChildPosition == -1) return "";

    List<Song> currentFolderSongs = songMap.get(folderList.get(currentGroupPosition));
    int nextPosition;

    if (isShuffleEnabled) {
        nextPosition = (int)(Math.random() * currentFolderSongs.size());
    } else {
        nextPosition = currentChildPosition + 1;
    }

    if (nextPosition < currentFolderSongs.size()) {
        return currentFolderSongs.get(nextPosition).title;
    } else {
        return "";  // No next song
    }
}

    /** Initialize ExoPlayer **/
    private void initPlayer() {
        exoPlayer = new ExoPlayer.Builder(this).build();

        // Add the playback state listener here
        exoPlayer.addListener(new ExoPlayer.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == ExoPlayer.STATE_ENDED) {
                    playNextSong();
                }
            }
        });
    }
    //play next Song
    private void playNextSong() {
        if (currentGroupPosition == -1 || currentChildPosition == -1) return;

        List<Song> currentFolderSongs = songMap.get(folderList.get(currentGroupPosition));
        int nextPosition ;
        if (isShuffleEnabled) {
            nextPosition = (int) (Math.random() * currentFolderSongs.size());
        } else {
            nextPosition = currentChildPosition + 1;
        }

        if (nextPosition < currentFolderSongs.size()) {
            currentChildPosition = nextPosition;
            Song nextSong = currentFolderSongs.get(nextPosition);
            nowPlayingTitle.setText(nextSong.title);
            playSong(nextSong.path, nextSong.durationMs);
            nowPlayingTitle.setText(nextSong.title);
            nextPlayingTitle.setText("Next: " + getNextSongTitle());

            // Also update UI to highlight new song if needed
        } else {
            // No more songs in this folder; optionally stop or loop
            exoPlayer.stop();
        }
    }



    /** Request storage permissions **/
    private void requestPermissionsIfNeeded() {
        String permission = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[]{permission}, PERMISSION_REQUEST_CODE
            );
        } else {
            loadSongs();
        }
    }

    /** Handle permission result **/
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadSongs();
        } else {
            Snackbar.make(binding.getRoot(),
                    "Permission denied. Music player will not work.",
                    Snackbar.LENGTH_LONG).show();
        }
    }

    /** Query MediaStore and group by folder **/
    private void loadSongs() {
        Uri collection = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION
        };

        try (Cursor cursor = getContentResolver().query(
                collection,
                projection,
                MediaStore.Audio.Media.IS_MUSIC + "!= 0",
                null,
                null
        )) {
            folderList.clear();
            songMap.clear();

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String title = cursor.getString(
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE));
                    String path = cursor.getString(
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));
                    String folder = cursor.getString(
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME));
                    long durationMs = cursor.getLong(
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));
                    if (!isValidFolder(folder, path)) continue;

                    if (!songMap.containsKey(folder)) {
                        folderList.add(folder);
                        songMap.put(folder, new ArrayList<>());
                    }
                    songMap.get(folder).add(new Song(title, path, durationMs));
                }
            }
        }

        // Sort folders and songs
        Collections.sort(folderList, String.CASE_INSENSITIVE_ORDER);
        for (List<Song> list : songMap.values()) {
            Collections.sort(list, (a, b) ->
                    a.title.compareToIgnoreCase(b.title)
            );
        }
        setupExpandableList();
    }

    /** Setup ExpandableListView and play logic **/
    private void setupExpandableList() {
        FolderSongAdapter adapter = new FolderSongAdapter(this, folderList, songMap);
        expSongList.setAdapter(adapter);

        expSongList.setOnChildClickListener((parent, v, groupPosition, childPosition, id) -> {
            currentGroupPosition = groupPosition;
            currentChildPosition = childPosition;


            Song song = songMap.get(folderList.get(groupPosition)).get(childPosition);
            nowPlayingTitle.setText(song.title);
            playSong(song.path, song.durationMs);
            nowPlayingTitle.setText(song.title);
            nextPlayingTitle.setText("Next: " + getNextSongTitle());
            return true;
        });


        playPauseBtn.setOnClickListener(v -> {
            if (exoPlayer.isPlaying()) {
                exoPlayer.pause();
            } else {
                exoPlayer.play();
            }
        });

        songSeekBar.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                exoPlayer.seekTo((long) value);
            }
        });
    }
    private List<String> playQueue = new ArrayList<>(); // List of song paths
    private int currentIndex = -1;
    private boolean isShuffle = false;

    /** Play a single song via ExoPlayer **/
    private void playSong(String path, long durationMs) {
        exoPlayer.stop();
        exoPlayer.clearMediaItems();
        exoPlayer.addMediaItem(MediaItem.fromUri(Uri.parse(path)));
        exoPlayer.prepare();
       //chnged for shuffle
        playQueue.clear();
        currentIndex=-1;
        isShuffle=false;
        exoPlayer.play();


            songSeekBar.setValueFrom(0);
            songSeekBar.setValueTo(durationMs);
            songSeekBar.setValue(0);

            // Set total time label
            totalTime.setText(formatTime(durationMs));
            currentTime.setText("0:00");

            startSeekBarUpdate();


    }

    /** Update seek bar every 50ms **/
    private void startSeekBarUpdate() {
        seekHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null && exoPlayer.isPlaying()) {
                    long currentPos = exoPlayer.getCurrentPosition();
                    songSeekBar.setValue(currentPos);
                    currentTime.setText(formatTime(currentPos));
                }
                seekHandler.postDelayed(this, 500); // update every 0.5s
            }
        }, 0);
    }
    private String formatTime(long millis) {
        int totalSeconds = (int) (millis / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }


    /** Release player resources **/
    @Override
    protected void onDestroy() {
        if (exoPlayer != null) {
            exoPlayer.release();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    /** Adapter for folder→songs grouping **/
    private static class FolderSongAdapter extends BaseExpandableListAdapter {
        private final Context ctx;
        private final List<String> folders;
        private final Map<String, List<Song>> songMap;

        FolderSongAdapter(Context ctx,
                          List<String> folders,
                          Map<String, List<Song>> songMap) {
            this.ctx = ctx;
            this.folders = folders;
            this.songMap = songMap;
        }

        @Override public int getGroupCount() {
            return folders.size();
        }

        @Override public int getChildrenCount(int groupPosition) {
            return songMap.get(folders.get(groupPosition)).size();
        }

        @Override public Object getGroup(int groupPosition) {
            return folders.get(groupPosition);
        }

        @Override public Object getChild(int groupPosition, int childPosition) {
            return songMap.get(folders.get(groupPosition)).get(childPosition).title;
        }

        @Override public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override public boolean hasStableIds() {
            return false;
        }

        @Override public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

        @Override
        public View getGroupView(int groupPosition,
                                 boolean isExpanded,
                                 View convertView,
                                 ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(ctx)
                        .inflate(android.R.layout.simple_expandable_list_item_1, parent, false);
            }
            TextView tv = convertView.findViewById(android.R.id.text1);
            tv.setText(folders.get(groupPosition));
            return convertView;
        }

        @Override
        public View getChildView(int groupPosition,
                                 int childPosition,
                                 boolean isLastChild,
                                 View convertView,
                                 ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(ctx)
                        .inflate(android.R.layout.simple_list_item_1, parent, false);
            }
            TextView tv = convertView.findViewById(android.R.id.text1);
            Song song = songMap.get(folders.get(groupPosition)).get(childPosition);
            tv.setText(song.title);
            return convertView;
        }
    }
}
