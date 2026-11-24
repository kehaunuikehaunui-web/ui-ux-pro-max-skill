package com.pochexpf.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Coquille Android : affiche la page Poche XPF embarquée, données en localStorage. */
public class MainActivity extends Activity {
    private static final int REQ_SAVE = 41;
    private static final int REQ_OPEN = 42;
    private static final int MAX_FILE = 5_000_000;

    private WebView web;
    private String pendingSave;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setTextZoom(100);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        web.setWebViewClient(new LinkClient());
        web.addJavascriptInterface(new Bridge(this), "PocheAndroid");
        setContentView(web);
        if (state == null || web.restoreState(state) == null) {
            web.loadUrl("file:///android_asset/index.html");
        }
    }

    /** Asks the user where to save the backup file (Storage Access Framework). */
    void startSave(String name, String content) {
        pendingSave = content;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, name);
        try {
            startActivityForResult(i, REQ_SAVE);
        } catch (Exception e) {
            pendingSave = null;
            reply("error", "sélecteur de fichiers indisponible");
        }
    }

    /** Asks the user to pick a backup file to restore. */
    void startOpen() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_OPEN);
        } catch (Exception e) {
            reply("error", "sélecteur de fichiers indisponible");
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        Uri uri = (result == RESULT_OK && data != null) ? data.getData() : null;
        if (request == REQ_SAVE) {
            String content = pendingSave;
            pendingSave = null;
            if (uri == null || content == null) { reply("cancel", ""); return; }
            try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) throw new IOException("no stream");
                out.write(content.getBytes(StandardCharsets.UTF_8));
                reply("saved", "");
            } catch (Exception e) {
                reply("error", "le fichier n'a pas pu être écrit");
            }
        } else if (request == REQ_OPEN) {
            if (uri == null) { reply("cancel", ""); return; }
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("no stream");
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) > 0) {
                    buf.write(chunk, 0, n);
                    if (buf.size() > MAX_FILE) throw new IOException("too big");
                }
                reply("file", new String(buf.toByteArray(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                reply("error", "le fichier n'a pas pu être lu");
            }
        }
    }

    private void reply(String type, String payload) {
        String js = "window.pocheNative&&window.pocheNative(" + JSONObject.quote(type) + "," + JSONObject.quote(payload) + ")";
        web.evaluateJavascript(js, null);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
