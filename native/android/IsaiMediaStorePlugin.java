package com.rishinnovations.isai;

import android.Manifest;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.PermissionState;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

@CapacitorPlugin(
    name = "IsaiMediaStore",
    permissions = {
        @Permission(
            alias = "audio",
            strings = {
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        )
    }
)
public class IsaiMediaStorePlugin extends Plugin {
    @PluginMethod
    public void scan(PluginCall call) {
        if (getPermissionState("audio") != PermissionState.GRANTED) {
            requestPermissionForAlias("audio", call, "scanPermissionCallback");
            return;
        }
        scanInternal(call);
    }

    @PermissionCallback
    private void scanPermissionCallback(PluginCall call) {
        if (getPermissionState("audio") == PermissionState.GRANTED) {
            scanInternal(call);
        } else {
            call.reject("Audio permission was denied");
        }
    }

    private void scanInternal(PluginCall call) {
        ContentResolver resolver = getContext().getContentResolver();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        java.util.ArrayList<String> projectionList = new java.util.ArrayList<>();
        projectionList.add(MediaStore.Audio.Media._ID);
        projectionList.add(MediaStore.Audio.Media.TITLE);
        projectionList.add(MediaStore.Audio.Media.ARTIST);
        projectionList.add(MediaStore.Audio.Media.ALBUM);
        projectionList.add(MediaStore.Audio.Media.ALBUM_ID);
        projectionList.add(MediaStore.Audio.Media.DURATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projectionList.add("genre");
        }
        projectionList.add(MediaStore.Audio.Media.DISPLAY_NAME);
        projectionList.add(MediaStore.Audio.Media.MIME_TYPE);
        String[] projection = projectionList.toArray(new String[0]);

        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND "
            + MediaStore.Audio.Media.DURATION + " > 0";

        JSArray media = new JSArray();

        try (Cursor cursor = resolver.query(
            collection,
            projection,
            selection,
            null,
            MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
        )) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int genreCol = cursor.getColumnIndex("genre");
                int displayCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
                int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String title = cursor.getString(titleCol);
                    String artist = cursor.getString(artistCol);
                    String album = cursor.getString(albumCol);
                    long albumId = cursor.getLong(albumIdCol);
                    long duration = cursor.getLong(durationCol);
                    String genre = genreCol >= 0 ? cursor.getString(genreCol) : null;
                    String displayName = cursor.getString(displayCol);
                    String mime = cursor.getString(mimeCol);

                    if (mime == null || !mime.toLowerCase().startsWith("audio/")) {
                        continue;
                    }

                    JSObject item = new JSObject();
                    item.put("id", id);
                    item.put("title", title);
                    item.put("artist", artist);
                    item.put("album", album);
                    item.put("albumId", albumId);
                    item.put("duration", duration);
                    item.put("genre", genre);
                    item.put("displayName", displayName);
                    item.put("mimeType", mime);
                    item.put("uri", Uri.withAppendedPath(collection, String.valueOf(id)).toString());
                    media.put(item);
                }
            }

            JSObject result = new JSObject();
            result.put("media", media);
            result.put("count", media.length());
            call.resolve(result);
        } catch (SecurityException error) {
            call.reject("Audio permission is required", null, error);
        } catch (Exception error) {
            call.reject("Unable to scan device music: " + error.getMessage(), null, error);
        }
    }
}
