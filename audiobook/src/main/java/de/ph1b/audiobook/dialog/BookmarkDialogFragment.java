package de.ph1b.audiobook.dialog;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.ph1b.audiobook.R;
import de.ph1b.audiobook.adapter.BookmarkAdapter;
import de.ph1b.audiobook.model.Book;
import de.ph1b.audiobook.model.Bookmark;
import de.ph1b.audiobook.persistence.BookShelf;
import de.ph1b.audiobook.persistence.PrefsManager;
import de.ph1b.audiobook.service.ServiceController;
import de.ph1b.audiobook.uitools.DividerItemDecoration;
import de.ph1b.audiobook.utils.App;
import de.ph1b.audiobook.utils.BookVendor;
import timber.log.Timber;

/**
 * Dialog for creating a bookmark
 *
 * @author Paul Woitaschek
 */
public class BookmarkDialogFragment extends DialogFragment {

    public static final String TAG = BookmarkDialogFragment.class.getSimpleName();
    private static final String BOOK_ID = "bookId";
    @Bind(R.id.add) ImageView addButton;
    @Bind(R.id.edit1) EditText bookmarkTitle;
    @Inject PrefsManager prefs;
    @Inject BookShelf db;
    @Inject BookVendor bookVendor;
    private BookmarkAdapter adapter;
    private ServiceController controller;
    private Book book;

    public static BookmarkDialogFragment newInstance(long bookId) {
        BookmarkDialogFragment bookmarkDialogFragment = new BookmarkDialogFragment();
        Bundle args = new Bundle();
        args.putLong(BookmarkDialogFragment.BOOK_ID, bookId);
        bookmarkDialogFragment.setArguments(args);
        return bookmarkDialogFragment;
    }

    public static void addBookmark(long bookId, @NonNull String title, @NonNull BookShelf db) {
        Book book = db.getActiveBooks()
                .singleOrDefault(null, filterBook -> filterBook.getId() == bookId)
                .toBlocking()
                .single();
        if (book != null) {
            Bookmark addedBookmark = new Bookmark(book.currentChapter().getFile(), title, book.getTime());
            List<Bookmark> newBookmarks = new ArrayList<>(book.getBookmarks());
            newBookmarks.add(addedBookmark);
            book = book.copy(
                    book.component1(),
                    Ordering.natural().immutableSortedCopy(newBookmarks),
                    book.component3(),
                    book.component4(),
                    book.component5(),
                    book.component6(),
                    book.component7(),
                    book.component8(),
                    book.component9(),
                    book.component10(),
                    book.component11());
            db.updateBook(book);
            Timber.v("Added bookmark=%s", addedBookmark);
        } else {
            Timber.e("Book does not exist");
        }
    }

    @OnClick(R.id.add)
    void addClicked() {
        String title = bookmarkTitle.getText().toString();
        if (title.isEmpty()) {
            title = book.currentChapter().getName();
        }

        addBookmark(book.getId(), title, db);
        Toast.makeText(getActivity(), R.string.bookmark_added, Toast.LENGTH_SHORT).show();
        bookmarkTitle.setText("");
        dismiss();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.getComponent().inject(this);

        controller = new ServiceController(getActivity());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        LayoutInflater inflater = getActivity().getLayoutInflater();
        @SuppressLint("InflateParams") View v = inflater.inflate(R.layout.dialog_bookmark, null);
        ButterKnife.bind(this, v);


        final long bookId = getArguments().getLong(BOOK_ID);
        book = bookVendor.byId(bookId);
        if (book == null) {
            throw new AssertionError("Cannot instantiate " + TAG + " without a book");
        }

        BookmarkAdapter.OnOptionsMenuClickedListener listener = new BookmarkAdapter.OnOptionsMenuClickedListener() {
            @Override
            public void onOptionsMenuClicked(final Bookmark clickedBookmark, View v) {
                PopupMenu popup = new PopupMenu(getActivity(), v);
                MenuInflater inflater = popup.getMenuInflater();
                inflater.inflate(R.menu.bookmark_popup, popup.getMenu());
                popup.setOnMenuItemClickListener(item -> {
                    MaterialDialog.Builder builder = new MaterialDialog.Builder(getActivity());
                    switch (item.getItemId()) {
                        case R.id.edit:
                            new MaterialDialog.Builder(getActivity())
                                    .title(R.string.bookmark_edit_title)
                                    .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
                                    .input(getString(R.string.bookmark_edit_hint), clickedBookmark.getTitle(), false, (materialDialog, charSequence) -> {
                                        Bookmark newBookmark = new Bookmark(clickedBookmark.getMediaFile(), charSequence.toString(), clickedBookmark.getTime());
                                        adapter.bookmarkUpdated(clickedBookmark, newBookmark);

                                        // replaces the bookmark in the book
                                        List<Bookmark> mutableBookmarks = new ArrayList<>(book.getBookmarks());
                                        mutableBookmarks.set(mutableBookmarks.indexOf(clickedBookmark), newBookmark);
                                        book = book.copy(
                                                book.component1(),
                                                ImmutableList.copyOf(mutableBookmarks),
                                                book.component3(),
                                                book.component4(),
                                                book.component5(),
                                                book.component6(),
                                                book.component7(),
                                                book.component8(),
                                                book.component9(),
                                                book.component10(),
                                                book.component11());
                                        db.updateBook(book);
                                    })
                                    .positiveText(R.string.dialog_confirm)
                                    .show();
                            return true;
                        case R.id.delete:
                            builder.title(R.string.bookmark_delete_title)
                                    .content(clickedBookmark.getTitle())
                                    .positiveText(R.string.remove)
                                    .negativeText(R.string.dialog_cancel)
                                    .onPositive((materialDialog, dialogAction) -> {
                                        List<Bookmark> mutableBookmarks = new ArrayList<>(book.getBookmarks());
                                        mutableBookmarks.remove(clickedBookmark);
                                        book = book.copy(
                                                book.component1(),
                                                ImmutableList.copyOf(mutableBookmarks),
                                                book.component3(),
                                                book.component4(),
                                                book.component5(),
                                                book.component6(),
                                                book.component7(),
                                                book.component8(),
                                                book.component9(),
                                                book.component10(),
                                                book.component11());
                                        adapter.removeItem(clickedBookmark);
                                        db.updateBook(book);
                                    })
                                    .show();
                            return true;
                        default:
                            return false;
                    }
                });
                popup.show();
            }

            @Override
            public void onBookmarkClicked(Bookmark bookmark) {
                prefs.setCurrentBookId(bookId);
                controller.changeTime(bookmark.getTime(), bookmark.getMediaFile());

                getDialog().cancel();
            }
        };

        final RecyclerView recyclerView = ButterKnife.findById(v, R.id.recycler);
        adapter = new BookmarkAdapter(book.getBookmarks(), book.getChapters(), listener);
        recyclerView.setAdapter(adapter);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity()));
        recyclerView.setLayoutManager(layoutManager);

        bookmarkTitle.setOnEditorActionListener((v1, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addButton.performClick(); //same as clicking on the +
                return true;
            }
            return false;
        });

        return new MaterialDialog.Builder(getActivity())
                .customView(v, false)
                .title(R.string.bookmark)
                .negativeText(R.string.dialog_cancel)
                .build();
    }
}