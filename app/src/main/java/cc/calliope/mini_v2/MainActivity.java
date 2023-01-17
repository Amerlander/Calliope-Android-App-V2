package cc.calliope.mini_v2;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;
import cc.calliope.mini_v2.adapter.ExtendedBluetoothDevice;
import cc.calliope.mini_v2.databinding.ActivityMainBinding;
import cc.calliope.mini_v2.service.DfuService;
import cc.calliope.mini_v2.ui.dialog.PatternDialogFragment;
import cc.calliope.mini_v2.utils.Permission;
import cc.calliope.mini_v2.utils.Utils;
import cc.calliope.mini_v2.utils.Version;
import cc.calliope.mini_v2.viewmodels.ScannerLiveData;
import cc.calliope.mini_v2.viewmodels.ScannerViewModel;
import cc.calliope.mini_v2.views.FobParams;
import cc.calliope.mini_v2.views.MovableFloatingActionButton;
import no.nordicsemi.android.dfu.DfuProgressListener;
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter;
import no.nordicsemi.android.dfu.DfuServiceListenerHelper;


public class MainActivity extends AppCompatActivity implements DialogInterface.OnDismissListener {
    private static final int REQUEST_CODE = 1022; // random number
    private static final int SNACKBAR_DURATION = 10000; // how long to display the snackbar message.
    private static boolean requestWasSent = false;

    private ActivityMainBinding binding;
    private ScannerViewModel scannerViewModel;

    private View rootView;
    private MovableFloatingActionButton patternFab;
    private ConstraintLayout mainLayout;
    private LinearLayout noPermissionLayout;
    private Button actionButton;
    private Button settingsButton;

    private ImageButton fullScreenButton;

    private Boolean isFullScreen = false;

    private Boolean isFlashingProcess = false; //

    ActivityResultLauncher<Intent> bluetoothEnableResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    if (checkPermission()) {
                        scannerViewModel.startScan();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavController navController = Navigation.findNavController(this, R.id.navigation_host_fragment);
        NavigationUI.setupWithNavController(binding.bottomNavigation, navController);

        scannerViewModel = new ViewModelProvider(this).get(ScannerViewModel.class);
        scannerViewModel.getScannerState().observe(this, this::scanResults);

        getViews();

        patternFab.setOnClickListener(this::onFabClick);
        actionButton.setOnClickListener(v -> requestPermissions());
        settingsButton.setOnClickListener(v -> requestAppSettings());

        if (fullScreenButton != null) {
            fullScreenButton.setOnClickListener(this::setFullScreenMode);
        }

//        registerCallbacksForFlashing();

    }

    @Override
    public void onBackPressed() {
        if (isFullScreen) {
            isFullScreen = false;
            binding.bottomNavigation.setVisibility(View.VISIBLE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            super.onBackPressed();
        }
    }

    private void getViews() {
        rootView = binding.getRoot();
        patternFab = binding.patternFab;
        actionButton = binding.noPermissionLayout.actionButton;
        settingsButton = binding.noPermissionLayout.settingsButton;
        mainLayout = binding.mainLayout;
        noPermissionLayout = binding.noPermissionLayout.getRoot();
        fullScreenButton = binding.fullscreenButton;
    }

    @Override
    public void onResume() {
        super.onResume();
        requestWasSent = false;

        if (checkPermission()) {
            if (!Utils.isBluetoothEnabled()) {
                showBluetoothDisabledWarning();
            } else if (!Version.upperSnowCone && !Utils.isLocationEnabled(this)) {
                showLocationDisabledWarning();
            }
            scannerViewModel.startScan();
        }

        DfuServiceListenerHelper.registerProgressListener(this, mDfuProgressListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        scannerViewModel.stopScan();
        DfuServiceListenerHelper.unregisterProgressListener(this, mDfuProgressListener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    @Override
    public void onDismiss(final DialogInterface dialog) {
        //Fragment dialog had been dismissed
//        fab.setVisibility(View.VISIBLE);
    }

    private void onFabClick(View view) {
        view.startAnimation(new AlphaAnimation(1F, 0.75F));

        if(isFlashingProcess){
            final Intent intent = new Intent(this, DFUActivity.class);
            startActivity(intent);
        }else {
            showPatternDialog(new FobParams(
                    view.getWidth(),
                    view.getHeight(),
                    view.getX(),
                    view.getY()
            ));
        }
    }

    private void setFullScreenMode(View view) {
        isFullScreen = true;
        binding.bottomNavigation.setVisibility(View.GONE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private int getColorWrapper(int id) {
        if (Version.upperMarshmallow) {
            return super.getColor(id);
        } else {
            //noinspection deprecation
            return getResources().getColor(id);
        }
    }

    private void scanResults(final ScannerLiveData state) {
//        Log.v("SCANNER", "current device: " + state.getCurrentDevice());

        if (hasOpenedDialogs())
            return;

        if (!state.isBluetoothEnabled() && !requestWasSent) {
            showBluetoothDisabledWarning();
        }

        ExtendedBluetoothDevice device = state.getCurrentDevice();
        int color = getColorWrapper(
                (device != null && device.isRelevant())
                        || isFlashingProcess ?
                R.color.green :
                R.color.orange);
        patternFab.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    private boolean checkPermission() {
        boolean isBluetoothAccessGranted = Permission.isAccessGranted(this, Permission.BLUETOOTH);
        boolean isLocationAccessGranted = Version.upperSnowCone || Permission.isAccessGranted(this, Permission.LOCATION);

        if (isBluetoothAccessGranted && isLocationAccessGranted) {
            showContent();
            return true;
        } else {
            showInfoNoPermission(isBluetoothAccessGranted ? Permission.LOCATION : Permission.BLUETOOTH);
            return false;
        }
    }

    private void showBluetoothDisabledWarning() {
        Snackbar snackbar = Utils.errorSnackbar(rootView, "Bluetooth is disable");
        snackbar.setDuration(SNACKBAR_DURATION);
        snackbar.setAction("Enable", this::openBluetoothEnableActivity)
                .show();
    }

    private void showLocationDisabledWarning() {
        Utils.errorSnackbar(rootView, "Location is disable")
                .show();
    }

    private void showContent() {
        mainLayout.setVisibility(View.VISIBLE);
        noPermissionLayout.setVisibility(View.GONE);
    }

    private void showInfoNoPermission(@Permission.RequestType int requestType) {
        ContentNoPermission content = ContentNoPermission.getContent(requestType);
        boolean deniedForever = Permission.isAccessDeniedForever(this, requestType);

        mainLayout.setVisibility(View.GONE);
        noPermissionLayout.setVisibility(View.VISIBLE);

        binding.noPermissionLayout.iconImageView.setImageResource(content.getIcResId());
        binding.noPermissionLayout.titleTextView.setText(content.getTitleResId());
        binding.noPermissionLayout.messageTextView.setText(content.getMessageResId());

        actionButton.setVisibility(deniedForever ? View.GONE : View.VISIBLE);
        settingsButton.setVisibility(deniedForever ? View.VISIBLE : View.GONE);
    }

    public void openBluetoothEnableActivity(View view) {
        requestWasSent = true;
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        bluetoothEnableResultLauncher.launch(enableBtIntent);
    }

    private void showPatternDialog(FobParams params) {
//        patternFab.setBackgroundTintList(ColorStateList.valueOf(getColorWrapper(R.color.orange)));
        scannerViewModel.startScan(); // On older devices, "auto-start" scanning does not work after bluetooth is turned on.

        FragmentManager fragmentManager = getSupportFragmentManager();
        PatternDialogFragment dialogFragment = PatternDialogFragment.newInstance(params);
        dialogFragment.show(fragmentManager, "fragment_pattern");
//        fab.setVisibility(View.GONE);
    }

    private void requestPermissions() {
        int requestType = Permission.isAccessGranted(this, Permission.BLUETOOTH) ?
                Permission.LOCATION :
                Permission.BLUETOOTH;
        String[] permissionsArray = Permission.getPermissionsArray(requestType);

        Permission.markPermissionRequested(this, requestType);
        ActivityCompat.requestPermissions(this, permissionsArray, REQUEST_CODE);
    }

    private void requestAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", getPackageName(), null);
        intent.setData(uri);
        startActivity(intent);
    }

    private boolean hasOpenedDialogs() {
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        for (Fragment fragment : fragments) {
            if (fragment instanceof DialogFragment) {
                return true;
            }
        }
        return false;
    }

    /**
     * Registers callbacks that allows to handle flashing process
     * and react to flashing progress, errors and log some messages.
     */
    private void registerCallbacksForFlashing() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(DfuService.BROADCAST_PROGRESS);
        filter.addAction(DfuService.BROADCAST_ERROR);
        filter.addAction(DfuService.BROADCAST_LOG);
        BroadcastReceiver dfuResultReceiver = new DFUResultReceiver();

        LocalBroadcastManager.getInstance(this).registerReceiver(dfuResultReceiver, filter);
    }

    private final DfuProgressListener mDfuProgressListener = new DfuProgressListenerAdapter() {
        private static final String TAG_PL = "DfuProgressListener";

        @Override
        public void onDfuProcessStarting(@NonNull final String deviceAddress) {
            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
            Log.e(TAG_PL, method);
            isFlashingProcess = true;
            binding.patternFab.setProgress(1);
        }

        @Override
        public void onDfuCompleted(@NonNull final String deviceAddress) {
            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
            Log.e(TAG_PL, method);
            isFlashingProcess = false;
            binding.patternFab.setProgress(0);
        }

        @Override
        public void onDfuAborted(@NonNull final String deviceAddress) {
            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
            Log.e(TAG_PL, method);
            isFlashingProcess = false;
            binding.patternFab.setProgress(0);
        }

        @Override
        public void onProgressChanged(@NonNull final String deviceAddress, final int percent, final float speed, final float avgSpeed, final int currentPart, final int partsTotal) {
            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
            Log.e(TAG_PL, method + " percent: " + percent);
            isFlashingProcess = true;
            binding.patternFab.setProgress(percent);
        }

        @Override
        public void onError(@NonNull final String deviceAddress, final int error, final int errorType, final String message) {
            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
            Log.e(TAG_PL, method + " error: " + message);
            isFlashingProcess = false;
            binding.patternFab.setProgress(0);
        }
    };
}