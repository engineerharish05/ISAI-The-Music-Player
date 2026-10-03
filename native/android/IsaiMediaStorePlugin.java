package com.rishinnovations.isai;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

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
    public void startPlaybackService(PluginCall call) {
        try {
            Intent intent = new Intent(getContext(), IsaiPlaybackService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getContext().startForegroundService(intent);
            } else {
                getContext().startService(intent);
            }
            call.resolve();
        } catch (Exception error) {
            call.reject("Unable to start playback service: " + error.getMessage(), null, error);
        }
    }

    @PluginMethod
    public void stopPlaybackService(PluginCall call) {
        try {
            getContext().stopService(new Intent(getContext(), IsaiPlaybackService.class));
            call.resolve();
        } catch (Exception error) {
            call.reject("Unable to stop playback service: " + error.getMessage(), null, error);
        }
    }
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

    @PluginMethod
    public void prepare(PluginCall call) {
        if (getPermissionState("audio") != PermissionState.GRANTED) {
            call.reject("Audio permission is required");
            return;
        }

        String uriValue = call.getString("uri");
        String displayName = call.getString("displayName", "track.mp3");
        if (uriValue == null || uriValue.isEmpty()) {
            call.reject("Missing audio URI");
            return;
        }

        ContentResolver resolver = getContext().getContentResolver();
        Uri source = Uri.parse(uriValue);
        File cacheDir = getContext().getCacheDir();
        String extension = ".audio";
        int dot = displayName.lastIndexOf('.');
        if (dot >= 0 && dot < displayName.length() - 1) {
            extension = displayName.substring(dot).replaceAll("[^A-Za-z0-9.]", "");
            if (extension.length() < 2 || extension.length() > 8) {
                extension = ".audio";
            }
        }

        String safeId = Integer.toHexString(uriValue.hashCode());
        File target = new File(cacheDir, "isai-" + safeId + extension);

        try (InputStream input = resolver.openInputStream(source);
             FileOutputStream output = new FileOutputStream(target, false)) {
            if (input == null) {
                call.reject("Unable to open selected audio");
                return;
            }

            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();

            JSObject result = new JSObject();
            result.put("uri", Uri.fromFile(target).toString());
            call.resolve(result);
        } catch (SecurityException error) {
            call.reject("Audio permission is required", null, error);
        } catch (Exception error) {
            call.reject("Unable to prepare audio: " + error.getMessage(), null, error);
        }
    }

    private String displayNameOrFallback(Cursor cursor, int displayCol) {
        if (displayCol >= 0) {
            String value = cursor.getString(displayCol);
            if (value != null && !value.trim().isEmpty()) return value;
        }
        return "Unknown track";
    }

    private void scanInternal(PluginCall call) {
        ContentResolver resolver = getContext().getContentResolver();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE
        };

        String selection = MediaStore.Audio.Media.MIME_TYPE + " LIKE ? AND "
            + MediaStore.Audio.Media.DURATION + " > 0";
        String[] selectionArgs = {"audio/%"};

        JSArray media = new JSArray();

        try (Cursor cursor = resolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
        )) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                int albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                int displayCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
                int mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String title = cursor.getString(titleCol);
                    String artist = cursor.getString(artistCol);
                    String album = cursor.getString(albumCol);
                    long albumId = cursor.getLong(albumIdCol);
                    long duration = cursor.getLong(durationCol);
                    String displayName = cursor.getString(displayCol);
                    String mime = cursor.getString(mimeCol);
                    if (title == null || title.trim().isEmpty()) title = displayNameOrFallback(cursor, displayCol);

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
                    item.put("genre", "Local");
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
