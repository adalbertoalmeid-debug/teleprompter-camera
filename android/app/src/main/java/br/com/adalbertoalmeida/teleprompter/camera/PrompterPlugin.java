package br.com.adalbertoalmeida.teleprompter.camera;

import android.view.KeyEvent;
import android.view.WindowManager;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Recursos nativos do prompter:
 *  - enter(): mantém a tela acesa e, se pedido, passa os botões de volume para o app
 *  - exit(): devolve o comportamento normal da tela e do volume
 *  - evento "volume" ({ key: "up" | "down" }) enquanto o prompter está aberto
 */
@CapacitorPlugin(name = "Prompter")
public class PrompterPlugin extends Plugin {

    private volatile boolean captureVolume = false;

    @PluginMethod
    public void enter(PluginCall call) {
        captureVolume = call.getBoolean("volumeKeys", true);
        getActivity().runOnUiThread(() ->
            getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        call.resolve();
    }

    @PluginMethod
    public void exit(PluginCall call) {
        captureVolume = false;
        getActivity().runOnUiThread(() ->
            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        call.resolve();
    }

    /** Chamado pela MainActivity. Retorna true quando o app consumiu a tecla. */
    boolean handleKey(KeyEvent event) {
        if (!captureVolume) return false;
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            JSObject data = new JSObject();
            data.put("key", code == KeyEvent.KEYCODE_VOLUME_UP ? "up" : "down");
            notifyListeners("volume", data);
        }
        return true; // consome o apertar e o soltar, para o volume do sistema não mudar
    }
}
