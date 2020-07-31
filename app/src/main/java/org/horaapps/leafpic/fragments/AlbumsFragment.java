package org.horaapps.leafpic.fragments;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.orhanobut.hawk.Hawk;
import com.squareup.haha.perflib.Main;

import org.horaapps.leafpic.R;
import org.horaapps.leafpic.activities.MainActivity;
import org.horaapps.leafpic.adapters.AlbumsAdapter;
import org.horaapps.leafpic.data.Album;
import org.horaapps.leafpic.data.AlbumsHelper;
import org.horaapps.leafpic.data.HandlingAlbums;
import org.horaapps.leafpic.data.MediaHelper;
import org.horaapps.leafpic.data.provider.CPHelper;
import org.horaapps.leafpic.data.sort.SortingMode;
import org.horaapps.leafpic.data.sort.SortingOrder;
import org.horaapps.leafpic.progress.ProgressBottomSheet;
import org.horaapps.leafpic.util.AlertDialogsHelper;
import org.horaapps.leafpic.util.AnimationUtils;
import org.horaapps.leafpic.util.DeviceUtils;
import org.horaapps.leafpic.util.Measure;
import org.horaapps.leafpic.util.Security;
import org.horaapps.leafpic.util.preferences.Prefs;
import org.horaapps.leafpic.views.GridSpacingItemDecoration;
import org.horaapps.liz.ThemeHelper;
import org.horaapps.liz.ThemedActivity;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import jp.wasabeef.recyclerview.animators.LandingAnimator;

/**
 * Created by dnld on 3/13/17.
 */

public class AlbumsFragment extends BaseMediaGridFragment {

    public static final String TAG = "AlbumsFragment";

    @BindView(R.id.albums) RecyclerView rv;
    @BindView(R.id.swipe_refresh) SwipeRefreshLayout refresh;
    @BindView(R.id.toolbar)
    Toolbar toolbar;

    private AlbumsAdapter adapter;
    private GridSpacingItemDecoration spacingDecoration;
    private AlbumClickListener listener;

    private boolean hidden = false;
    ArrayList<String> excuded = new ArrayList<>();

    public interface AlbumClickListener {
        void onAlbumClick(Album album);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        excuded = db().getExcludedFolders(getContext());
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof AlbumClickListener) listener = (AlbumClickListener) context;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!clearSelected())
            updateToolbar();
        setUpColumns();
    }

    public void displayAlbums(boolean hidden) {
        this.hidden = hidden;
        displayAlbums();
    }

    private void displayAlbums() {
        adapter.clear();
        SQLiteDatabase db = HandlingAlbums.getInstance(getContext().getApplicationContext()).getReadableDatabase();
        CPHelper.getAlbums(getContext(), hidden, excuded, sortingMode(), sortingOrder())
                .subscribeOn(Schedulers.io())
                .map(album -> album.withSettings(HandlingAlbums.getSettings(db, album.getPath())))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        album -> adapter.add(album),
                        throwable -> {
                            refresh.setRefreshing(false);
                            throwable.printStackTrace();
                        },
                        () -> {
                            db.close();
                            if (getNothingToShowListener() != null)
                                getNothingToShowListener().changedNothingToShow(getCount() == 0);
                            refresh.setRefreshing(false);

                            Hawk.put(hidden ? "h" : "albums", adapter.getAlbumsPaths());
                        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        toolbar.setTitle("");
        toolbar.setBackgroundColor(Color.BLACK);
        ((MainActivity) getActivity()).setSupportActionBar(toolbar);

        displayAlbums();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setUpColumns();
    }

    public void setUpColumns() {
        int columnsCount = columnsCount();

        if (columnsCount != ((GridLayoutManager) rv.getLayoutManager()).getSpanCount()) {
            rv.removeItemDecoration(spacingDecoration);
            spacingDecoration = new GridSpacingItemDecoration(columnsCount, Measure.pxToDp(3, getContext()), true);
            rv.addItemDecoration(spacingDecoration);
            rv.setLayoutManager(new GridLayoutManager(getContext(), columnsCount));
        }
    }

    public int columnsCount() {
        return 3;
    }

    @Override
    public int getTotalCount() {
        return adapter.getItemCount();
    }

    @Override
    public View.OnClickListener getToolbarButtonListener(boolean editMode) {
        if (editMode) return null;
        else return v -> adapter.clearSelected();
    }

    @Override
    public String getToolbarTitle() {
        return null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_albums, container, false);
        ButterKnife.bind(this, v);

        int spanCount = columnsCount();
        spacingDecoration = new GridSpacingItemDecoration(spanCount, Measure.pxToDp(3, getContext()), true);
        rv.setHasFixedSize(true);
        rv.addItemDecoration(spacingDecoration);
        rv.setLayoutManager(new GridLayoutManager(getContext(), spanCount));
        if(Prefs.animationsEnabled()) {
            rv.setItemAnimator(
                    AnimationUtils.getItemAnimator(
                            new LandingAnimator(new OvershootInterpolator(1f))
                    ));
        }

        adapter = new AlbumsAdapter(getContext(), this);

        refresh.setOnRefreshListener(this::displayAlbums);
        rv.setAdapter(adapter);
        return v;
    }

    public SortingMode sortingMode() {
        return adapter.sortingMode();
    }

    public SortingOrder sortingOrder() {
        return adapter.sortingOrder();
    }

    private HandlingAlbums db() {
        return HandlingAlbums.getInstance(getContext().getApplicationContext());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main_toolbar, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        Album selectedAlbum = adapter.getFirstSelectedAlbum();
        switch (item.getItemId()) {

        }

        return super.onOptionsItemSelected(item);
    }

    private void showDeleteBottomSheet() {
        List<Album> selected = adapter.getSelectedAlbums();
        ArrayList<io.reactivex.Observable<Album>> sources = new ArrayList<>(selected.size());
        for (Album media : selected)
            sources.add(MediaHelper.deleteAlbum(getContext().getApplicationContext(), media));

        ProgressBottomSheet<Album> bottomSheet = new ProgressBottomSheet.Builder<Album>(R.string.delete_bottom_sheet_title)
                .autoDismiss(false)
                .sources(sources)
                .listener(new ProgressBottomSheet.Listener<Album>() {
                    @Override
                    public void onCompleted() {
                        adapter.invalidateSelectedCount();
                    }

                    @Override
                    public void onProgress(Album item) {
                        adapter.removeAlbum(item);
                    }
                })
                .build();

        bottomSheet.showNow(getChildFragmentManager(), null);
    }

    public int getCount() {
        return adapter.getItemCount();
    }

    public int getSelectedCount() {
        return adapter.getSelectedCount();
    }

    @Override
    public boolean editMode() {
        return adapter.selecting();
    }

    @Override
    public boolean clearSelected() {
        return adapter.clearSelected();
    }

    @Override
    public void onItemSelected(int position) {
        if (listener != null) listener.onAlbumClick(adapter.get(position));
    }

    @Override
    public void onSelectMode(boolean selectMode) {
        refresh.setEnabled(!selectMode);
        updateToolbar();
        getActivity().invalidateOptionsMenu();
    }


    @Override
    public void refreshTheme(ThemeHelper t) {
        rv.setBackgroundColor(Color.BLACK);
        adapter.refreshTheme(t);

         refresh.setColorSchemeColors(t.getAccentColor());
        refresh.setProgressBackgroundColorSchemeColor(t.getBackgroundColor());
    }
}
