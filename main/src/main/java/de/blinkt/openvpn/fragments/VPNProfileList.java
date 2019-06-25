/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.fragments;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListFragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.provider.OpenableColumns;
import android.security.KeyChain;
import android.support.annotation.RequiresApi;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import de.blinkt.openvpn.LaunchVPN;
import de.blinkt.openvpn.R;
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.activities.ConfigConverter;
import de.blinkt.openvpn.activities.DisconnectVPN;
import de.blinkt.openvpn.activities.FileSelect;
import de.blinkt.openvpn.activities.VPNPreferences;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ConnectionStatus;
import de.blinkt.openvpn.core.IOpenVPNServiceInternal;
import de.blinkt.openvpn.core.IServiceStatus;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.PasswordCache;
import de.blinkt.openvpn.core.Preferences;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VpnStatus;
import de.blinkt.openvpn.views.FileSelectLayout;

import static de.blinkt.openvpn.core.OpenVPNService.DISCONNECT_VPN;


public class VPNProfileList extends ListFragment implements OnClickListener, VpnStatus.StateListener , Handler.Callback {

    public final static int RESULT_VPN_DELETED = Activity.RESULT_FIRST_USER;
    public final static int RESULT_VPN_DUPLICATE = Activity.RESULT_FIRST_USER + 1;

    private static final int MENU_ADD_PROFILE = Menu.FIRST;

    private static final int START_VPN_CONFIG = 92;
    private static final int SELECT_PROFILE = 43;
    private static final int IMPORT_PROFILE = 231;
    private static final int IMPORT_PROFILE_LOCAL = 232;
    private static final int FILE_PICKER_RESULT_KITKAT = 392;
    private static  Button btn_connect_disconnect;
    private static final int MENU_IMPORT_PROFILE = Menu.FIRST + 1;
    private static final int MENU_CHANGE_SORTING = Menu.FIRST + 2;
    private static final String PREF_SORT_BY_LRU = "sortProfilesByLRU";
    private String mLastStatusMessage;
    Context mContext;
  private  static   ProgressBar progressBar;
    RelativeLayout progressLayout;
  private static TextView txtProgress;

    private Handler handler;
   public static Handler callback;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.vpn_profile_list, container, false);

        TextView newvpntext = (TextView) v.findViewById(R.id.add_new_vpn_hint);
        TextView importvpntext = (TextView) v.findViewById(R.id.import_vpn_hint);
        mContext=getActivity();
      //  newvpntext.setText(Html.fromHtml(getString(R.string.add_new_vpn_hint), new MiniImageGetter(), null));
      //  importvpntext.setText(Html.fromHtml(getString(R.string.vpn_import_hint), new MiniImageGetter(), null));

         btn_connect_disconnect = (Button) v.findViewById(R.id.btn_connect_disconnect);
        ImageButton fab_add = (ImageButton) v.findViewById(R.id.fab_add);
        ImageButton fab_import = (ImageButton) v.findViewById(R.id.fab_import);
        if (fab_add != null)
            fab_add.setOnClickListener(this);

        if (fab_import != null)
            fab_import.setOnClickListener(this);
        btn_connect_disconnect.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                populateVpnList();

            }
        });
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mMessageReceiver,
                new IntentFilter("progress"));
        btn_connect_disconnect.setText("Connect With VPN");
        boolean sortByLRU = Preferences.getDefaultSharedPreferences(getActivity()).getBoolean(PREF_SORT_BY_LRU, false);
        Collection<VpnProfile> allvpn = getPM().getProfiles();
        if(allvpn.size()>0)
        {
              if (VpnStatus.isVPNActive() &&((VpnProfile) allvpn.toArray()[0]) .getUUIDString().equals(VpnStatus.getLastConnectedVPNProfile()))
              {
                  btn_connect_disconnect.setText("Connected");
              }
            Log.e("profile",""+((VpnProfile) allvpn.toArray()[0]).toString());
        }else
        {
            btn_connect_disconnect.setText("Connect With VPN");
        }
        btn_connect_disconnect.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if(allvpn.size()>0)
                {
                    if (VpnStatus.isVPNActive() &&((VpnProfile) allvpn.toArray()[0]) .getUUIDString().equals(VpnStatus.getLastConnectedVPNProfile()))
                    {
                        editVPN((VpnProfile) allvpn.toArray()[0]);
                    }
                    Log.e("profile edit",""+((VpnProfile) allvpn.toArray()[0]).toString());
                }
             //   Intent launchIntent =mContext. getPackageManager().getLaunchIntentForPackage("com.facebook.katana");
             //   Intent launchIntent =mContext. getPackageManager().getLaunchIntentForPackage("com.facebook.orca");
               // startActivity( launchIntent );
                return false;
            }
        });
        pachageInfo();

        txtProgress = (TextView)v. findViewById(R.id.txtProgress);
        progressBar = (ProgressBar)v. findViewById(R.id.progressBar);
        progressLayout =v. findViewById(R.id.progressLayout);
        callback=new Handler(this);
        return v;

    }
    int pStatus=0;
    public  static  void test()
    {
//
        Log.e("test","test");
//        btn_connect_disconnect.setText("Connected");
//        progressBar.setProgress(100);
//        txtProgress.setText(100 + " %");
//        progressBar.setVisibility(View.GONE);
    }
    private  void startProgressbar()
    {
        isConnected=false;
        handler = new Handler();
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (pStatus <= 100) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if(!isConnected) {
                                progressBar.setProgress(pStatus);
                                txtProgress.setText(pStatus + " %");
//                                if(pStatus==100) {
////                                    progressBar.setProgress(pStatus);
////                                    txtProgress.setText(pStatus + " %");
////                                    progressLayout.setVisibility(View.GONE);
////                                    btn_connect_disconnect.setText("Connected");
////                                }
////                                else
////                                {
////
////                                }
                            }
                            else
                            {

                                    progressBar.setProgress(100);
                                    txtProgress.setText(100 + " %");
                                    progressLayout.setVisibility(View.GONE);
                                    btn_connect_disconnect.setText("Connected");



                            }
                        }
                    });
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    pStatus++;
                }

            }
        }).start();
    }
private  void pachageInfo()
{
//    final PackageManager pm = getActivity().getPackageManager();
////get a list of installed apps.
//    List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
//
//    for (ApplicationInfo packageInfo : packages) {
//        Log.e("\n\n\tVPNProfile", "Installed package :" + packageInfo.packageName);
//        Log.e("VPNProfile", "Source dir : " + packageInfo.sourceDir);
//        Log.e("VPNProfile", "icon dir : " + packageInfo.icon);
//        Log.e("VPNProfile","Launch Activity :" + pm.getLaunchIntentForPackage(packageInfo.packageName));
//    }

}

    private void startEmbeddedProfile()
    {
        Uri path=null;
        try {
           // InputStream conf = getActivity().getAssets().open("client.ovpn");
            // path=  Uri.fromFile(new File("//assets/client.ovpn"));
           // path = Uri.parse("android.resource://de.blinkt.openvpn/"+R.raw.client);
            path = Uri.parse("android.resource://com.rsa.openvpn/"+R.raw.client);
//            if(new File("//assets/client.ovpn").exists())
//             {
//                 Log.e("path","exist");
//             }
//             else
//             {
//                 Log.e("path","not exist");
//             }
            Log.e("path",""+path);
            startConfigImport(path,true);
        } catch (Exception  e) {
            Log.e("path",""+e.getMessage());
            e.printStackTrace();
        }
    }
    @Override
    public void updateState(String state, String logmessage, final int localizedResId, ConnectionStatus level) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLastStatusMessage = VpnStatus.getLastCleanLogMessage(getActivity());
             //   mArrayadapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void setConnectedVPN(String uuid) {
    }

    @Override
    public boolean handleMessage(Message msg) {

        Log.e("handleMessage","mess");
        return false;
    }

    private class VPNArrayAdapter extends ArrayAdapter<VpnProfile> {

        public VPNArrayAdapter(Context context, int resource,
                               int textViewResourceId) {
            super(context, resource, textViewResourceId);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View v = super.getView(position, convertView, parent);

            final VpnProfile profile = (VpnProfile) getListAdapter().getItem(position);

            View titleview = v.findViewById(R.id.vpn_list_item_left);
            titleview.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    startOrStopVPN(profile);
                }
            });

            View settingsview = v.findViewById(R.id.quickedit_settings);
            settingsview.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    editVPN(profile);

                }
            });

            TextView subtitle = (TextView) v.findViewById(R.id.vpn_item_subtitle);
            if (profile.getUUIDString().equals(VpnStatus.getLastConnectedVPNProfile())) {
                subtitle.setText(mLastStatusMessage);
                subtitle.setVisibility(View.VISIBLE);
            } else {
                subtitle.setText("");
                subtitle.setVisibility(View.GONE);
            }


            return v;
        }
    }

    private void startOrStopVPN(VpnProfile profile) {
        if (VpnStatus.isVPNActive() && profile.getUUIDString().equals(VpnStatus.getLastConnectedVPNProfile())) {
            Intent disconnectVPN = new Intent(getActivity(), DisconnectVPN.class);
            startActivity(disconnectVPN);
        } else {
            progressLayout.setVisibility(View.VISIBLE);
            Log.e("progressLayout","call");
            startProgressbar();
            startVPN(profile);
        }
    }


    private ArrayAdapter<VpnProfile> mArrayadapter;

    protected VpnProfile mEditProfile = null;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }


    // Shortcut version is increased to refresh all shortcuts
    final static int SHORTCUT_VERSION = 1;

    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    void updateDynamicShortcuts() {
        PersistableBundle versionExtras = new PersistableBundle();
        versionExtras.putInt("version", SHORTCUT_VERSION);

        ShortcutManager shortcutManager = getContext().getSystemService(ShortcutManager.class);
        if (shortcutManager.isRateLimitingActive())
            return;

        List<ShortcutInfo> shortcuts = shortcutManager.getDynamicShortcuts();
        int maxvpn = shortcutManager.getMaxShortcutCountPerActivity() - 1;


        ShortcutInfo disconnectShortcut = new ShortcutInfo.Builder(getContext(), "disconnectVPN")
                .setShortLabel("Disconnect")
                .setLongLabel("Disconnect VPN")
                .setIntent(new Intent(getContext(), DisconnectVPN.class).setAction(DISCONNECT_VPN))
                .setIcon(Icon.createWithResource(getContext(), R.drawable.ic_shortcut_cancel))
                .setExtras(versionExtras)
                .build();

        LinkedList<ShortcutInfo> newShortcuts = new LinkedList<>();
        LinkedList<ShortcutInfo> updateShortcuts = new LinkedList<>();

        LinkedList<String> removeShortcuts = new LinkedList<>();
        LinkedList<String> disableShortcuts = new LinkedList<>();

        boolean addDisconnect = true;


        TreeSet<VpnProfile> sortedProfilesLRU = new TreeSet<VpnProfile>(new VpnProfileLRUComparator());
        ProfileManager profileManager = ProfileManager.getInstance(getContext());
        sortedProfilesLRU.addAll(profileManager.getProfiles());

        LinkedList<VpnProfile> LRUProfiles = new LinkedList<>();
        maxvpn = Math.min(maxvpn, sortedProfilesLRU.size());

        for (int i = 0; i < maxvpn; i++) {
            LRUProfiles.add(sortedProfilesLRU.pollFirst());
        }

        for (ShortcutInfo shortcut : shortcuts) {
            if (shortcut.getId().equals("disconnectVPN")) {
                addDisconnect = false;
                if (shortcut.getExtras() == null
                        || shortcut.getExtras().getInt("version") != SHORTCUT_VERSION)
                    updateShortcuts.add(disconnectShortcut);

            } else {
                VpnProfile p = ProfileManager.get(getContext(), shortcut.getId());
                if (p == null || p.profileDeleted) {
                    if (shortcut.isEnabled()) {
                        disableShortcuts.add(shortcut.getId());
                        removeShortcuts.add(shortcut.getId());
                    }
                    if (!shortcut.isPinned())
                        removeShortcuts.add(shortcut.getId());
                } else {

                    if (LRUProfiles.contains(p))
                        LRUProfiles.remove(p);
                    else
                        removeShortcuts.add(p.getUUIDString());

                    if (!p.getName().equals(shortcut.getShortLabel())
                            || shortcut.getExtras() == null
                            || shortcut.getExtras().getInt("version") != SHORTCUT_VERSION)
                        updateShortcuts.add(createShortcut(p));


                }

            }

        }
        if (addDisconnect)
            newShortcuts.add(disconnectShortcut);
        for (VpnProfile p : LRUProfiles)
            newShortcuts.add(createShortcut(p));

        if (updateShortcuts.size() > 0)
            shortcutManager.updateShortcuts(updateShortcuts);
        if (removeShortcuts.size() > 0)
            shortcutManager.removeDynamicShortcuts(removeShortcuts);
        if (newShortcuts.size() > 0)
            shortcutManager.addDynamicShortcuts(newShortcuts);
        if (disableShortcuts.size() > 0)
            shortcutManager.disableShortcuts(disableShortcuts, "VpnProfile does not exist anymore.");
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    ShortcutInfo createShortcut(VpnProfile profile) {
        Intent shortcutIntent = new Intent(Intent.ACTION_MAIN);
        shortcutIntent.setClass(getActivity(), LaunchVPN.class);
        shortcutIntent.putExtra(LaunchVPN.EXTRA_KEY, profile.getUUID().toString());
        shortcutIntent.setAction(Intent.ACTION_MAIN);
        shortcutIntent.putExtra("EXTRA_HIDELOG", true);

        PersistableBundle versionExtras = new PersistableBundle();
        versionExtras.putInt("version", SHORTCUT_VERSION);

        return new ShortcutInfo.Builder(getContext(), profile.getUUIDString())
                .setShortLabel(profile.getName())
                .setLongLabel(getString(R.string.qs_connect, profile.getName()))
                .setIcon(Icon.createWithResource(getContext(), R.drawable.ic_shortcut_vpn_key))
                .setIntent(shortcutIntent)
                .setExtras(versionExtras)
                .build();
    }

    class MiniImageGetter implements ImageGetter {


        @Override
        public Drawable getDrawable(String source) {
            Drawable d = null;
            if ("ic_menu_add".equals(source))
                d = getActivity().getResources().getDrawable(R.drawable.ic_menu_add_grey);
            else if ("ic_menu_archive".equals(source))
                d = getActivity().getResources().getDrawable(R.drawable.ic_menu_import_grey);


            if (d != null) {
                d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
                return d;
            } else {
                return null;
            }
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        //setListAdapter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            updateDynamicShortcuts();
        }
        VpnStatus.addStateListener(this);

    }
    public  static  boolean isConnected;
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            String type = intent.getStringExtra("type");
            if(type.equals("connected")) {

                btn_connect_disconnect.setText("Connected");
                progressBar.setProgress(100);
                txtProgress.setText(100 + " %");
                progressBar.setVisibility(View.GONE);
                //handler.r
            }
            else
            {
                btn_connect_disconnect.setText("Connect With VPN");
            }
            Log.e("receiver", "Got message: " + type);

        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mMessageReceiver);

    }

    @Override
    public void onPause() {
        super.onPause();
        VpnStatus.removeStateListener(this);

//        mContext.unbindService(mConnection);
    }



    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
       // setListAdapter();
    }

    static class VpnProfileNameComparator implements Comparator<VpnProfile> {

        @Override
        public int compare(VpnProfile lhs, VpnProfile rhs) {
            if (lhs == rhs)
                // Catches also both null
                return 0;

            if (lhs == null)
                return -1;
            if (rhs == null)
                return 1;

            if (lhs.mName == null)
                return -1;
            if (rhs.mName == null)
                return 1;

            return lhs.mName.compareTo(rhs.mName);
        }

    }

    static class VpnProfileLRUComparator implements Comparator<VpnProfile> {

        VpnProfileNameComparator nameComparator = new VpnProfileNameComparator();

        @Override
        public int compare(VpnProfile lhs, VpnProfile rhs) {
            if (lhs == rhs)
                // Catches also both null
                return 0;

            if (lhs == null)
                return -1;
            if (rhs == null)
                return 1;

            // Copied from Long.compare
            if (lhs.mLastUsed > rhs.mLastUsed)
                return -1;
            if (lhs.mLastUsed < rhs.mLastUsed)
                return 1;
            else
                return nameComparator.compare(lhs, rhs);
        }
    }


    private void setListAdapter() {
//        if (mArrayadapter == null) {
//            mArrayadapter = new VPNArrayAdapter(getActivity(), R.layout.vpn_list_item, R.id.vpn_item_title);
//
//        }
       // populateVpnList();
    }

    private void populateVpnList() {
        boolean sortByLRU = Preferences.getDefaultSharedPreferences(getActivity()).getBoolean(PREF_SORT_BY_LRU, false);
        Collection<VpnProfile> allvpn = getPM().getProfiles();
        if(allvpn.size()>0)
        {
            startOrStopVPN((VpnProfile) allvpn.toArray()[0]);
            Log.e("profile",""+((VpnProfile) allvpn.toArray()[0]).toString());
        }else
        {
            progressLayout.setVisibility(View.VISIBLE);
            Log.e("progressLayout","call");
            startProgressbar();
            startEmbeddedProfile();
        }
//        TreeSet<VpnProfile> sortedset;
//        if (sortByLRU)
//            sortedset = new TreeSet<>(new VpnProfileLRUComparator());
//        else
//            sortedset = new TreeSet<>(new VpnProfileNameComparator());
//
//        sortedset.addAll(allvpn);
//        mArrayadapter.clear();
//        mArrayadapter.addAll(sortedset);
//
//        setListAdapter(mArrayadapter);
//        mArrayadapter.notifyDataSetChanged();
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
//        menu.add(0, MENU_ADD_PROFILE, 0, R.string.menu_add_profile)
//                .setIcon(R.drawable.ic_menu_add)
//                .setAlphabeticShortcut('a')
//                .setTitleCondensed(getActivity().getString(R.string.add))
//                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
//
//        menu.add(0, MENU_IMPORT_PROFILE, 0, R.string.menu_import)
//                .setIcon(R.drawable.ic_menu_import)
//                .setAlphabeticShortcut('i')
//                .setTitleCondensed(getActivity().getString(R.string.menu_import_short))
//                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

//        menu.add(0, MENU_CHANGE_SORTING, 0, R.string.change_sorting)
//                .setIcon(R.drawable.ic_sort)
//                .setAlphabeticShortcut('s')
//                .setTitleCondensed(getString(R.string.sort))
//                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);

    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == MENU_ADD_PROFILE) {
            onAddOrDuplicateProfile(null);
            return true;
        } else if (itemId == MENU_IMPORT_PROFILE) {
            return startImportConfigFilePicker();
        } else if (itemId == MENU_CHANGE_SORTING) {
            return changeSorting();
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private boolean changeSorting() {
        SharedPreferences prefs = Preferences.getDefaultSharedPreferences(getActivity());
        boolean oldValue = prefs.getBoolean(PREF_SORT_BY_LRU, false);
        SharedPreferences.Editor prefsedit = prefs.edit();
        if (oldValue) {
            Toast.makeText(getActivity(), R.string.sorted_az, Toast.LENGTH_SHORT).show();
            prefsedit.putBoolean(PREF_SORT_BY_LRU, false);
        } else {
            prefsedit.putBoolean(PREF_SORT_BY_LRU, true);
            Toast.makeText(getActivity(), R.string.sorted_lru, Toast.LENGTH_SHORT).show();
        }
        prefsedit.apply();
        populateVpnList();
        return true;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fab_import:
                startImportConfigFilePicker();
                break;
            case R.id.fab_add:
                onAddOrDuplicateProfile(null);
                break;
        }
    }

    private boolean startImportConfigFilePicker() {
        boolean startOldFileDialog = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && !Utils.alwaysUseOldFileChooser(getActivity() ))
            startOldFileDialog = !startFilePicker();

        if (startOldFileDialog)
            startImportConfig();

        return true;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private boolean startFilePicker() {

        Intent i = Utils.getFilePickerIntent(getActivity(), Utils.FileType.OVPN_CONFIG);
        if (i != null) {
            startActivityForResult(i, FILE_PICKER_RESULT_KITKAT);
            return true;
        } else
            return false;
    }

    private void startImportConfig() {
        Intent intent = new Intent(getActivity(), FileSelect.class);
        intent.putExtra(FileSelect.NO_INLINE_SELECTION, true);
        intent.putExtra(FileSelect.WINDOW_TITLE, R.string.import_configuration_file);
        startActivityForResult(intent, SELECT_PROFILE);
    }


    private void onAddOrDuplicateProfile(final VpnProfile mCopyProfile) {
        Context context = getActivity();
        if (context != null) {
            final EditText entry = new EditText(context);
            entry.setSingleLine();

            AlertDialog.Builder dialog = new AlertDialog.Builder(context);
            if (mCopyProfile == null)
                dialog.setTitle(R.string.menu_add_profile);
            else {
                dialog.setTitle(context.getString(R.string.duplicate_profile_title, mCopyProfile.mName));
                entry.setText(getString(R.string.copy_of_profile, mCopyProfile.mName));
            }

            dialog.setMessage(R.string.add_profile_name_prompt);
            dialog.setView(entry);

            dialog.setNeutralButton(R.string.menu_import_short,
                    (dialog1, which) -> startImportConfigFilePicker());
            dialog.setPositiveButton(android.R.string.ok,
                    (dialog12, which) -> {
                        String name = entry.getText().toString();
                        if (getPM().getProfileByName(name) == null) {
                            VpnProfile profile;
                            if (mCopyProfile != null) {
                                profile = mCopyProfile.copy(name);
                                // Remove restrictions on copy profile
                                profile.mProfileCreator = null;
                                profile.mUserEditable = true;
                            } else
                                profile = new VpnProfile(name);

                            addProfile(profile);
                            editVPN(profile);
                        } else {
                            Toast.makeText(getActivity(), R.string.duplicate_profile_name, Toast.LENGTH_LONG).show();
                        }
                    });
            dialog.setNegativeButton(android.R.string.cancel, null);
            dialog.create().show();
        }

    }

    private void addProfile(VpnProfile profile) {
        getPM().addProfile(profile);
        getPM().saveProfileList(getActivity());
        getPM().saveProfile(getActivity(), profile);
        mArrayadapter.add(profile);
    }

    private ProfileManager getPM() {
        return ProfileManager.getInstance(getActivity());
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_VPN_DELETED) {
            if (mArrayadapter != null && mEditProfile != null)
                mArrayadapter.remove(mEditProfile);
        } else if (resultCode == RESULT_VPN_DUPLICATE && data != null) {
            String profileUUID = data.getStringExtra(VpnProfile.EXTRA_PROFILEUUID);
            VpnProfile profile = ProfileManager.get(getActivity(), profileUUID);
            if (profile != null)
                onAddOrDuplicateProfile(profile);
        }


        if (resultCode != Activity.RESULT_OK)
        {
            return;
        }



        if (requestCode == START_VPN_CONFIG) {
            String configuredVPN = data.getStringExtra(VpnProfile.EXTRA_PROFILEUUID);

            VpnProfile profile = ProfileManager.get(getActivity(), configuredVPN);
            getPM().saveProfile(getActivity(), profile);
            // Name could be modified, reset List adapter
          //  setListAdapter();

        } else if (requestCode == SELECT_PROFILE) {
            String fileData = data.getStringExtra(FileSelect.RESULT_DATA);
            Uri uri = new Uri.Builder().path(fileData).scheme("file").build();

            startConfigImport(uri,false);
        }
        else if (requestCode == IMPORT_PROFILE) {
            String profileUUID = data.getStringExtra(VpnProfile.EXTRA_PROFILEUUID);

            Log.e("profileUUID","--"+profileUUID);
            mArrayadapter.add(ProfileManager.get(getActivity(), profileUUID));
        } else if (requestCode == IMPORT_PROFILE_LOCAL) {
            String profileUUID = data.getStringExtra(VpnProfile.EXTRA_PROFILEUUID);
            Log.e("profileUUID","--"+profileUUID);
            //mArrayadapter.add(ProfileManager.get(getActivity(), profileUUID));

            VpnProfile profile = ProfileManager.get(getActivity(), profileUUID);
            Log.e("profileUUID--","--"+profile.toString()+"\n"+profile.mUsername);
            startOrStopVPN(profile);
        }
        else if (requestCode == FILE_PICKER_RESULT_KITKAT) {
            if (data != null) {
                Uri uri = data.getData();
                startConfigImport(uri,false);
            }
        }

    }

    private void startConfigImport(Uri uri,boolean isLocal) {
      //  Intent startImport = new Intent(getActivity(), ConfigConverter.class);
        if(isLocal)
        {
//            startImport.setAction(ConfigConverter.IMPORT_PROFILE_LOCAL);
//            startImport.setAction(ConfigConverter.IMPORT_PROFILE_LOCAL);
//            startImport.setData(uri);
//            startActivityForResult(startImport, IMPORT_PROFILE_LOCAL);
            doImportUri(uri);
            Log.e("IMPORT_PROFILE_LOCAL",""+uri);
        }
        else
        {
//            startImport.setAction(ConfigConverter.IMPORT_PROFILE);
//            startImport.setAction(ConfigConverter.IMPORT_PROFILE);
//            startImport.setData(uri);
//            startActivityForResult(startImport, IMPORT_PROFILE);
        }

    }
    private transient List<String> mPathsegments;
    private void doImportUri(Uri data) {
        //log(R.string.import_experimental);
      //  log(R.string.importing_config, data.toString());
        String possibleName = null;
        if ((data.getScheme() != null && data.getScheme().equals("file")) ||
                (data.getLastPathSegment() != null &&
                        (data.getLastPathSegment().endsWith(".ovpn") ||
                                data.getLastPathSegment().endsWith(".conf")))
        ) {
            possibleName = data.getLastPathSegment();
            if (possibleName.lastIndexOf('/') != -1)
                possibleName = possibleName.substring(possibleName.lastIndexOf('/') + 1);

        }

        mPathsegments = data.getPathSegments();

        Cursor cursor = mContext.getContentResolver().query(data, null, null, null, null);

        try {

            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);

                if (columnIndex != -1) {
                    String displayName = cursor.getString(columnIndex);
                    if (displayName != null)
                        possibleName = displayName;
                }
                columnIndex = cursor.getColumnIndex("mime_type");
                if (columnIndex != -1) {
                  //  log("Mime type: " + cursor.getString(columnIndex));
                }
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        if (possibleName != null) {
            possibleName = possibleName.replace(".ovpn", "");
            possibleName = possibleName.replace(".conf", "");
        }

        startImportTask(data, possibleName);


    }

    private AsyncTask<Void, Void, Integer> mImportTask;
    private void addViewToLog(View view) {
        //mLogLayout.addView(view, mLogLayout.getChildCount() - 1);
    }

    private File findFileRaw(String filename) {
        if (filename == null || filename.equals(""))
            return null;

        // Try diffent path relative to /mnt/sdcard
        File sdcard = Environment.getExternalStorageDirectory();
        File root = new File("/");

        HashSet<File> dirlist = new HashSet<>();

        for (int i = mPathsegments.size() - 1; i >= 0; i--) {
            String path = "";
            for (int j = 0; j <= i; j++) {
                path += "/" + mPathsegments.get(j);
            }
            // Do a little hackish dance for the Android File Importer
            // /document/primary:ovpn/openvpn-imt.conf


            if (path.indexOf(':') != -1 && path.lastIndexOf('/') > path.indexOf(':')) {
                String possibleDir = path.substring(path.indexOf(':') + 1, path.length());
                // Unquote chars in the  path
                try {
                    possibleDir = URLDecoder.decode(possibleDir, "UTF-8");
                } catch (UnsupportedEncodingException ignored) {}

                possibleDir = possibleDir.substring(0, possibleDir.lastIndexOf('/'));




                dirlist.add(new File(sdcard, possibleDir));

            }
            dirlist.add(new File(path));


        }
        dirlist.add(sdcard);
        dirlist.add(root);


        String[] fileparts = filename.split("/");
        for (File rootdir : dirlist) {
            String suffix = "";
            for (int i = fileparts.length - 1; i >= 0; i--) {
                if (i == fileparts.length - 1)
                    suffix = fileparts[i];
                else
                    suffix = fileparts[i] + "/" + suffix;

                File possibleFile = new File(rootdir, suffix);
                if (possibleFile.canRead())
                    return possibleFile;

            }
        }
        return null;
    }
    private Map<Utils.FileType, FileSelectLayout> fileSelectMap = new HashMap<>();
    private File findFile(String filename, Utils.FileType fileType) {
        File foundfile = findFileRaw(filename);

        if (foundfile == null && filename != null && !filename.equals("")) {
          //  log(R.string.import_could_not_open, filename);
        }
        fileSelectMap.put(fileType, null);

        return foundfile;
    }

    private String embedFile(String filename, Utils.FileType type, boolean onlyFindFileAndNullonNotFound) {
        if (filename == null)
            return null;

        // Already embedded, nothing to do
        if (VpnProfile.isEmbedded(filename))
            return filename;

        File possibleFile = findFile(filename, type);
        if (possibleFile == null)
            if (onlyFindFileAndNullonNotFound)
                return null;
            else
                return filename;
        else if (onlyFindFileAndNullonNotFound)
            return possibleFile.getAbsolutePath();
        else
            return readFileContent(possibleFile, type == Utils.FileType.PKCS12);

    }
    private byte[] readBytesFromFile(File file) throws IOException {
        InputStream input = new FileInputStream(file);

        long len = file.length();
        if (len > VpnProfile.MAX_EMBED_FILE_SIZE)
            throw new IOException("File size of file to import too large.");

        // Create the byte array to hold the data
        byte[] bytes = new byte[(int) len];

        // Read in the bytes
        int offset = 0;
        int bytesRead;
        while (offset < bytes.length
                && (bytesRead = input.read(bytes, offset, bytes.length - offset)) >= 0) {
            offset += bytesRead;
        }

        input.close();
        return bytes;
    }

    String readFileContent(File possibleFile, boolean base64encode) {
        byte[] filedata;
        try {
            filedata = readBytesFromFile(possibleFile);
        } catch (IOException e) {
           // log(e.getLocalizedMessage());
            return null;
        }

        String data;
        if (base64encode) {
            data = Base64.encodeToString(filedata, Base64.DEFAULT);
        } else {
            data = new String(filedata);

        }

        return VpnProfile.DISPLAYNAME_TAG + possibleFile.getName() + VpnProfile.INLINE_TAG + data;

    }
    private String mAliasName = null;
    private String mEmbeddedPwFile;
    void embedFiles(ConfigParser cp) {
        // This where I would like to have a c++ style
        // void embedFile(std::string & option)

        if (mResult.mPKCS12Filename != null) {
            File pkcs12file = findFileRaw(mResult.mPKCS12Filename);
            if (pkcs12file != null) {
                mAliasName = pkcs12file.getName().replace(".p12", "");
            } else {
                mAliasName = "Imported PKCS12";
            }
        }


        mResult.mCaFilename = embedFile(mResult.mCaFilename, Utils.FileType.CA_CERTIFICATE, false);
        mResult.mClientCertFilename = embedFile(mResult.mClientCertFilename, Utils.FileType.CLIENT_CERTIFICATE, false);
        mResult.mClientKeyFilename = embedFile(mResult.mClientKeyFilename, Utils.FileType.KEYFILE, false);
        mResult.mTLSAuthFilename = embedFile(mResult.mTLSAuthFilename, Utils.FileType.TLS_AUTH_FILE, false);
        mResult.mPKCS12Filename = embedFile(mResult.mPKCS12Filename, Utils.FileType.PKCS12, false);
        mResult.mCrlFilename = embedFile(mResult.mCrlFilename, Utils.FileType.CRL_FILE, true);
        if (cp != null) {
            mEmbeddedPwFile = cp.getAuthUserPassFile();
            mEmbeddedPwFile = embedFile(cp.getAuthUserPassFile(), Utils.FileType.USERPW_FILE, false);
        }

        Log.e("saved","data");
        userActionSaveProfile();

    }
    private boolean userActionSaveProfile() {
        if (mResult == null) {
            //log(R.string.import_config_error);
           // Toast.makeText(this, R.string.import_config_error, Toast.LENGTH_LONG).show();
            return true;
        }

        mResult.mName = "rsa";
        ProfileManager vpl = ProfileManager.getInstance(mContext);
        if (vpl.getProfileByName(mResult.mName) != null) {
         //   mProfilename.setError(getString(R.string.duplicate_profile_name));
            return true;
        }



            saveProfile();

        return true;
    }
    private void saveProfile() {
        Intent result = new Intent();
        ProfileManager vpl = ProfileManager.getInstance(mContext);

        if (!TextUtils.isEmpty(mEmbeddedPwFile))
            ConfigParser.useEmbbedUserAuth(mResult, mEmbeddedPwFile);

        vpl.addProfile(mResult);
        vpl.saveProfile(mContext, mResult);
        vpl.saveProfileList(mContext);
        result.putExtra(VpnProfile.EXTRA_PROFILEUUID, mResult.getUUID().toString());

       // String profileUUID = data.getStringExtra(VpnProfile.EXTRA_PROFILEUUID);
       // Log.e("profileUUID","--"+profileUUID);
        //mArrayadapter.add(ProfileManager.get(getActivity(), profileUUID));

        //VpnProfile profile = ProfileManager.get(getActivity(), profileUUID);
       // Log.e("profileUUID--","--"+profile.toString()+"\n"+profile.mUsername);
        startOrStopVPN(mResult);
      //  setResult(Activity.RESULT_OK, result);
      //  finish();
    }
    private Intent installPKCS12() {


        String pkcs12datastr = mResult.mPKCS12Filename;
        if (VpnProfile.isEmbedded(pkcs12datastr)) {
            Intent inkeyIntent = KeyChain.createInstallIntent();

            pkcs12datastr = VpnProfile.getEmbeddedContent(pkcs12datastr);


            byte[] pkcs12data = Base64.decode(pkcs12datastr, Base64.DEFAULT);


            inkeyIntent.putExtra(KeyChain.EXTRA_PKCS12, pkcs12data);

            if (mAliasName.equals(""))
                mAliasName = null;

            if (mAliasName != null) {
                inkeyIntent.putExtra(KeyChain.EXTRA_NAME, mAliasName);
            }
            return inkeyIntent;

        }
        return null;
    }
    private VpnProfile mResult;
    private void doImport(InputStream is) {
        ConfigParser cp = new ConfigParser();
        try {
            InputStreamReader isr = new InputStreamReader(is);

            cp.parseConfig(isr);
            mResult = cp.convertProfile();
            embedFiles(cp);
            return;

        } catch (IOException | ConfigParser.ConfigParseError e) {
            //log(R.string.error_reading_config_file);
           // log(e.getLocalizedMessage());
        }
        mResult = null;

    }
    private void startImportTask(final Uri data, final String possibleName) {
        mImportTask = new AsyncTask<Void, Void, Integer>() {
            private ProgressBar mProgress;

            @Override
            protected void onPreExecute() {
                mProgress = new ProgressBar(mContext);
                addViewToLog(mProgress);
            }

            @Override
            protected Integer doInBackground(Void... params) {
                try {
                    InputStream is = mContext.getContentResolver().openInputStream(data);

                    doImport(is);
                    is.close();
                    if (mResult==null)
                        return -3;
                } catch (IOException| SecurityException se)

                {
                  //  log(R.string.import_content_resolve_error + ":" + se.getLocalizedMessage());
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
//                        checkMarschmallowFileImportError(data);
                    return -2;
                }

                return 0;
            }

            @Override
            protected void onPostExecute(Integer errorCode) {
               // mLogLayout.removeView(mProgress);
               // addMissingFileDialogs();
               // updateFileSelectDialogs();

                if (errorCode == 0) {
                    //displayWarnings();
                    mResult.mName = getUniqueProfileName(possibleName);
                    mResult.mUsername="rsa";
                    mResult.mPassword="rsa@1638";
//                    mProfilename.setVisibility(View.VISIBLE);
//                    mProfilenameLabel.setVisibility(View.VISIBLE);
//                    mProfilename.setText(mResult.getName());

                //    log(R.string.import_done);
                }
            }
        }.execute();
    }
    private String getUniqueProfileName(String possibleName) {

        int i = 0;

        ProfileManager vpl = ProfileManager.getInstance(mContext);

        String newname = possibleName;

        // 	Default to
        if (mResult.mName != null && !ConfigParser.CONVERTED_PROFILE.equals(mResult.mName))
            newname = mResult.mName;

        while (newname == null || vpl.getProfileByName(newname) != null) {
            i++;
            if (i == 1)
                newname = getString(R.string.converted_profile);
            else
                newname = getString(R.string.converted_profile_i, i);
        }

        return newname;
    }


    private void editVPN(VpnProfile profile) {
        mEditProfile = profile;
        Intent vprefintent = new Intent(getActivity(), VPNPreferences.class)
                .putExtra(getActivity().getPackageName() + ".profileUUID", profile.getUUID().toString());

        startActivityForResult(vprefintent, START_VPN_CONFIG);
    }

    private void startVPN(VpnProfile profile) {
        //   Intent launchIntent =mContext. getPackageManager().getLaunchIntentForPackage("com.facebook.katana");
        //   Intent launchIntent =mContext. getPackageManager().getLaunchIntentForPackage("com.facebook.orca");
        profile.mAllowedAppsVpnAreDisallowed=true;
        profile.mAllowAppVpnBypass=false;
//        profile.mAllowedAppsVpn.add("com.android.chrome");

        final PackageManager pm = getActivity().getPackageManager();
//get a list of installed apps.
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo packageInfo : packages)
        {
            if(!packageInfo.packageName.equals("com.facebook.katana")||!packageInfo.packageName.equals("com.facebook.orca"))
            {
               // Log.e("\n\n\tVPNProfile", "Installed package :" + packageInfo.packageName);
                profile.mAllowedAppsVpn.add(packageInfo.packageName);
            }
           // Log.e("VPNProfile", "Source dir : " + packageInfo.sourceDir);
           // Log.e("VPNProfile", "icon dir : " + packageInfo.icon);
          //  Log.e("VPNProfile","Launch Activity :" + pm.getLaunchIntentForPackage(packageInfo.packageName));
        }
        Log.e("\n\n Total VPNProfile", "Installed package :" +  profile.mAllowedAppsVpn.size());

        getPM().saveProfile(getActivity(), profile);

        Intent intent = new Intent(getActivity(), LaunchVPN.class);
        intent.putExtra(LaunchVPN.EXTRA_KEY, profile.getUUID().toString());
        intent.setAction(Intent.ACTION_MAIN);
        startActivity(intent);

    }

}
