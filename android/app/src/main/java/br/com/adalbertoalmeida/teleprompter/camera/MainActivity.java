package br.com.adalbertoalmeida.teleprompter.camera;

import android.os.Bundle;
import android.view.KeyEvent;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.PluginHandle;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PrompterPlugin.class);
        registerPlugin(RecorderPlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (bridge != null) {
            PluginHandle handle = bridge.getPlugin("Prompter");
            if (handle != null && handle.getInstance() instanceof PrompterPlugin
                    && ((PrompterPlugin) handle.getInstance()).handleKey(event)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
