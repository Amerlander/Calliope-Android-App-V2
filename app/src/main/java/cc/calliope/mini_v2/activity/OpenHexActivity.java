package cc.calliope.mini_v2.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import cc.calliope.mini_v2.R;
import cc.calliope.mini_v2.adapter.ExtendedBluetoothDevice;
import cc.calliope.mini_v2.databinding.ActivityHexBinding;
import cc.calliope.mini_v2.fragment.editors.Editor;
import cc.calliope.mini_v2.utils.FileUtils;
import cc.calliope.mini_v2.utils.StaticExtra;
import cc.calliope.mini_v2.utils.Utils;
import cc.calliope.mini_v2.viewmodels.ScannerLiveData;
import cc.calliope.mini_v2.views.FabMenuView;


public class OpenHexActivity extends ScannerActivity {
    private ActivityHexBinding binding;
    private ExtendedBluetoothDevice device;
    private boolean isStartFlashing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityHexBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setPatternFab(binding.patternFab);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        String scheme = intent.getScheme();

        Log.w("HexActivity", "action: " + action);
        Log.w("HexActivity", "type: " + type);
        Log.w("HexActivity", "scheme: " + scheme);

        if (Intent.ACTION_VIEW.equals(action) && type != null /*&& type.equals("application/octet-stream")*/) {
            Uri uri = intent.getData();
            String decodedUri;

            try {
                decodedUri = URLDecoder.decode(uri.toString(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }

            String name = FilenameUtils.getBaseName(decodedUri);

            binding.infoTextView.setText(
                    String.format(getString(R.string.open_hex_info), name)
            );

            binding.flashButton.setOnClickListener(v -> {
                try {
                    File file = FileUtils.getFile(this, Editor.LIBRARY.toString(), name);
                    if (file == null) {
                        return;
                    }

                    isStartFlashing = true;
                    copyFile(uri, file);
                    startDFUActivity(file);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        if (isStartFlashing) {
            finish();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    @Override
    protected void scanResults(final ScannerLiveData state) {
        super.scanResults(state);
        device = state.getCurrentDevice();
    }

    public void copyFile(Uri uri, File destFile) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        OutputStream outputStream = new FileOutputStream(destFile);

        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, length);
        }

        outputStream.flush();
        outputStream.close();
        inputStream.close();
    }

    private void startDFUActivity(File file) {
        if (device != null && device.isRelevant()) {
            final Intent intent = new Intent(this, DFUActivity.class);
            intent.putExtra(StaticExtra.EXTRA_DEVICE, device);
            intent.putExtra(StaticExtra.EXTRA_FILE_PATH, file.getAbsolutePath());
            startActivity(intent);
        } else {
            Utils.errorSnackbar(binding.getRoot(), getString(R.string.error_snackbar_no_connected)).show();
        }
    }
}