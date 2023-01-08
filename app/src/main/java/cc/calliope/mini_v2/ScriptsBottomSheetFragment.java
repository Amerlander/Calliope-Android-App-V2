package cc.calliope.mini_v2;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.ArrayList;

import cc.calliope.mini_v2.adapter.ExtendedBluetoothDevice;
import cc.calliope.mini_v2.databinding.FragmentScriptsBottomSheetBinding;
import cc.calliope.mini_v2.ui.editors.Editor;
import cc.calliope.mini_v2.utils.Utils;

public class ScriptsBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String FILE_EXTENSION = ".hex";
    private static final String DEVICE = "device_parcelable";
    private FragmentScriptsBottomSheetBinding binding;
    private ExtendedBluetoothDevice device;
    private ScriptsRecyclerAdapter scriptsRecyclerAdapter;
    private Activity activity;

    public static ScriptsBottomSheetFragment newInstance(ExtendedBluetoothDevice device) {

        final ScriptsBottomSheetFragment fragment = new ScriptsBottomSheetFragment();
        final Bundle args = new Bundle();
        args.putParcelable(DEVICE, device);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        binding = FragmentScriptsBottomSheetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        activity = getActivity();
        if(activity == null)
            return;

//        DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
//        int height = displayMetrics.heightPixels;
//        int maxHeight = (int) (height*0.40);
//
//        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from((View) view.getParent());
//        behavior.setPeekHeight(maxHeight);

        Bundle bundle = getArguments();
        if (bundle != null)
            device = bundle.getParcelable(DEVICE);

        ArrayList<FileWrapper> filesList = new ArrayList<>();

        filesList.addAll(getFiles(Editor.MAKECODE));
        filesList.addAll(getFiles(Editor.ROBERTA));
        filesList.addAll(getFiles(Editor.LIBRARY));

        if (!filesList.isEmpty()) {
            final RecyclerView recyclerView = binding.scriptsRecyclerView;
            recyclerView.setItemAnimator(new DefaultItemAnimator());
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            scriptsRecyclerAdapter = new ScriptsRecyclerAdapter(filesList);
            scriptsRecyclerAdapter.setOnItemClickListener(this::openDFUActivity);
            scriptsRecyclerAdapter.setOnItemLongClickListener(this::openPopupMenu);
            recyclerView.setAdapter(scriptsRecyclerAdapter);
        }else{
            Log.w("onViewCreated", "filesList is empty");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private ArrayList<FileWrapper> getFiles(Editor editor) {
        File[] filesArray = new File(activity.getFilesDir().toString() + File.separator + editor).listFiles();

        ArrayList<FileWrapper> filesList = new ArrayList<>();

        if (filesArray != null && filesArray.length > 0) {
            for (File file : filesArray) {
                String name = file.getName();
                if (name.contains(FILE_EXTENSION)) {
                    filesList.add(new FileWrapper(file, editor));
                }
            }
        }
        return filesList;
    }

    private void openDFUActivity(FileWrapper file) {
        if (device != null && device.isRelevant()) {
            final Intent intent = new Intent(activity, DFUActivity.class);
            intent.putExtra("cc.calliope.mini.EXTRA_DEVICE", device);
            intent.putExtra("EXTRA_FILE", file.getAbsolutePath());
            startActivity(intent);
        } else {
            Utils.errorSnackbar(binding.getRoot(), "No mini connected").show();
        }
    }

    private void openPopupMenu(View view, FileWrapper file) {
        PopupMenu popup = new PopupMenu(view.getContext(), view);
        popup.setOnMenuItemClickListener(item -> {
            //Non-constant Fields
            int id = item.getItemId();
            if (id == R.id.rename) {
                return renameFile(file);
            } else if (id == R.id.share) {
                return shareFile(file.getFile());
            } else if (id == R.id.remove) {
                return removeFile(file);
            } else {
                return false;
            }
        });
        popup.inflate(R.menu.scripts_popup_menu);
        popup.show();
    }

    private boolean renameFile(FileWrapper file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_rename, activity.findViewById(R.id.layoutDialogContainer));
        builder.setView(view);

        ((TextView) view.findViewById(R.id.textTitle)).setText("Rename file");
        EditText editText = view.findViewById(R.id.textName);
        editText.setText(FilenameUtils.removeExtension(file.getName()));
//        editText.requestFocus();

        ((Button) view.findViewById(R.id.buttonYes)).setText("Rename");
        ((Button) view.findViewById(R.id.buttonNo)).setText("Cancel");
        final AlertDialog alertDialog = builder.create();
        view.findViewById(R.id.buttonYes).setOnClickListener(view1 -> {
            File dir = new File(FilenameUtils.getFullPath(file.getAbsolutePath()));
            if (dir.exists()) {
                FileWrapper dest = new FileWrapper(new File(dir, editText.getText().toString() + FILE_EXTENSION), file.getEditor());
                if (file.exists()) {
                    if (!dest.exists() && file.renameTo(dest.getFile())) {
                        scriptsRecyclerAdapter.change(file, dest);
                    } else {
                        Utils.errorSnackbar(view, "The file with this name exists").show();
                        return;
                    }
                }
            }
            alertDialog.dismiss();
        });
        view.findViewById(R.id.buttonNo).setOnClickListener(view12 -> alertDialog.dismiss());
        if (alertDialog.getWindow() != null) {
            alertDialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
        }
        alertDialog.show();
        return true;
    }

    private boolean shareFile(File file) {
        Intent intentShareFile = new Intent(Intent.ACTION_SEND);
        Uri uri = FileProvider.getUriForFile(activity, "cc.calliope.file_provider", file);

        if (file.exists()) {
            intentShareFile.setType("text/plain");
            intentShareFile.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intentShareFile.putExtra(Intent.EXTRA_STREAM, uri);
            intentShareFile.putExtra(Intent.EXTRA_SUBJECT, "Sharing File...");
            intentShareFile.putExtra(Intent.EXTRA_TEXT, "Calliope mini firmware");

            startActivity(Intent.createChooser(intentShareFile, "Share File"));
        }
        return true;
    }

    private boolean removeFile(FileWrapper file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        View view = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_warning, activity.findViewById(R.id.layoutDialogContainer));
        builder.setView(view);

        ((TextView) view.findViewById(R.id.textTitle)).setText("Delete file");
        ((TextView) view.findViewById(R.id.textMessage)).setText(String.format(
                "You will permanently delete \"%s\".", FilenameUtils.removeExtension(file.getName())));
        ((Button) view.findViewById(R.id.buttonYes)).setText("Continue");
        ((Button) view.findViewById(R.id.buttonNo)).setText("Cancel");
        final AlertDialog alertDialog = builder.create();
        view.findViewById(R.id.buttonYes).setOnClickListener(view1 -> {
            if (file.delete()) {
                scriptsRecyclerAdapter.remove(file);
//                setBottomSheetVisibility(!recyclerAdapter.isEmpty());
                alertDialog.dismiss();
            }
        });
        view.findViewById(R.id.buttonNo).setOnClickListener(view12 -> alertDialog.dismiss());
        if (alertDialog.getWindow() != null) {
            alertDialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
        }
        alertDialog.show();
        return true;
    }
}