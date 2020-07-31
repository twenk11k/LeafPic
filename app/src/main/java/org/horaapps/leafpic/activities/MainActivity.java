package org.horaapps.leafpic.activities;

import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.typeface.IIcon;
import com.orhanobut.hawk.Hawk;

import org.horaapps.leafpic.BuildConfig;
import org.horaapps.leafpic.R;
import org.horaapps.leafpic.about.AboutActivity;
import org.horaapps.leafpic.activities.base.SharedMediaActivity;
import org.horaapps.leafpic.data.Album;
import org.horaapps.leafpic.data.Media;
import org.horaapps.leafpic.fragments.AlbumsFragment;
import org.horaapps.leafpic.fragments.EditModeListener;
import org.horaapps.leafpic.fragments.NothingToShowListener;
import org.horaapps.leafpic.fragments.RvMediaFragment;
import org.horaapps.leafpic.interfaces.MediaClickListener;
import org.horaapps.leafpic.timeline.TimelineFragment;
import org.horaapps.leafpic.util.AlertDialogsHelper;
import org.horaapps.leafpic.util.DeviceUtils;
import org.horaapps.leafpic.util.LegacyCompatFileProvider;
import org.horaapps.leafpic.util.Security;
import org.horaapps.leafpic.util.StringUtils;
import org.horaapps.leafpic.util.preferences.Prefs;
import org.horaapps.leafpic.views.navigation_drawer.NavigationDrawer;
import org.horaapps.liz.ColorPalette;

import java.util.ArrayList;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

import static org.horaapps.leafpic.views.navigation_drawer.NavigationDrawer.ItemListener;
import static org.horaapps.leafpic.views.navigation_drawer.NavigationDrawer.NAVIGATION_ITEM_ABOUT;
import static org.horaapps.leafpic.views.navigation_drawer.NavigationDrawer.NAVIGATION_ITEM_ALL_ALBUMS;
import static org.horaapps.leafpic.views.navigation_drawer.NavigationDrawer.NAVIGATION_ITEM_ALL_MEDIA;
import static org.horaapps.leafpic.views.navigation_drawer.NavigationDrawer.NAVIGATION_ITEM_DONATE;
import static org.horaapps.leafpic.views.navigation_drawer.NavigationDrawer.NAVIGATION_ITEM_HIDDEN_FOLDERS;
import static org.horaapps.leafpic.views.navigation_drawer.NavigationDrawer.NAVIGATION_ITEM_SETTINGS;
import static org.horaapps.leafpic.views.navigation_drawer.NavigationDrawer.NAVIGATION_ITEM_TIMELINE;
import static org.horaapps.leafpic.views.navigation_drawer.NavigationDrawer.NAVIGATION_ITEM_WALLPAPERS;
import static org.horaapps.leafpic.views.navigation_drawer.NavigationDrawer.NavigationItem;

/**
 * The Main Activity used to display Albums / Media.
 */
public class MainActivity extends SharedMediaActivity implements
        MediaClickListener, AlbumsFragment.AlbumClickListener,
        NothingToShowListener {

    public static final String ARGS_PICK_MODE = "pick_mode";

    private static final String SAVE_FRAGMENT_MODE = "fragment_mode";

    public @interface FragmentMode {
        int MODE_ALBUMS = 1001;
        int MODE_MEDIA = 1002;
        int MODE_TIMELINE = 1003;
    }

    private AlbumsFragment albumsFragment;
    private RvMediaFragment rvMediaFragment;
    private TimelineFragment timelineFragment;

    private boolean pickMode = false;
    private Unbinder unbinder;

    @FragmentMode private int fragmentMode;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        unbinder = ButterKnife.bind(this);

        pickMode = getIntent().getBooleanExtra(ARGS_PICK_MODE, false);

        if (savedInstanceState == null) {
            fragmentMode = FragmentMode.MODE_ALBUMS;
            initAlbumsFragment();
            setContentFragment();

            return;
        }

        /* We have some instance state */
        restoreState(savedInstanceState);

        switch (fragmentMode) {

            case FragmentMode.MODE_MEDIA:
                rvMediaFragment = (RvMediaFragment) getSupportFragmentManager().findFragmentByTag(RvMediaFragment.TAG);
                rvMediaFragment.setListener(this);
                break;

            case FragmentMode.MODE_ALBUMS:
                albumsFragment = (AlbumsFragment) getSupportFragmentManager().findFragmentByTag(AlbumsFragment.TAG);
                break;

        }
    }

    private void setContentFragment() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.content, albumsFragment, AlbumsFragment.TAG)
                .addToBackStack(null)
                .commit();
    }

    private void initAlbumsFragment() {
        unreferenceFragments();
        albumsFragment = new AlbumsFragment();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(SAVE_FRAGMENT_MODE, fragmentMode);
        super.onSaveInstanceState(outState);
    }

    private void restoreState(@NonNull Bundle savedInstance) {
        fragmentMode = savedInstance.getInt(SAVE_FRAGMENT_MODE, FragmentMode.MODE_ALBUMS);
    }

    private void displayAlbums(boolean hidden) {
        fragmentMode = FragmentMode.MODE_ALBUMS;
        if (albumsFragment == null) initAlbumsFragment();
        albumsFragment.displayAlbums(hidden);
        setContentFragment();
    }

    public void displayMedia(Album album) {
        unreferenceFragments();
        rvMediaFragment = RvMediaFragment.make(album);

        fragmentMode = FragmentMode.MODE_MEDIA;

        rvMediaFragment.setListener(this);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.content, rvMediaFragment, RvMediaFragment.TAG)
                .addToBackStack(null)
                .commit();
    }

    public void displayTimeline(Album album) {
        unreferenceFragments();
        timelineFragment = TimelineFragment.Companion.newInstance(album);

        fragmentMode = FragmentMode.MODE_TIMELINE;

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.content, timelineFragment, TimelineFragment.TAG)
                .addToBackStack(null)
                .commit();

    }

    @Override
    protected void onDestroy() {
        unbinder.unbind();
        super.onDestroy();
    }

    @Override
    public void onMediaClick(Album album, ArrayList<Media> media, int position) {

        if (!pickMode) {
            Intent intent = new Intent(getApplicationContext(), SingleMediaActivity.class);
            intent.putExtra(SingleMediaActivity.EXTRA_ARGS_ALBUM, album);
            try {
                intent.setAction(SingleMediaActivity.ACTION_OPEN_ALBUM);
                intent.putExtra(SingleMediaActivity.EXTRA_ARGS_MEDIA, media);
                intent.putExtra(SingleMediaActivity.EXTRA_ARGS_POSITION, position);
                startActivity(intent);
            } catch (Exception e) { // Putting too much data into the Bundle
                // TODO: Find a better way to pass data between the activities - possibly a key to
                // access a HashMap or a unique value of a singleton Data Repository of some sort.
                intent.setAction(SingleMediaActivity.ACTION_OPEN_ALBUM_LAZY);
                intent.putExtra(SingleMediaActivity.EXTRA_ARGS_MEDIA, media.get(position));
                startActivity(intent);
            }

        } else {

            Media m = media.get(position);
            Uri uri = LegacyCompatFileProvider.getUri(getApplicationContext(), m.getFile());
            Intent res = new Intent();
            res.setData(uri);
            res.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            setResult(RESULT_OK, res);
            finish();
        }
    }

    @Override
    public void changedNothingToShow(boolean nothingToShow) {
        enableNothingToSHowPlaceHolder(nothingToShow);
    }


    public void goBackToAlbums() {
        unreferenceFragments();
        fragmentMode = FragmentMode.MODE_ALBUMS;
        getSupportFragmentManager().popBackStack();
        albumsFragment = (AlbumsFragment) getSupportFragmentManager().findFragmentByTag(AlbumsFragment.TAG);
    }

    private void unreferenceFragments() {
        // TODO Calvin: This is a hack for the current back button behavior.
        // Refactor the logic to avoid these member variables.
        // Allow the GC to reclaim the fragments for now
        timelineFragment = null;
        rvMediaFragment = null;
        albumsFragment = null;
    }

    @CallSuper
    @Override
    public void updateUiElements() {
        super.updateUiElements();
        //TODO: MUST BE FIXED

        /**** SWIPE TO REFRESH ****/

        setStatusBarColor();
        setNavBarColor();

//        setScrollViewColor(navigationDrawerView);
        setAllScrollbarsColor();

        // TODO Calvin: This performs a NO-OP. Find out what this is used for
        setRecentApp(getString(R.string.app_name));
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    protected void setStatusBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (isTranslucentStatusBar())
                getWindow().setStatusBarColor(ColorPalette.getObscuredColor(Color.BLACK));
            else
                getWindow().setStatusBarColor(Color.BLACK);
        }
    }

    private void setAllScrollbarsColor() {
        Drawable drawableScrollBar = ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_scrollbar);
        drawableScrollBar.setColorFilter(new PorterDuffColorFilter(getPrimaryColor(), PorterDuff.Mode.SRC_ATOP));
    }

    private void doNothing() {
    }

    public void enableNothingToSHowPlaceHolder(boolean status) {
        findViewById(R.id.nothing_to_show_placeholder).setVisibility(status ? View.VISIBLE : View.GONE);
    }

    @Deprecated
    public void checkNothing(boolean status) {
        //TODO: @jibo come vuo fare qua? o anzi sopra!
        ((TextView) findViewById(R.id.emoji_easter_egg)).setTextColor(getSubTextColor());
        ((TextView) findViewById(R.id.nothing_to_show_text_emoji_easter_egg)).setTextColor(getSubTextColor());

        if (status && Prefs.showEasterEgg()) {
            findViewById(R.id.ll_emoji_easter_egg).setVisibility(View.VISIBLE);
            findViewById(R.id.nothing_to_show_placeholder).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.ll_emoji_easter_egg).setVisibility(View.GONE);
            findViewById(R.id.nothing_to_show_placeholder).setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {


        switch (item.getItemId()) {

            case R.id.settings:
                SettingsActivity.startActivity(this);
                return true;

            /*
            case R.id.action_move:
                SelectAlbumBuilder.with(getSupportFragmentManager())
                        .title(getString(R.string.move_to))
                        .onFolderSelected(new SelectAlbumBuilder.OnFolderSelected() {
                            @Override
                            public void folderSelected(String path) {
                                //TODo
                                //swipeRefreshLayout.setRefreshing(true);
                                /*if (getAlbum().moveSelectedMedia(getApplicationContext(), path) > 0) {
                                    if (getAlbum().getMedia().size() == 0) {
                                        //getAlbums().removeCurrentAlbum();
                                        //albumsAdapter.notifyDataSetChanged();
                                        displayAlbums(false);
                                    }
                                    //oldMediaAdapter.swapDataSet(getAlbum().getMedia());
                                    //finishEditMode();
                                    supportInvalidateOptionsMenu();
                                } else requestSdCardPermissions();

                                //swipeRefreshLayout.setRefreshing(false);
                            }
                        }).show();
                return true;
                */

            /*
            case R.id.action_copy:
                SelectAlbumBuilder.with(getSupportFragmentManager())
                        .title(getString(R.string.copy_to))
                        .onFolderSelected(new SelectAlbumBuilder.OnFolderSelected() {
                            @Override
                            public void folderSelected(String path) {
                                boolean success = getAlbum().copySelectedPhotos(getApplicationContext(), path);
                                //finishEditMode();

                                if (!success) // TODO: 11/21/16 handle in other way
                                    requestSdCardPermissions();
                            }
                        }).show();

                return true;

                */


            /*case R.id.rename:
                final EditText editTextNewName = new EditText(getApplicationContext());
                editTextNewName.setText(albumsMode ? firstSelectedAlbum.getName() : getAlbum().getName());

                final AlertDialog insertTextDialog = AlertDialogsHelper.getInsertTextDialog(MainActivity.this, editTextNewName, R.string.rename_album);

                insertTextDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.cancel).toUpperCase(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onMediaClick(DialogInterface dialogInterface, int i) {
                        insertTextDialog.dismiss();
                    }
                });

                insertTextDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.ok_action).toUpperCase(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onMediaClick(DialogInterface dialogInterface, int i) {
                        if (editTextNewName.length() != 0) {
                            swipeRefreshLayout.setRefreshing(true);
                            boolean success;
                            if (albumsMode) {

                                int index = getAlbums().albums.indexOf(firstSelectedAlbum);
                                getAlbums().getAlbum(index).updatePhotos(getApplicationContext());
                                success = getAlbums().getAlbum(index).renameAlbum(getApplicationContext(),
                                        editTextNewName.getText().toString());
                                albumsAdapter.notifyItemChanged(index);
                            } else {
                                success = getAlbum().renameAlbum(getApplicationContext(), editTextNewName.getText().toString());
                                toolbar.setTitle(getAlbum().getName());
                                oldMediaAdapter.notifyDataSetChanged();
                            }
                            insertTextDialog.dismiss();
                            if (!success) requestSdCardPermissions();
                            swipeRefreshLayout.setRefreshing(false);
                        } else {
                            StringUtils.showToast(getApplicationContext(), getString(R.string.insert_something));
                            editTextNewName.requestFocus();
                        }
                    }
                });

                insertTextDialog.show();
                return true;*/


            default:
                /** If we got here, the user's action was not recognized.
                 *  Invoke the superclass to handle it. */
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        if (inAlbumMode()) {
            if (!albumsFragment.onBackPressed()) {
                finish();
            }

        } else if (inTimelineMode() && !timelineFragment.onBackPressed()) {
            goBackToAlbums();

        } else if (inMediaMode() && !rvMediaFragment.onBackPressed()) {
            goBackToAlbums();
        }
    }

    @Override
    public void onAlbumClick(Album album) {
        displayMedia(album);
    }

    private boolean inAlbumMode() {
        return fragmentMode == FragmentMode.MODE_ALBUMS;
    }

    private boolean inMediaMode() {
        return fragmentMode == FragmentMode.MODE_MEDIA;
    }

    private boolean inTimelineMode() {
        return fragmentMode == FragmentMode.MODE_TIMELINE;
    }

}
