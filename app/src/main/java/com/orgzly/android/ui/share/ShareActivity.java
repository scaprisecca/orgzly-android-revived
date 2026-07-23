package com.orgzly.android.ui.share;

import androidx.appcompat.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import android.util.Log;

import androidx.core.app.TaskStackBuilder;
import androidx.core.content.pm.ShortcutManagerCompat;

import com.orgzly.BuildConfig;
import com.orgzly.R;
import com.orgzly.android.App;
import com.orgzly.android.AppIntent;
import com.orgzly.android.SharingShortcutsManager;
import com.orgzly.android.capture.CaptureInput;
import com.orgzly.android.capture.CaptureTargetResolution;
import com.orgzly.android.capture.CaptureTemplates;
import com.orgzly.android.data.DataRepository;
import com.orgzly.android.db.entity.Book;
import com.orgzly.android.db.entity.CaptureTemplateEntity;
import com.orgzly.android.db.entity.Note;
import com.orgzly.android.db.entity.SavedSearch;
import com.orgzly.android.prefs.AppPreferences;
import com.orgzly.android.query.Query;
import com.orgzly.android.query.QueryUtils;
import com.orgzly.android.query.user.DottedQueryParser;
import com.orgzly.android.sync.AutoSync;
import com.orgzly.android.ui.AppSnackbarUtils;
import com.orgzly.android.ui.CommonActivity;
import com.orgzly.android.ui.NotePlace;
import com.orgzly.android.ui.sync.SyncFragment;
import com.orgzly.android.ui.note.NoteFragment;
import com.orgzly.android.ui.note.NotePayload;
import com.orgzly.android.ui.util.ActivityUtils;
import com.orgzly.android.usecase.UseCase;
import com.orgzly.android.usecase.UseCaseResult;
import com.orgzly.android.util.LogUtils;
import com.orgzly.android.util.MiscUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.inject.Inject;

/**
 * Activity started when shared to Orgzly.
 *
 * TODO: Resuming - intent will stay the same.
 * If activity is not finished (by save, cancel or pressing back), next share will resume the
 * activity and the intent will stay the same. Other apps seem to have the same problem and
 * it's not a common scenario, but it should be fixed.
 */
public class ShareActivity extends CommonActivity
        implements
        NoteFragment.Listener,
        SyncFragment.Listener {

    public static final String TAG = ShareActivity.class.getName();

    /** Shared text files are read and their content is stored as note content. */
    private static final long MAX_TEXT_FILE_LENGTH_FOR_CONTENT = 1024 * 1024 * 2; // 2 MB

    private SyncFragment mSyncFragment;

    private String mError;

    private AlertDialog dialog;

    @Inject
    DataRepository dataRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        App.appComponent.inject(this);

        super.onCreate(savedInstanceState);

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, savedInstanceState);

        setContentView(R.layout.activity_share);

        Data data = getDataFromIntent(getIntent());

        setupFragments(savedInstanceState, data);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }

    private Data getTextDataFromIntent(Intent intent) {
        Data data = new Data();
        if (intent.hasExtra(Intent.EXTRA_TEXT)) {
            if (intent.hasExtra(Intent.EXTRA_SUBJECT)) {
                // Both "text" and "subject" received. Subject goes in heading, text goes in
                // body.
                String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
                if (subject != null && !subject.isEmpty()) {
                    data.title = subject;
                }
                data.content = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (AppPreferences.createOrgLinksFromSharedLinks(this) &&
                        !data.title.contains("\n")) {
                    try {
                        new URI(data.content);
                        data.title = "[[" + data.content + "][" + data.title + "]]";
                        data.content = null;
                    } catch (URISyntaxException ignored) {}
                }
            } else {
                // A single text string was shared. Put it in heading or body, depending on the
                // user setting.
                if (AppPreferences.sharedTextPlacement(App.getAppContext()).equals("in_note_heading")) {
                    data.title = intent.getStringExtra(Intent.EXTRA_TEXT);
                } else {
                    data.content = intent.getStringExtra(Intent.EXTRA_TEXT);
                }
            }
        } else if (intent.hasExtra(Intent.EXTRA_STREAM)) {
            // A text file was shared
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);

            data.title = uri.getLastPathSegment();

            /*
             * Store file's content as note content.
             */
            try {
                File file = new File(uri.getPath());

                /* Don't read large files. */
                if (file.length() > MAX_TEXT_FILE_LENGTH_FOR_CONTENT) {
                    mError = "File has " + file.length() +
                            " bytes (refusing to read files larger then " +
                            MAX_TEXT_FILE_LENGTH_FOR_CONTENT + " bytes)";

                } else {
                    data.content = MiscUtils.readStringFromFile(file);
                }

            } catch (IOException e) {
                e.printStackTrace();
                mError = "Failed reading the content of " + uri.toString() + ": " + e.toString();
            }
        }
        applyRoutingExtras(intent, data);
        return data;
    }

    private Data getDataFromIntent(Intent intent) {
        Data data = new Data();
        mError = null;

        String action = intent.getAction();
        String type = intent.getType();

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, intent);

        if (action != null && type != null) {
            switch (action) {
                case Intent.ACTION_SEND:
                    if (type.startsWith("text/")) {
                        data = getTextDataFromIntent(intent);
                    } else if (type.startsWith("image/")) {
                        handleSendImage(intent, data); // Handle single image being sent
                    } else {
                        mError = getString(R.string.share_type_not_supported, type);
                    }
                    break;
                case "com.google.android.gm.action.AUTO_SEND":
                    if (type.startsWith("text/") && intent.hasExtra(Intent.EXTRA_TEXT)) {
                        data.title = intent.getStringExtra(Intent.EXTRA_TEXT);
                    }
                    break;
                default:
                    mError = getString(R.string.share_action_not_supported, action);
            }
        }

        applyRoutingExtras(intent, data);

        /* Make sure that title is never empty. */
        if (data.title == null) data.title = "";

        return data;
    }

    private void setupFragments(Bundle savedInstanceState, Data data) {
        if (savedInstanceState == null) { /* Create and add fragments. */

            mSyncFragment = SyncFragment.getInstance();

            getSupportFragmentManager()
                    .beginTransaction()
                    .add(mSyncFragment, SyncFragment.FRAGMENT_TAG)
                    .commit();

            openEditorForShareData(data);
        } else { /* Get existing fragments. */
            mSyncFragment = (SyncFragment) getSupportFragmentManager().findFragmentByTag(SyncFragment.FRAGMENT_TAG);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        autoSync.trigger(AutoSync.Type.APP_RESUMED);

        if (mError != null) {
            AppSnackbarUtils.showSnackbar(this, mError);
            mError = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        autoSync.trigger(AutoSync.Type.APP_SUSPENDED);
    }

    public static PendingIntent createNewNotePendingIntent(Context context, String category, SavedSearch savedSearch) {
        Intent resultIntent = createNewNoteIntent(context);

        // For distinguishing pending events
        resultIntent.addCategory(category);

        if (savedSearch != null) {
            resultIntent.putExtra(AppIntent.EXTRA_QUERY_STRING, savedSearch.getQuery());
        }

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, resultIntent);

        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your application to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(ShareActivity.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);

        return stackBuilder.getPendingIntent(
                0, ActivityUtils.immutable(PendingIntent.FLAG_UPDATE_CURRENT));
    }

    public static Intent createNewNoteIntent(Context context) {
        Intent intent = new Intent(context, ShareActivity.class);
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, "");
        return intent;
    }

    @Override
    public void onNoteCreated(Note note) {
        finish();
    }

    @Override
    public void onNoteUpdated(Note note) {
    }

    @Override
    public void onNoteCanceled() {
        finish();
    }

    /**
     * User action succeeded.
     */
    @Override
    public void onSuccess(UseCase action, UseCaseResult result) {
    }

    /**
     * User action failed.
     */
    @Override
    public void onError(UseCase action, Throwable throwable) {
        AppSnackbarUtils.showSnackbar(this, throwable.getLocalizedMessage());
    }

    private class Data {
        String title;
        String content;
        Long bookId = null;
        CaptureTemplateEntity template = null;
    }

    private static class ImportedImage {
        final String relativePath;

        ImportedImage(String relativePath) {
            this.relativePath = relativePath;
        }
    }

    /**
     * Copy the shared image into persistent storage under the configured relative file root
     * and link to that copied file in the note content.
     */
    private void handleSendImage(Intent intent, Data data) {
        Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);

        if (uri == null) {
            data.title = "";
            data.content = "Cannot find image using this URI.";
            return;
        }

        String displayName = null;

        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                if (BuildConfig.LOG_DEBUG)
                    LogUtils.d(TAG, DatabaseUtils.dumpCursorToString(cursor));

                int displayNameColumnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (displayNameColumnIndex == -1) {
                    displayNameColumnIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                }

                if (displayNameColumnIndex != -1) {
                    displayName = cursor.getString(displayNameColumnIndex);
                }
            }
        }

        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = uri.getLastPathSegment();
        }

        String fileName = buildSharedImageFileName(displayName, uri);
        data.title = buildSharedImageTitle(fileName);

        try {
            ImportedImage importedImage = copySharedImageToRelativeStorage(uri, fileName);
            data.content = "file:" + importedImage.relativePath;
        } catch (IOException e) {
            Log.e(TAG, "Failed to import shared image from " + uri, e);
            data.content = uri.toString() + "\n\nFailed to import shared image: " + e.getMessage();
        }
    }

    private ImportedImage copySharedImageToRelativeStorage(Uri uri, String displayName) throws IOException {
        File baseDir = new File(AppPreferences.sharedImagesBaseDirectory(this));
        if (!baseDir.isDirectory() && !baseDir.mkdirs()) {
            throw new IOException("Failed creating shared image base directory " + baseDir);
        }

        File canonicalBaseDir = baseDir.getCanonicalFile();
        String relativeDirectory = AppPreferences.sharedImagesRelativeDirectory(this);
        File dir = new File(canonicalBaseDir, relativeDirectory).getCanonicalFile();
        String canonicalBasePath = canonicalBaseDir.getPath();
        String targetDirPath = dir.getPath();

        if (!targetDirPath.equals(canonicalBasePath)
                && !targetDirPath.startsWith(canonicalBasePath + File.separator)) {
            throw new IOException("Shared image folder must stay under Orgzly app storage");
        }

        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Failed creating shared image directory " + dir);
        }

        String safeName = sanitizeFileName(displayName);
        if (!safeName.contains(".")) {
            String extension = getExtensionForSharedImage(uri);
            if (!extension.isEmpty()) {
                safeName = safeName + "." + extension;
            }
        }

        File target = uniqueFile(dir, safeName);

        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("Unable to open shared image stream");
            }

            try (OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
                out.flush();
            }
        }

        return new ImportedImage(target.getCanonicalPath());
    }

    private String getExtensionForSharedImage(Uri uri) {
        String mimeType = getContentResolver().getType(uri);
        if (mimeType == null) {
            return "";
        }

        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        return extension != null ? extension : "";
    }

    private String buildSharedImageFileName(String displayName, Uri uri) {
        String safeName = sanitizeFileName(displayName);
        String extension = filenameExtension(safeName);

        if (extension.isEmpty()) {
            extension = getExtensionForSharedImage(uri);
        }

        String baseName = filenameBaseName(safeName);
        if (baseName.isEmpty() || looksLikeGeneratedImageName(baseName)) {
            baseName = "shared_image_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date());
        }

        return extension.isEmpty() ? baseName : baseName + "." + extension;
    }

    private String buildSharedImageTitle(String fileName) {
        String title = filenameBaseName(fileName)
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();

        title = title.replaceAll("\\s+", " ");

        return title.isEmpty() ? "Shared image" : title;
    }

    private String sanitizeFileName(String displayName) {
        String sanitized = displayName == null ? "" : displayName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return sanitized.isEmpty() ? "shared_image" : sanitized;
    }

    private String filenameBaseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            return fileName.substring(0, dot);
        }

        return fileName;
    }

    private String filenameExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot > 0 && dot < fileName.length() - 1) {
            return fileName.substring(dot + 1);
        }

        return "";
    }

    private boolean looksLikeGeneratedImageName(String name) {
        return name.matches("\\d+")
                || name.matches("(?i)shared[_ -]?image")
                || name.matches("(?i)image")
                || name.matches("(?i)img[_ -]?\\d+");
    }

    private File uniqueFile(File dir, String fileName) {
        File candidate = new File(dir, fileName);
        if (!candidate.exists()) {
            return candidate;
        }

        String name = fileName;
        String extension = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            name = fileName.substring(0, dot);
            extension = fileName.substring(dot);
        }

        int index = 1;
        while (candidate.exists()) {
            candidate = new File(dir, name + "_" + index + extension);
            index++;
        }

        return candidate;
    }

    private void applyRoutingExtras(Intent intent, Data data) {
        // TODO: Was used for direct share shortcuts to pass the book name. Used someplace else?
        if (intent.hasExtra(AppIntent.EXTRA_QUERY_STRING)) {
            Query query = new DottedQueryParser().parse(intent.getStringExtra(AppIntent.EXTRA_QUERY_STRING));
            String bookName = QueryUtils.extractFirstBookNameFromQuery(query.getCondition());

            if (bookName != null) {
                Book book = dataRepository.getBook(bookName);
                if (book != null) {
                    data.bookId = book.getId();
                    if (BuildConfig.LOG_DEBUG)
                        LogUtils.d(TAG, "Using book " + data.bookId
                                + " from passed query " + query + " (" + bookName + ")");
                }
            }
        }
        if (intent.hasExtra(AppIntent.EXTRA_BOOK_ID)) {
            data.bookId = intent.getLongExtra(AppIntent.EXTRA_BOOK_ID, 0L);
            if (BuildConfig.LOG_DEBUG)
                LogUtils.d(TAG, "Using book " + data.bookId
                        + " from passed book ID");
        }
        // Coming from Direct Share shortcut
        if (intent.hasExtra(Intent.EXTRA_SHORTCUT_ID)) {
            String shortcutId = intent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID);
            data.bookId = SharingShortcutsManager.bookIdFromShortcutId(shortcutId);
            if (BuildConfig.LOG_DEBUG)
                LogUtils.d(TAG, "Using book " + data.bookId
                        + " from passed shortcut ID");
        }
        data.template = CaptureTemplates.fromId(
                dataRepository,
                intent.getStringExtra(AppIntent.EXTRA_CAPTURE_TEMPLATE_ID));
    }

    private void openEditorForShareData(Data data) {
        if (data.template != null) {
            showNoteEditor(data, data.template);
            return;
        }

        java.util.List<CaptureTemplateEntity> shareTemplates = CaptureTemplates.shareEnabledTemplates(dataRepository);

        if (shareTemplates.isEmpty()) {
            showNoteEditor(data, null);
        } else if (shareTemplates.size() == 1) {
            showNoteEditor(data, shareTemplates.get(0));
        } else {
            String[] items = new String[shareTemplates.size() + 1];
            items[0] = getString(R.string.capture_template_blank_note);

            for (int i = 0; i < shareTemplates.size(); i++) {
                items[i + 1] = shareTemplates.get(i).getName();
            }

            dialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.capture_template_picker_title)
                    .setItems(items, (dialogInterface, which) -> {
                        CaptureTemplateEntity template = which == 0 ? null : shareTemplates.get(which - 1);
                        showNoteEditor(data, template);
                    })
                    .setNegativeButton(R.string.cancel, (dialogInterface, which) -> finish())
                    .show();
        }
    }

    private void showNoteEditor(Data data, CaptureTemplateEntity template) {
        try {
            CaptureTargetResolution target = CaptureTemplates.resolveTarget(
                    dataRepository,
                    this,
                    template,
                    data.bookId);

            NotePayload payload = CaptureTemplates.buildPayload(
                    this,
                    template,
                    new CaptureInput(data.title, data.content));

            if (target.getMissingHeadingPath() != null) {
                AppSnackbarUtils.showSnackbar(
                        this,
                        getString(R.string.capture_template_target_heading_missing_summary, target.getMissingHeadingPath()));
            }

            NoteFragment noteFragment = NoteFragment.forNewNote(target.getPlace(), payload);

            if (noteFragment != null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.activity_share_main, noteFragment, NoteFragment.FRAGMENT_TAG)
                        .commit();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            finish();
        }
    }
}
