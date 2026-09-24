package br.com.adalbertoalmeida.teleprompter.camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.Surface;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;

/**
 * Câmera nativa atrás da página (a WebView fica transparente por cima).
 * Grava só a câmera e o microfone: o texto do prompter NÃO entra no vídeo.
 *
 * start({facing})  abre a câmera ("front" | "back")
 * flip()           alterna frontal/traseira (fora da gravação)
 * record()         começa a gravar (MP4 na galeria, pasta Movies/Teleprompter)
 * stop()           termina a gravação -> evento "recordingFinished" { uri, error }
 * close()          fecha a câmera e devolve a WebView ao normal
 */
@CapacitorPlugin(
    name = "Recorder",
    permissions = {
        @Permission(strings = { Manifest.permission.CAMERA }, alias = "camera"),
        @Permission(strings = { Manifest.permission.RECORD_AUDIO }, alias = "microphone"),
        @Permission(strings = { Manifest.permission.WRITE_EXTERNAL_STORAGE }, alias = "storage")
    }
)
public class RecorderPlugin extends Plugin {

    private ProcessCameraProvider provider;
    private PreviewView previewView;
    private Preview preview;
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private String facing = "front";

    private Executor main() { return ContextCompat.getMainExecutor(getContext()); }

    @SuppressWarnings("deprecation")
    private int displayRotation() {
        try { return getActivity().getWindowManager().getDefaultDisplay().getRotation(); }
        catch (Exception e) { return Surface.ROTATION_0; }
    }

    // ---------- abrir / fechar ----------

    @PluginMethod
    public void start(PluginCall call) {
        facing = "back".equals(call.getString("facing")) ? "back" : "front";
        boolean legacyStorage = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
        if (getPermissionState("camera") != PermissionState.GRANTED
                || getPermissionState("microphone") != PermissionState.GRANTED
                || (legacyStorage && getPermissionState("storage") != PermissionState.GRANTED)) {
            String[] aliases = legacyStorage
                ? new String[] { "camera", "microphone", "storage" }
                : new String[] { "camera", "microphone" };
            requestPermissionForAliases(aliases, call, "permsCallback");
            return;
        }
        openCamera(call);
    }

    @PermissionCallback
    private void permsCallback(PluginCall call) {
        if (getPermissionState("camera") != PermissionState.GRANTED) {
            call.reject("Permissão da câmera negada. Libere em Configurações > Apps > Teleprompter Câmera.", "NO_CAMERA");
            return;
        }
        openCamera(call);
    }

    private void openCamera(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            try {
                WebView web = getBridge().getWebView();
                if (previewView == null) {
                    previewView = new PreviewView(getContext());
                    previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
                    previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
                    ViewGroup parent = (ViewGroup) web.getParent();
                    parent.addView(previewView, 0, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                }
                web.setBackgroundColor(Color.TRANSPARENT);
                ProcessCameraProvider.getInstance(getContext()).addListener(() -> {
                    try {
                        provider = ProcessCameraProvider.getInstance(getContext()).get();
                        bind();
                        JSObject r = new JSObject();
                        r.put("facing", facing);
                        r.put("audio", getPermissionState("microphone") == PermissionState.GRANTED);
                        call.resolve(r);
                    } catch (Exception e) {
                        call.reject("Não foi possível abrir a câmera: " + e.getMessage(), "CAMERA_ERROR");
                    }
                }, main());
            } catch (Exception e) {
                call.reject("Não foi possível abrir a câmera: " + e.getMessage(), "CAMERA_ERROR");
            }
        });
    }

    private void bind() {
        int rot = displayRotation();
        preview = new Preview.Builder().setTargetRotation(rot).build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        Recorder recorder = new Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.FHD,
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
            .build();
        videoCapture = VideoCapture.withOutput(recorder);
        videoCapture.setTargetRotation(rot);
        CameraSelector selector = "back".equals(facing)
            ? CameraSelector.DEFAULT_BACK_CAMERA : CameraSelector.DEFAULT_FRONT_CAMERA;
        provider.unbindAll();
        provider.bindToLifecycle((LifecycleOwner) getActivity(), selector, preview, videoCapture);
    }

    @PluginMethod
    public void flip(PluginCall call) {
        if (recording != null) { call.reject("Pare a gravação antes de trocar a câmera.", "RECORDING"); return; }
        if (provider == null) { call.reject("A câmera não está aberta.", "NOT_OPEN"); return; }
        getActivity().runOnUiThread(() -> {
            String old = facing;
            facing = "back".equals(facing) ? "front" : "back";
            try { bind(); }
            catch (Exception e) {
                facing = old;
                try { bind(); } catch (Exception ignored) {}
                call.reject("Este aparelho não tem essa câmera disponível.", "NO_SUCH_CAMERA");
                return;
            }
            JSObject r = new JSObject();
            r.put("facing", facing);
            call.resolve(r);
        });
    }

    @PluginMethod
    public void close(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (recording != null) { recording.stop(); recording = null; }
            if (provider != null) provider.unbindAll();
            if (previewView != null) {
                ViewGroup parent = (ViewGroup) previewView.getParent();
                if (parent != null) parent.removeView(previewView);
                previewView = null;
            }
            getBridge().getWebView().setBackgroundColor(Color.BLACK);
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            call.resolve();
        });
    }

    // ---------- gravação ----------

    @SuppressLint("MissingPermission") // o microfone só é ligado quando a permissão foi concedida
    @PluginMethod
    public void record(PluginCall call) {
        if (videoCapture == null) { call.reject("A câmera não está aberta.", "NOT_OPEN"); return; }
        if (recording != null) { call.reject("Já está gravando.", "RECORDING"); return; }
        getActivity().runOnUiThread(() -> {
            try {
                // trava a orientação da tela durante a gravação
                getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
                videoCapture.setTargetRotation(displayRotation());

                String name = "Teleprompter_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                cv.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cv.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Teleprompter");
                }
                MediaStoreOutputOptions opts = new MediaStoreOutputOptions.Builder(
                    getContext().getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                    .setContentValues(cv).build();

                PendingRecording pending = videoCapture.getOutput().prepareRecording(getContext(), opts);
                boolean audio = getPermissionState("microphone") == PermissionState.GRANTED;
                if (audio) pending = pending.withAudioEnabled();

                recording = pending.start(main(), event -> {
                    if (event instanceof VideoRecordEvent.Finalize) {
                        VideoRecordEvent.Finalize fin = (VideoRecordEvent.Finalize) event;
                        Uri uri = fin.getOutputResults().getOutputUri();
                        JSObject r = new JSObject();
                        boolean saved = uri != null && !Uri.EMPTY.equals(uri);
                        r.put("saved", saved);
                        if (saved) r.put("uri", uri.toString());
                        if (fin.hasError()) r.put("error", fin.getError());
                        recording = null;
                        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                        notifyListeners("recordingFinished", r);
                    }
                });
                JSObject r = new JSObject();
                r.put("audio", audio);
                call.resolve(r);
            } catch (Exception e) {
                recording = null;
                getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                call.reject("Não foi possível iniciar a gravação: " + e.getMessage(), "RECORD_ERROR");
            }
        });
    }

    @PluginMethod
    public void stop(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (recording != null) recording.stop(); // o resultado chega no evento "recordingFinished"
            call.resolve();
        });
    }

    // Ao girar o celular (fora da gravação), a câmera acompanha a nova orientação
    @Override
    protected void handleOnConfigurationChanged(Configuration newConfig) {
        super.handleOnConfigurationChanged(newConfig);
        if (recording == null && preview != null && videoCapture != null) {
            int rot = displayRotation();
            preview.setTargetRotation(rot);
            videoCapture.setTargetRotation(rot);
        }
    }
}
