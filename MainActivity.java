package com.chacarestaurante.pos;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import androidx.core.content.FileProvider;

public class MainActivity extends Activity {

    // ✅ CAMBIA ESTA URL por la de tu deploy de Google Apps Script
    private static final String POS_URL = "https://script.google.com/a/macros/digitalposapp.com/s/AKfycbzGrKC0dj_KwxrtdbtRqQEELEml91xV6A1j3ZZ7vqG_BAlSK28OG1q8Wm-seu8OCTY6YA/exec";

    private static final String TAG = "ChacaPOS";
    private WebView webView;
    private ProgressBar progressBar;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Pantalla siempre encendida (útil para tablet en el restaurante)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Layout principal
        RelativeLayout layout = new RelativeLayout(this);
        layout.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT));

        // WebView
        webView = new WebView(this);
        RelativeLayout.LayoutParams webParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);
        webView.setLayoutParams(webParams);

        // ProgressBar de carga
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, 8));
        progressBar.setMax(100);
        progressBar.setVisibility(View.VISIBLE);

        layout.addView(webView);
        layout.addView(progressBar);
        setContentView(layout);

        // Configurar WebView
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " ChacaPOS/1.0");

        // ✅ JavascriptInterface — el puente clave para impresión automática
        webView.addJavascriptInterface(new PrintBridge(), "AndroidPrint");

        // WebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Dejar que Google Apps Script maneje sus propias URLs
                if (url.startsWith("https://script.google.com") ||
                    url.startsWith("https://accounts.google.com")) {
                    return false;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);

                // ✅ Inyectar el bridge JS al cargar la página
                // Esto sobreescribe _descargarTicketBin() con la versión nativa
                String bridgeScript =
                    "javascript:(function(){" +
                    "  if(typeof _ticketBytesActuales !== 'undefined'){" +
                    "    window._descargarTicketBin = function(){" +
                    "      if(!_ticketBytesActuales){ return; }" +
                    "      try{" +
                    "        var b64 = btoa(String.fromCharCode.apply(null, new Uint8Array(_ticketBytesActuales)));" +
                    "        AndroidPrint.imprimirEscPos(b64);" +
                    "        var m = document.getElementById('_modalTicketPreview');" +
                    "        if(m) m.style.display='none';" +
                    "      }catch(e){ console.error('Bridge error:',e); }" +
                    "    };" +
                    "  }" +
                    "  window.addEventListener('_ticketListo', function(e){" +
                    "    if(e.detail && e.detail.bytes){" +
                    "      var b64 = btoa(String.fromCharCode.apply(null, new Uint8Array(e.detail.bytes)));" +
                    "      AndroidPrint.imprimirEscPos(b64);" +
                    "    }" +
                    "  });" +
                    "})();";
                view.loadUrl(bridgeScript);
            }

            @Override
            public void onPageStarted(android.webkit.WebView view, String url, android.graphics.Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(10);
            }
        });

        // WebChromeClient para progreso
        webView.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) {
                    mainHandler.postDelayed(() -> progressBar.setVisibility(View.GONE), 300);
                }
            }
        });

        webView.loadUrl(POS_URL);
    }

    // ══════════════════════════════════════════════════════════
    // ✅ BRIDGE DE IMPRESIÓN — llamado desde JavaScript
    // ══════════════════════════════════════════════════════════
    private class PrintBridge {

        @JavascriptInterface
        public void imprimirEscPos(String base64Data) {
            Log.d(TAG, "imprimirEscPos llamado, bytes b64: " + base64Data.length());
            try {
                // Decodificar base64 a bytes
                byte[] escposBytes = Base64.decode(base64Data, Base64.DEFAULT);
                Log.d(TAG, "Bytes decodificados: " + escposBytes.length);

                // Guardar en archivo temporal
                File tempDir = new File(getCacheDir(), "tickets");
                if (!tempDir.exists()) tempDir.mkdirs();

                File ticketFile = new File(tempDir, "ticket_" + System.currentTimeMillis() + ".bin");
                try (FileOutputStream fos = new FileOutputStream(ticketFile)) {
                    fos.write(escposBytes);
                    fos.flush();
                }
                Log.d(TAG, "Archivo guardado: " + ticketFile.getAbsolutePath());

                // Obtener URI via FileProvider para compartir con RawBT
                Uri fileUri = FileProvider.getUriForFile(
                        MainActivity.this,
                        "com.chacarestaurante.pos.fileprovider",
                        ticketFile
                );

                // ✅ Intent directo a RawBT — SIN selector de apps, SIN diálogos
                Intent printIntent = new Intent(Intent.ACTION_VIEW);
                printIntent.setDataAndType(fileUri, "application/octet-stream");
                printIntent.setPackage("ru.a402d.rawbtprinter"); // Package de RawBT
                printIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                printIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // Verificar que RawBT está instalado
                if (getPackageManager().resolveActivity(printIntent, 0) != null) {
                    startActivity(printIntent);
                    Log.d(TAG, "RawBT iniciado OK");
                    mainHandler.post(() ->
                        Toast.makeText(MainActivity.this, "Imprimiendo...", Toast.LENGTH_SHORT).show()
                    );
                } else {
                    Log.w(TAG, "RawBT no encontrado, intentando selector...");
                    // Fallback: mostrar selector si RawBT no está instalado
                    Intent chooser = Intent.createChooser(printIntent, "Imprimir con...");
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(chooser);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error en imprimirEscPos: " + e.getMessage(), e);
                mainHandler.post(() ->
                    Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }

        @JavascriptInterface
        public boolean rawBTDisponible() {
            Intent test = new Intent(Intent.ACTION_VIEW);
            test.setPackage("ru.a402d.rawbtprinter");
            return getPackageManager().resolveActivity(test, 0) != null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
}
